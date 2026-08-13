package cx.yeet.stfst

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/**
 * The keyboard itself: two thumb combs and the touch handling that turns taps
 * and walks into strokes.
 *
 * Drawing reads the chorder and decides nothing. Every shape is asked of
 * [Comb], the region a cell owns included, so the picture is the decision
 * function rather than a copy of it that can drift.
 */
@SuppressLint("ViewConstructor")
class CombView(
    context: Context,
    private var config: Config,
    private val order: String,
    private val onStroke: (Long) -> Unit,
    /** True in the settings preview, which has no app to lay out around. */
    private val embedded: Boolean = false,
) : View(context) {

    private var comb = Comb(order, config)
    private var chorder = Chorder(comb, config) { onStroke(it) }
    private val haptics = Haptics(context, config)

    /** The last cell each thumb was told about, so a tick reports a change only. */
    private val told = mutableMapOf<Bank, Spot?>(Bank.LEFT to null, Bank.RIGHT to null)

    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dashes = DashPathEffect(floatArrayOf(9f, 9f), 0f)
    private val stalls = mutableMapOf<Bank, Runnable>()

    /** Where and when a touch went down, to tell a quick tap from a walk. */
    private class Tap(val x: Float, val y: Float, val time: Long)

    private val downs = mutableMapOf<Int, Tap>()
    private val lastTaps = mutableMapOf<Bank, Tap>()

    /** The one timer that sends a held tap on when its window closes. */
    private var flusher: Runnable? = null

    /** What the driver reported about one touch sample. */
    private class Touch(
        val x: Float,
        val y: Float,
        val major: Float,
        val minor: Float,
        val orientation: Float,
        val pressure: Float,
        val size: Float,
    )

    private val clouds = mapOf(
        Bank.LEFT to ArrayDeque<Touch>(), Bank.RIGHT to ArrayDeque<Touch>()
    )

    private val landscape: Boolean
        get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    /** Whether the two combs stand on the edges with the bar in the gap. */
    val sides: Boolean get() = !embedded && landscape && config.landscapeSides

    private val tilt: Float get() = config.tilt

    fun apply(fresh: Config) {
        config = fresh
        comb = Comb(order, fresh)
        chorder = Chorder(comb, fresh) { onStroke(it) }
        haptics.apply(fresh)
        layoutFields()
        requestLayout()
        invalidate()
    }

    /**
     * How deep the keys are. Above them the view is the preview band, which is
     * drawn over the app and takes no touches.
     */
    val keysDeep: Int
        get() {
            val screen = resources.displayMetrics.heightPixels
            val share =
                if (landscape && !embedded) config.landscapeHeight else config.keyboardHeight
            return (screen * share.coerceIn(0.15f, 0.95f)).toInt()
        }

    private val previewDeep: Int
        get() =
            if (embedded) 0
            else (resources.displayMetrics.heightPixels * config.previewHeight
                .coerceIn(0f, 0.6f)).toInt()

    /** Where the keys start, so the band above them can be drawn over the app. */
    val keysTop: Int get() = height - keysDeep

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val width = MeasureSpec.getSize(widthSpec)
        var height = keysDeep + previewDeep
        // The window is what it is. Measuring past it would put the top of the
        // comb off the glass, which is what asking for the whole screen did.
        if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) {
            height = min(height, MeasureSpec.getSize(heightSpec))
        }
        setMeasuredDimension(width, height)
    }

    /**
     * The horizontal run each thumb's field covers. Portrait splits the whole
     * width; in landscape each field hugs its own side and the ground between
     * them belongs to neither.
     */
    private fun spans(
        width: Float,
    ): Pair<ClosedFloatingPointRange<Float>, ClosedFloatingPointRange<Float>> {
        if (landscape && config.landscapeSides) {
            val wide = width * config.splitLandscape.coerceIn(0.15f, 0.5f)
            return (0f..wide) to ((width - wide)..width)
        }
        val edge = width * config.fieldSplit
        return (0f..edge) to (edge..width)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = layoutFields()

    private fun layoutFields() {
        val (left, right) = spans(width.toFloat())
        val floor = height.toFloat()
        val top = keysTop + if (config.touchReadout) keysDeep * 0.04f else 0f
        chorder.landscape = landscape
        chorder.place(Bank.LEFT, Field(left.start, top, left.endInclusive, floor))
        chorder.place(Bank.RIGHT, Field(right.start, top, right.endInclusive, floor))
        cut()
    }

    /** The same bank, drawn large in the band over the app. */
    private fun band(bank: Bank): Field {
        val (left, right) = spans(width.toFloat())
        val span = if (bank == Bank.LEFT) left else right
        return Field(span.start, 0f, span.endInclusive, keysTop.toFloat())
    }

    /** Whether a touch is on the keys at all, rather than the band over the app. */
    private fun onKeys(y: Float): Boolean = y >= keysTop

    /** The field under this x, or null on the ground between split fields. */
    private fun bankAt(x: Float): Bank? {
        val (left, right) = spans(width.toFloat())
        return when {
            x <= left.endInclusive -> Bank.LEFT
            x >= right.start -> Bank.RIGHT
            else -> null
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val at = event.actionIndex
                val pointer = event.getPointerId(at)
                val x = event.getX(at)
                val y = event.getY(at)
                if (!onKeys(y)) return false
                val bank = bankAt(x) ?: return false
                clouds[bank]?.clear()
                record(event, at, bank)
                // A double tap arms the side's key and then goes down as a
                // normal touch, so tap tap on -T releases as -*T. Its own lift
                // is not recorded as a tap, which writes it at once.
                if (!doubleTap(bank, x, y, event.eventTime)) {
                    chorder.flush()
                    downs[pointer] = Tap(x, y, event.eventTime)
                }
                if (chorder.down(bank, pointer, x, y)) haptics.take(bank)
                tell(bank)
                armStall(bank)
            }

            MotionEvent.ACTION_MOVE -> {
                for (at in 0 until event.pointerCount) {
                    val pointer = event.getPointerId(at)
                    val thumb = chorder.forPointer(pointer) ?: continue
                    record(event, at, thumb.bank)
                    downs[pointer]?.let {
                        val far = hypot(event.getX(at) - it.x, event.getY(at) - it.y)
                        if (far > height * 0.02f) downs.remove(pointer)
                    }
                    // One hand can write a whole stroke: a touch that arrives
                    // in the other field is handed over, and the field it left
                    // keeps what it took.
                    if (config.oneThumb) {
                        bankAt(event.getX(at))?.takeIf { it != thumb.bank }?.let {
                            if (chorder.cross(pointer, it, event.getX(at), event.getY(at))) {
                                haptics.take(it)
                            }
                        }
                    }
                    var took = false
                    // Every sample, not one per frame: take-on-entry reads the
                    // path itself, and a fast sweep crosses a cell between frames.
                    for (h in 0 until event.historySize) {
                        took = chorder.move(
                            pointer, event.getHistoricalX(at, h), event.getHistoricalY(at, h)
                        ) || took
                    }
                    took = chorder.move(pointer, event.getX(at), event.getY(at)) || took
                    if (took) haptics.take(thumb.bank)
                    tell(thumb.bank)
                    armStall(thumb.bank)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pointer = event.getPointerId(event.actionIndex)
                val down = downs.remove(pointer)
                val thumb = chorder.forPointer(pointer)
                val tap = config.doubleTapMs > 0 && down != null &&
                    event.eventTime - down.time <= config.doubleTapMs
                val stroke = chorder.up(pointer, tap)
                if (stroke != 0L) haptics.stroke(thumb?.bank ?: Bank.LEFT)
                if (tap && thumb != null) {
                    lastTaps[thumb.bank] = Tap(down!!.x, down.y, event.eventTime)
                    if (stroke != 0L) armFlush()
                }
                thumb?.let { told[it.bank] = null }
            }

            MotionEvent.ACTION_CANCEL -> {
                downs.clear()
                lastTaps.clear()
                chorder.reset()
            }
        }
        invalidate()
        return true
    }

    /**
     * A move event carries the samples taken between frames, so reading the
     * history gives the whole touched path and not one point per frame.
     */
    private fun record(event: MotionEvent, at: Int, bank: Bank) {
        if (!config.cloud && !config.touchReadout) return
        val cloud = clouds[bank] ?: return
        for (h in 0 until event.historySize) {
            cloud.addLast(
                Touch(
                    event.getHistoricalX(at, h), event.getHistoricalY(at, h),
                    event.getHistoricalTouchMajor(at, h), event.getHistoricalTouchMinor(at, h),
                    event.getHistoricalOrientation(at, h), event.getHistoricalPressure(at, h),
                    event.getHistoricalSize(at, h),
                )
            )
        }
        cloud.addLast(
            Touch(
                event.getX(at), event.getY(at),
                event.getTouchMajor(at), event.getTouchMinor(at),
                event.getOrientation(at), event.getPressure(at), event.getSize(at),
            )
        )
        while (cloud.size > config.cloudSamples.coerceAtLeast(1)) cloud.removeFirst()
    }

    /**
     * A second quick tap close to the first arms the side's double-tap key and
     * unwrites the first tap's held stroke; the second tap then strokes as
     * itself, so tap tap on `-T` releases as `-*T`.
     */
    private fun doubleTap(bank: Bank, x: Float, y: Float, now: Long): Boolean {
        if (config.doubleTapMs <= 0) return false
        val tap = lastTaps.remove(bank) ?: return false
        if (now - tap.time > config.doubleTapMs) return false
        if (hypot(x - tap.x, y - tap.y) > height * 0.05f) return false
        val key = order.indexOf(config.doubleTap(bank))
        if (key < 0) return false
        chorder.cancelHeld()
        chorder.stick(key)
        haptics.take(bank)
        return true
    }

    private fun armFlush() {
        flusher?.let { removeCallbacks(it) }
        val task = Runnable {
            chorder.flush()
            invalidate()
        }
        flusher = task
        postDelayed(task, config.doubleTapMs)
    }

    /** A focus that a portal has thrown away comes back when the thumb holds still. */
    private fun armStall(bank: Bank) {
        stalls.remove(bank)?.let { removeCallbacks(it) }
        val task = Runnable {
            chorder.stall(bank)
            told[bank] = null
            invalidate()
        }
        stalls[bank] = task
        postDelayed(task, config.stallMs)
    }

    /**
     * Report the cell under the focus, once per change.
     *
     * Where the focus is becomes something to feel rather than something to
     * read, which is what using this without looking needs.
     */
    private fun tell(bank: Bank) {
        val spot = chorder.thumb(bank).spot
        if (told[bank] == spot) return
        told[bank] = spot
        if (spot != null) haptics.tick(bank)
    }

    private fun fade(color: Int, dim: Float): Int =
        if (dim >= 1f) color else Color.argb(
            (Color.alpha(color) * dim).toInt(),
            Color.red(color), Color.green(color), Color.blue(color)
        )

    private class Palette(
        val face: Int,
        val edge: Int,
        val text: Int,
        val hot: Int,
        val warm: Int,
        val hole: Int,
    )

    override fun onDraw(canvas: Canvas) {
        val dark = (resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val paint = Palette(
            face = if (dark) Color.rgb(38, 40, 46) else Color.WHITE,
            edge = if (dark) Color.rgb(70, 74, 84) else Color.rgb(206, 210, 218),
            text = if (dark) Color.rgb(232, 234, 238) else Color.rgb(24, 26, 30),
            hot = Color.rgb(64, 132, 240),
            warm = if (dark) Color.rgb(30, 58, 104) else Color.rgb(206, 226, 255),
            hole = if (dark) Color.rgb(216, 168, 77) else Color.rgb(169, 111, 22),
        )
        val back = if (dark) Color.rgb(18, 18, 20) else Color.rgb(246, 246, 248)
        // Only the fields get a ground, and only where the keys are. Above them
        // and between them the app shows through, which is what the band is for.
        ink.style = Paint.Style.FILL
        ink.color = back
        for (bank in Bank.values()) {
            val field = chorder.thumb(bank).field
            canvas.drawRect(field.left, keysTop.toFloat(), field.right, height.toFloat(), ink)
        }
        ink.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, config.labelSp, resources.displayMetrics
        )
        ink.textAlign = Paint.Align.CENTER
        for (bank in Bank.values()) {
            val thumb = chorder.thumb(bank)
            val sketch = sketches[bank] ?: continue
            if (config.cloud) drawCloud(canvas, bank, paint)
            drawComb(canvas, thumb, paint, sketch)
            drawFocusCell(canvas, thumb, paint, sketch)
            drawMarks(canvas, thumb, paint)
            if (config.showPortals) drawPortals(canvas, thumb, paint)
            drawTaken(canvas, thumb, paint, thumb.field)
        }
        if (keysTop > 0 && chorder.thumb(Bank.LEFT).live || chorder.thumb(Bank.RIGHT).live) {
            for (bank in Bank.values()) {
                val band = bands[bank] ?: continue
                ink.style = Paint.Style.FILL
                ink.color = fade(back, 0.88f)
                canvas.drawRect(band(bank).let {
                    android.graphics.RectF(it.left, it.top, it.right, it.bottom)
                }, ink)
                drawComb(canvas, chorder.thumb(bank), paint, band)
                drawFocusCell(canvas, chorder.thumb(bank), paint, band)
                drawTaken(canvas, chorder.thumb(bank), paint, band(bank))
            }
        }
        if (config.touchReadout) drawReadout(canvas, paint)
    }

    /**
     * The shapes of one bank, which depend on the layout and the field alone.
     *
     * Only the colours change as a stroke is built, so the paths are cut once
     * per layout rather than once per frame. [Comb] is still the only source of
     * them: what is cached is the answer, not a second way of working it out.
     */
    private class Sketch(
        val ground: List<Pair<Spot, Path>>,
        val keys: List<Triple<Button, Path, Point>>,
        val chain: Path?,
        val radius: Float,
        val at: Placement,
    )

    private var sketches: Map<Bank, Sketch> = emptyMap()
    private var bands: Map<Bank, Sketch> = emptyMap()

    private fun cut() {
        sketches = Bank.values().associateWith { carve(it, chorder.thumb(it).field) }
        bands = if (keysTop <= 0) emptyMap()
        else Bank.values().associateWith { carve(it, band(it)) }
    }

    private fun carve(bank: Bank, field: Field): Sketch {
        val at = comb.place(bank, field, tilt)
        val chain = Path()
        var started = false
        for (key in comb.chain.getValue(bank)) {
            val button = comb.buttons.getValue(bank).firstOrNull { it.key == key } ?: continue
            val c = comb.center(bank, field, tilt, button)
            if (started) chain.lineTo(c.x, c.y) else chain.moveTo(c.x, c.y)
            started = true
        }
        return Sketch(
            ground = comb.spots(bank).filter { comb.keyOf(it) < 0 }.mapNotNull { spot ->
                polygon(comb.region(bank, field, tilt, spot))?.let { spot to it }
            },
            keys = comb.buttons.getValue(bank).mapNotNull { button ->
                melded(bank, field, button)?.let {
                    Triple(button, it, comb.center(bank, field, tilt, button))
                }
            },
            chain = chain.takeIf { started },
            radius = at.radius,
            at = at,
        )
    }

    /** Every cell of one bank: the ground it owns, its outline, and its label. */
    private fun drawComb(canvas: Canvas, thumb: Thumb, paint: Palette, sketch: Sketch) {
        val at = sketch.at

        ink.pathEffect = dashes
        ink.strokeWidth = sketch.radius * 0.03f
        ink.style = Paint.Style.STROKE
        for ((spot, path) in sketch.ground) {
            ink.color = if (spot.v == Comb.HOLE) paint.hole else fade(paint.edge, 0.7f)
            canvas.drawPath(path, ink)
            if (spot.v != Comb.HOLE) continue
            val p = comb.point(spot, at)
            canvas.drawCircle(p.x, p.y, sketch.radius * 0.3f, ink)
        }
        ink.pathEffect = null

        if (config.showChain) sketch.chain?.let {
            ink.style = Paint.Style.STROKE
            ink.color = fade(paint.text, 0.22f)
            ink.pathEffect = dashes
            canvas.drawPath(it, ink)
            ink.pathEffect = null
        }

        ink.strokeWidth = sketch.radius * 0.055f
        val held = (thumb.keys + chorder.sticky).toSet()
        for ((button, path, middle) in sketch.keys) {
            // Three things a key can be, and they are three different answers:
            // in the chord, still available, or spent by steno order.
            val state = when {
                button.key in held -> 2
                comb.live(button.key, thumb.keys) -> 1
                else -> 0
            }
            if (state == 0 && !config.showDeadCells) continue
            ink.style = Paint.Style.FILL
            ink.color = when (state) {
                2 -> paint.warm
                1 -> paint.face
                else -> fade(paint.face, 0.35f)
            }
            canvas.drawPath(path, ink)
            ink.style = Paint.Style.STROKE
            ink.strokeWidth = sketch.radius * if (state == 2) 0.085f else 0.055f
            ink.color = when (state) {
                2 -> paint.hot
                1 -> paint.edge
                else -> fade(paint.edge, 0.4f)
            }
            canvas.drawPath(path, ink)
            if (!config.showLabels) continue
            ink.style = Paint.Style.FILL
            ink.color = when (state) {
                2 -> paint.hot
                1 -> paint.text
                else -> fade(paint.text, 0.3f)
            }
            canvas.drawText(button.v.toString(), middle.x, middle.y + ink.textSize * 0.35f, ink)
        }
        ink.strokeWidth = sketch.radius * 0.055f
    }

    /**
     * Where each portal out of the cell under the focus goes.
     *
     * A stride is a number until you see it land. Each portal side gets a line
     * from the side it leaves by to the cell it arrives at, so the four numbers
     * read as four places while the thumb is on them. A ring on the landing
     * means arriving takes that key; a dashed one means it comes to rest beside
     * it, with the cluster then one hop away.
     *
     * The arrow in the landing points the way out, not the way in: four rings
     * say where the portals go, and only the arrow says which is which.
     *
     * A focus resting on a landing gets the one jump it can still ride, drawn
     * the same way. So the picture is always the sides of the cell the focus is
     * in, whether that cell is a key or a place a jump left it.
     */
    private fun drawPortals(canvas: Canvas, thumb: Thumb, paint: Palette) {
        if (!thumb.live) return
        val spot = thumb.spot ?: return
        val at = comb.place(thumb.bank, thumb.field, tilt)
        val r = sketches[thumb.bank]?.radius ?: return
        val riding = thumb.riding
        val ahead = riding?.let { comb.onward(thumb.bank, it, thumb.keys) }
        val jumps =
            if (riding != null && ahead != null) listOf(riding.side to ahead)
            else Side.values().mapNotNull { side ->
                comb.portal(thumb.bank, spot, side, thumb.keys)?.let { side to it }
            }
        for ((side, land) in jumps) {
            val from = comb.edge(spot, side, at)
            val to = comb.point(land.spot, at)
            ink.style = Paint.Style.STROKE
            ink.strokeWidth = r * 0.05f
            ink.color = fade(paint.hot, 0.4f)
            ink.pathEffect = dashes
            canvas.drawLine(from.x, from.y, to.x, to.y, ink)
            ink.pathEffect = if (land.onKey) null else dashes
            ink.color = fade(paint.hot, if (land.onKey) 0.85f else 0.5f)
            canvas.drawCircle(to.x, to.y, r * 0.42f, ink)
            ink.pathEffect = null
            ink.style = Paint.Style.FILL
            ink.color = fade(paint.hot, if (land.onKey) 0.85f else 0.6f)
            // A key cell already carries its letter, so the arrow rides the ring
            // rather than sitting over it.
            val sit = if (land.onKey) r * 0.42f else 0f
            val back = unit(to, from)
            // The side already knows which way it points: it is the step from the
            // cell's middle out to the edge the portal leaves by.
            val way = unit(comb.point(spot, at), from)
            val more = comb.onward(thumb.bank, Ride(land, side), thumb.keys) != null
            arrow(canvas, to.x + back.x * sit, to.y + back.y * sit, way.x, way.y, r * 0.26f, more)
        }
    }

    /** The unit step from `a` toward `b`. */
    private fun unit(a: Point, b: Point): Point {
        val run = hypot(b.x - a.x, b.y - a.y).coerceAtLeast(1f)
        return Point((b.x - a.x) / run, (b.y - a.y) / run)
    }

    /**
     * A solid arrowhead at a point, pointing along a unit vector.
     *
     * A plain triangle reads as a triangle at any angle. The notch in the tail
     * is what makes it read as pointing.
     *
     * Two heads for a landing the same slide can carry past, which is the one
     * thing the picture cannot say by where it puts a ring.
     */
    private fun arrow(
        canvas: Canvas, x: Float, y: Float, dx: Float, dy: Float, size: Float, twice: Boolean,
    ) {
        // Along the way it points, and across it.
        fun at(lead: Float, on: Float, off: Float) = Point(
            x + dx * size * (on + lead) - dy * size * off,
            y + dy * size * (on + lead) + dx * size * off,
        )
        for (lead in if (twice) listOf(0.9f, -0.9f) else listOf(0f)) {
            val path = Path()
            val tip = at(lead, 1.5f, 0f)
            path.moveTo(tip.x, tip.y)
            for (p in listOf(at(lead, -0.9f, 0.95f), at(lead, -0.4f, 0f), at(lead, -0.9f, -0.95f))) {
                path.lineTo(p.x, p.y)
            }
            path.close()
            canvas.drawPath(path, ink)
        }
    }

    /**
     * One button, melded: the union of the ground its cells own.
     *
     * Each cell is clipped against every rival but its own button's, so the
     * pieces meet exactly and the union has no seam to stroke.
     */
    private fun melded(bank: Bank, field: Field, button: Button): Path? {
        val whole = Path()
        for (piece in comb.regions(bank, field, tilt, button)) {
            val path = polygon(piece) ?: continue
            whole.op(path, Path.Op.UNION)
        }
        return whole.takeIf { !it.isEmpty }
    }

    private fun polygon(points: List<Point>): Path? {
        if (points.size < 3) return null
        val path = Path()
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
        path.close()
        return path
    }

    /**
     * The cell the focus is in, filled, and the thumb that drives it.
     *
     * A portal or a hole parts the two, and after one the thumb is drawn as
     * well, faint, tied to the focus that left it. What is filled is what a
     * further step would take, which is the answer before it is given.
     */
    private fun drawFocusCell(canvas: Canvas, thumb: Thumb, paint: Palette, sketch: Sketch) {
        if (!thumb.live) return
        val spot = thumb.spot ?: return
        val button = comb.buttons.getValue(thumb.bank)
            .firstOrNull { b -> b.spots.any { it.col == spot.col && it.row == spot.row } }
        val cut = sketch.keys.firstOrNull { it.first == button }
        val path = cut?.second ?: sketch.ground.firstOrNull { it.first == spot }?.second ?: return
        // Warm only where a step would take a key; over ground and over a cell
        // the order has passed it says where, not what.
        ink.style = Paint.Style.FILL
        ink.color = if (comb.resolve(spot, thumb.keys) != null) paint.warm
        else fade(paint.warm, 0.45f)
        canvas.drawPath(path, ink)
        if (cut == null || !config.showLabels) return
        ink.color = paint.text
        canvas.drawText(cut.first.v.toString(), cut.third.x, cut.third.y + ink.textSize * 0.35f, ink)
    }

    /** Where the thumb is, and where the focus a jump left it went. */
    private fun drawMarks(canvas: Canvas, thumb: Thumb, paint: Palette) {
        if (!thumb.live) return
        val mark = config.markRadius * min(thumb.field.width, thumb.field.height)
        val parted = hypot(thumb.offX, thumb.offY) > mark
        ink.style = Paint.Style.FILL
        ink.color = paint.hot
        canvas.drawCircle(thumb.focusX, thumb.focusY, mark, ink)
        if (!parted) return
        ink.color = fade(paint.hot, 0.35f)
        canvas.drawCircle(thumb.atX, thumb.atY, mark * 0.7f, ink)
        ink.style = Paint.Style.STROKE
        ink.strokeWidth = mark * 0.25f
        canvas.drawLine(thumb.atX, thumb.atY, thumb.focusX, thumb.focusY, ink)
    }

    /** The keys this thumb holds, armed ones included. */
    private fun drawTaken(canvas: Canvas, thumb: Thumb, paint: Palette, where: Field) {
        val armed = chorder.sticky.filter { comb.bankOf(it) == thumb.bank }
        val keys = (thumb.keys + armed).distinct()
        if (keys.isEmpty()) return
        ink.style = Paint.Style.FILL
        ink.color = paint.hot
        // In steno order, not take order, so what is shown is the outline.
        canvas.drawText(
            keys.sorted().joinToString("") { order[it].toString() },
            where.centerX, where.top + ink.textSize, ink
        )
    }

    private fun drawCloud(canvas: Canvas, bank: Bank, paint: Palette) {
        val cloud = clouds[bank] ?: return
        val unit = min(chorder.thumb(bank).field.width, chorder.thumb(bank).field.height)
        ink.style = Paint.Style.FILL
        ink.color = fade(paint.hot, config.cloudAlpha)
        for (touch in cloud) {
            // Panels report one, the other, or neither. Fall through to the dot.
            val major = when {
                touch.major > 1f -> touch.major
                touch.size > 0f -> touch.size * unit
                else -> config.markRadius * unit
            }
            val a = major * 0.5f
            val b = (if (touch.minor > 1f) touch.minor else major) * 0.5f
            canvas.save()
            canvas.rotate(Math.toDegrees(touch.orientation.toDouble()).toFloat(), touch.x, touch.y)
            canvas.drawOval(touch.x - b, touch.y - a, touch.x + b, touch.y + a, ink)
            canvas.restore()
        }
    }

    /**
     * What the driver says about the live touch.
     *
     * Many panels report a constant pressure and a real contact size, so
     * whether a firm press can be told from a light one is a question these
     * numbers answer.
     */
    private fun drawReadout(canvas: Canvas, paint: Palette) {
        ink.style = Paint.Style.FILL
        ink.color = fade(paint.text, 0.65f)
        for (bank in Bank.values()) {
            val touch = clouds[bank]?.lastOrNull() ?: continue
            val field = chorder.thumb(bank).field
            canvas.drawText(
                "p %.2f  sz %.2f  maj %.0f  min %.0f".format(
                    touch.pressure, touch.size, touch.major, touch.minor
                ),
                field.centerX, ink.textSize, ink
            )
        }
    }
}
