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
import kotlin.math.max
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
    /** The chord in hand changed. Whoever can say what it writes answers with [writes]. */
    private val onChord: (Long) -> Unit = {},
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

    /** How wide each edge field is, which whatever sits in the gap must clear. */
    fun sideWidth(width: Float): Float = width * config.splitLandscape.coerceIn(0.15f, 0.5f)

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
    var keysDeep: Int = 0
        private set

    /** Where the keys start, so the band above them can be drawn over the app. */
    val keysTop: Int get() = height - keysDeep

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val width = MeasureSpec.getSize(widthSpec)
        val screen = resources.displayMetrics.heightPixels
        val share = if (landscape && !embedded) config.landscapeHeight else config.keyboardHeight
        var keys = (screen * share.coerceIn(0.15f, 0.95f)).toInt()
        var band = (screen * config.previewHeight.coerceIn(0f, 0.6f)).toInt()
        // The window is what it is. Measuring past it would put the top of the
        // comb off the glass, so the keys and the band give up room together.
        if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) {
            val room = MeasureSpec.getSize(heightSpec)
            if (keys + band > room) {
                keys = (keys * (room.toFloat() / (keys + band))).toInt()
                band = room - keys
            }
        }
        keysDeep = keys
        setMeasuredDimension(width, keys + band)
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
            val wide = sideWidth(width)
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
        ask()
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
        val edge: Int,
        val text: Int,
        val hot: Int,
        val hole: Int,
        val back: Int,
        /** The ground each kind of key stands on, plain and in the chord. */
        val tints: Map<Kind, Tint>,
    )

    /**
     * What a key is to the eye.
     *
     * The vowels are what a stroke turns on, and the modifiers are the keys
     * that spell nothing by themselves. Both are read off the glyph, so a
     * layout that moves them does not have to say so twice.
     */
    private enum class Kind { PLAIN, VOWEL, MOD }

    private class Tint(val face: Int, val warm: Int)

    private fun kindOf(key: Int): Kind = when {
        key < 0 -> Kind.PLAIN
        order[key] in "AOEU" -> Kind.VOWEL
        !order[key].isLetter() -> Kind.MOD
        else -> Kind.PLAIN
    }

    private fun face(paint: Palette, key: Int, held: Boolean): Int =
        paint.tints.getValue(kindOf(key)).let { if (held) it.warm else it.face }

    /**
     * How far the band over the app has faded in: 0 gone, 1 there.
     *
     * A band that appears and goes at full strength reads as a flash over
     * whatever the app was showing.
     */
    private var glow = 0f
    private var glowAt = 0L

    private fun glow(): Float {
        val now = android.view.animation.AnimationUtils.currentAnimationTimeMillis()
        val step = if (glowAt == 0L) 0f else (now - glowAt) / FADE_MS
        glowAt = now
        val want = if (chorder.left.live || chorder.right.live) 1f else 0f
        glow = if (want > glow) min(want, glow + step) else max(want, glow - step)
        if (glow != want) postInvalidateOnAnimation()
        return glow * glow * (3f - 2f * glow)
    }

    override fun onDraw(canvas: Canvas) {
        val dark = (resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val paint = Palette(
            edge = if (dark) Color.rgb(70, 74, 84) else Color.rgb(206, 210, 218),
            text = if (dark) Color.rgb(232, 234, 238) else Color.rgb(24, 26, 30),
            hot = Color.rgb(64, 132, 240),
            hole = if (dark) Color.rgb(216, 168, 77) else Color.rgb(169, 111, 22),
            back = if (dark) Color.rgb(18, 18, 20) else Color.rgb(246, 246, 248),
            tints = mapOf(
                Kind.PLAIN to Tint(
                    if (dark) Color.rgb(38, 40, 46) else Color.WHITE,
                    if (dark) Color.rgb(30, 58, 104) else Color.rgb(206, 226, 255),
                ),
                Kind.VOWEL to Tint(
                    if (dark) Color.rgb(30, 48, 40) else Color.rgb(235, 248, 240),
                    if (dark) Color.rgb(28, 84, 58) else Color.rgb(196, 236, 212),
                ),
                Kind.MOD to Tint(
                    if (dark) Color.rgb(46, 40, 56) else Color.rgb(246, 241, 250),
                    if (dark) Color.rgb(72, 56, 108) else Color.rgb(226, 214, 246),
                ),
            ),
        )
        val back = paint.back
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
            val sketch = sketches[bank] ?: continue
            if (config.cloud) drawCloud(canvas, bank, paint)
            drawBank(canvas, bank, paint, sketch, chorder.thumb(bank).field)
        }
        drawWriting(canvas, paint, keysTop.toFloat())
        val show = glow()
        if (keysTop > 0 && show > 0f) {
            canvas.saveLayerAlpha(0f, 0f, width.toFloat(), keysTop.toFloat(), (show * 255).toInt())
            for (bank in Bank.values()) {
                drawBank(canvas, bank, paint, bands[bank] ?: continue, band(bank))
            }
            drawWriting(canvas, paint, 0f)
            canvas.restore()
        }
        if (config.touchReadout) drawReadout(canvas, paint)
    }

    /** One bank's whole picture, in the keys and in the band alike. */
    private fun drawBank(canvas: Canvas, bank: Bank, paint: Palette, sketch: Sketch, where: Field) {
        val thumb = chorder.thumb(bank)
        drawComb(canvas, thumb, paint, sketch)
        drawFocusCell(canvas, thumb, paint, sketch)
        if (config.showPortals) drawPortals(canvas, thumb, paint, sketch)
        drawMarks(canvas, thumb, paint, sketch)
        drawTaken(canvas, thumb, paint, where)
    }

    /** One button as drawn: its melded outline and where its label sits. */
    private data class Cut(val button: Button, val path: Path, val middle: Point)

    /**
     * The shapes of one bank, which depend on the layout and the field alone.
     *
     * Only the colours change as a stroke is built, so the paths are cut once
     * per layout rather than once per frame. [Comb] is still the only source of
     * them: what is cached is the answer, not a second way of working it out.
     */
    private class Sketch(
        val ground: List<Pair<Spot, Path>>,
        val keys: List<Cut>,
        val chain: Path?,
        val radius: Float,
        val at: Placement,
        /** Whether this copy is drawn over the app, where the ground is not ours. */
        val over: Boolean,
    )

    private var sketches: Map<Bank, Sketch> = emptyMap()
    private var bands: Map<Bank, Sketch> = emptyMap()

    private fun cut() {
        sketches = Bank.values().associateWith { carve(it, chorder.thumb(it).field, false) }
        bands = if (keysTop <= 0) emptyMap()
        else Bank.values().associateWith { carve(it, band(it), true) }
    }

    private fun carve(bank: Bank, field: Field, over: Boolean): Sketch {
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
                    Cut(button, it, comb.center(bank, field, tilt, button))
                }
            },
            chain = chain.takeIf { started },
            radius = at.radius,
            at = at,
            over = over,
        )
    }

    private fun keyCut(sketch: Sketch, spot: Spot): Cut? =
        sketch.keys.firstOrNull { cut ->
            cut.button.spots.any { it.col == spot.col && it.row == spot.row }
        }

    private fun cellPath(sketch: Sketch, spot: Spot): Path? =
        keyCut(sketch, spot)?.path ?: sketch.ground.firstOrNull { it.first == spot }?.second

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
            // A spent key gives up its hue. Kind is a question about live keys,
            // so held and spent stay one step apart whatever kind it is.
            ink.color = if (state == 0) fade(paint.tints.getValue(Kind.PLAIN).face, 0.3f)
            else face(paint, button.key, state == 2)
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
            label(canvas, button.v, middle)
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
     * Sliding on the same way rides the stride again, so the line goes on to
     * where that ends. The heads count the slides it takes, and they are drawn
     * where the slide ends rather than where it passes through.
     *
     * A focus resting on a landing gets the one jump it can still ride, drawn
     * the same way. So the picture is always the sides of the cell the focus is
     * in, whether that cell is a key or a place a jump left it.
     */
    private fun drawPortals(canvas: Canvas, thumb: Thumb, paint: Palette, sketch: Sketch) {
        if (!thumb.live) return
        val spot = thumb.spot ?: return
        for (run in comb.slides(thumb.bank, spot, thumb.riding, thumb.keys)) {
            var from = spot
            for ((i, stop) in run.withIndex()) {
                drawStop(
                    canvas, paint, sketch, from, stop,
                    first = i == 0, heads = if (i == run.size - 1) run.size else 1,
                )
                from = stop.land.spot
            }
            drawAim(canvas, paint, sketch, spot, run.first())
        }
    }

    /**
     * The key a side aims at, written in the cell that side is crossed through.
     *
     * A ring at the far end says where a side goes, and the eye has to travel
     * the line to read it. The name sits where the thumb already is, just
     * inside the cell it is about to enter, so a side reads as a key.
     */
    private fun drawAim(canvas: Canvas, paint: Palette, sketch: Sketch, from: Spot, stop: Ride) {
        val middle = comb.point(from, sketch.at)
        val way = unit(middle, comb.edge(from, stop.side, sketch.at))
        // Past the side, and short of the crossed cell's own middle, so the two
        // names do not sit on each other.
        val out = (Comb.SQ3 * 0.5f + 0.45f) * sketch.radius
        val at = Point(middle.x + way.x * out, middle.y + way.y * out)
        val size = ink.textSize
        ink.style = Paint.Style.FILL
        // Its own ground. The cell beneath may carry a spent key's letter, and
        // over the app there is no ground at all.
        ink.color = paint.back
        canvas.drawCircle(at.x, at.y, size * 0.62f, ink)
        ink.textSize = size * 0.8f
        ink.color = paint.hot
        label(canvas, order[stop.land.aim], at)
        ink.textSize = size
    }

    /**
     * One place a slide comes to, with the line that reaches it.
     *
     * [heads] is how many slides it takes to arrive, which only the last stop
     * of a run can say. The line leaves the side the thumb crosses, so the
     * arrow points the way out and not at the cell it goes to.
     */
    private fun drawStop(
        canvas: Canvas, paint: Palette, sketch: Sketch,
        from: Spot, stop: Ride, first: Boolean, heads: Int,
    ) {
        val at = sketch.at
        val r = sketch.radius
        val leaves = comb.edge(from, stop.side, at)
        val start = if (first) leaves else comb.point(from, at)
        val to = comb.point(stop.land.spot, at)
        ink.style = Paint.Style.STROKE
        ink.strokeWidth = r * 0.05f
        ink.color = fade(paint.hot, 0.4f)
        ink.pathEffect = dashes
        canvas.drawLine(start.x, start.y, to.x, to.y, ink)
        ink.pathEffect = null
        if (sketch.over) solid(canvas, paint, sketch, stop.land.spot)
        ink.style = Paint.Style.STROKE
        ink.strokeWidth = r * 0.05f
        ink.pathEffect = if (stop.land.onKey) null else dashes
        ink.color = fade(paint.hot, if (stop.land.onKey) 0.85f else 0.5f)
        canvas.drawCircle(to.x, to.y, r * 0.42f, ink)
        ink.pathEffect = null
        ink.style = Paint.Style.FILL
        ink.color = fade(paint.hot, if (stop.land.onKey) 0.85f else 0.6f)
        // A key cell already carries its letter, so the arrow rides the ring
        // rather than sitting over it.
        val sit = if (stop.land.onKey) r * 0.42f else 0f
        val back = unit(to, comb.point(from, at))
        // The side already knows which way it points: it is the step from the
        // cell's middle out to the edge the slide leaves by.
        val way = unit(comb.point(from, at), leaves)
        arrow(canvas, to.x + back.x * sit, to.y + back.y * sit, way.x, way.y, r * 0.26f, heads)
    }

    /** A cell the band must not let the app through: a place the thumb is going. */
    private fun solid(canvas: Canvas, paint: Palette, sketch: Sketch, spot: Spot) {
        val path = cellPath(sketch, spot) ?: return
        val cut = keyCut(sketch, spot)
        ink.style = Paint.Style.FILL
        ink.color = if (cut != null) face(paint, cut.button.key, false) else paint.back
        canvas.drawPath(path, ink)
        if (cut == null) return
        ink.style = Paint.Style.STROKE
        ink.strokeWidth = sketch.radius * 0.055f
        ink.color = paint.edge
        canvas.drawPath(path, ink)
        if (!config.showLabels) return
        ink.style = Paint.Style.FILL
        ink.color = paint.text
        label(canvas, cut.button.v, cut.middle)
    }

    /** A key's letter, centred on its cell. */
    private fun label(canvas: Canvas, v: Char, at: Point) {
        canvas.drawText(v.toString(), at.x, at.y + ink.textSize * 0.35f, ink)
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
     * One head per slide it takes to arrive, which is the one thing the picture
     * cannot say by where it puts a ring.
     */
    private fun arrow(
        canvas: Canvas, x: Float, y: Float, dx: Float, dy: Float, size: Float, heads: Int,
    ) {
        // Along the way it points, and across it.
        fun at(lead: Float, on: Float, off: Float) = Point(
            x + dx * size * (on + lead) - dy * size * off,
            y + dy * size * (on + lead) + dx * size * off,
        )
        for (lead in List(heads) { 0.9f * (heads - 1 - 2 * it) }) {
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
        val cut = keyCut(sketch, spot)
        val path = cellPath(sketch, spot) ?: return
        // Warm only where a step would take a key; over ground and over a cell
        // the order has passed it says where, not what.
        val warm = face(paint, cut?.button?.key ?: -1, true)
        ink.style = Paint.Style.FILL
        ink.color = if (comb.resolve(spot, thumb.keys) != null) warm else fade(warm, 0.45f)
        canvas.drawPath(path, ink)
        if (cut == null || !config.showLabels) return
        ink.color = paint.text
        label(canvas, cut.button.v, cut.middle)
    }

    /**
     * Where the thumb is, and where the focus a jump left it went.
     *
     * Both are places on the comb rather than places on the glass, so the copy
     * over the app carries them into its own placement and shows the same two.
     */
    private fun drawMarks(canvas: Canvas, thumb: Thumb, paint: Palette, sketch: Sketch) {
        if (!thumb.live) return
        val here = sketches[thumb.bank]?.at ?: return
        val focus = comb.carry(here, sketch.at, Point(thumb.focusX, thumb.focusY))
        val held = comb.carry(here, sketch.at, Point(thumb.atX, thumb.atY))
        val mark = config.markRadius * min(thumb.field.width, thumb.field.height) *
            (sketch.radius / here.radius)
        ink.style = Paint.Style.FILL
        ink.color = paint.hot
        canvas.drawCircle(focus.x, focus.y, mark, ink)
        if (hypot(focus.x - held.x, focus.y - held.y) <= mark) return
        ink.color = fade(paint.hot, 0.35f)
        canvas.drawCircle(held.x, held.y, mark * 0.7f, ink)
        ink.style = Paint.Style.STROKE
        ink.strokeWidth = mark * 0.25f
        canvas.drawLine(held.x, held.y, focus.x, focus.y, ink)
    }

    /**
     * What the stroke in hand would write, if a thumb lifted now.
     *
     * The answer comes back from whoever owns the runtime, and it is kept with
     * the chord it is about. A chord the answer is not about is not drawn, so
     * the last stroke's text never sits over this one.
     */
    private var wroteFor = 0L
    private var wrote = ""

    fun writes(chord: Long, text: String) {
        wroteFor = chord
        wrote = text
        invalidate()
    }

    /** Ask what is in hand now, once per change of the chord. */
    private var asked = 0L

    private fun ask() {
        val mask = chorder.mask()
        if (mask == asked) return
        asked = mask
        onChord(mask)
    }

    private fun drawWriting(canvas: Canvas, paint: Palette, top: Float) {
        val text = if (chorder.mask() == wroteFor) wrote else ""
        if (text.isEmpty()) return
        ink.style = Paint.Style.FILL
        ink.color = fade(paint.text, 0.8f)
        canvas.drawText(
            text.takeLast(48), width * 0.5f, top + ink.textSize * 2.4f, ink
        )
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

    companion object {
        /** How long the band takes to come and go. */
        private const val FADE_MS = 130f
    }
}
