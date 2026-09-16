package cx.yeet.stfst

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The keyboard itself: two thumb banks of nested rings, and the touch handling
 * that turns taps and slides into strokes.
 *
 * Drawing reads the chorder and decides nothing. Every shape is asked of
 * [Ring], the outline of a cell included, so the picture is the decision
 * function rather than a copy of it that can drift.
 */
@SuppressLint("ViewConstructor")
class RingView(
    context: Context,
    private var config: Config,
    private val order: String,
    private val onStroke: (Long) -> Unit,
    /** The chord in hand changed. Whoever can say what it writes answers with [writes]. */
    private val onChord: (Long) -> Unit = {},
    /** True in the settings preview, which has no app to lay out around. */
    private val embedded: Boolean = false,
) : View(context) {

    private var ring = Ring(order, config)
    private var chorder = Chorder(ring, config) { onStroke(it) }
    private val haptics = Haptics(context, config)

    /** The last cell each thumb was told about, so a tick reports a change only. */
    private val told = mutableMapOf<Bank, Cell?>(Bank.LEFT to null, Bank.RIGHT to null)

    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Where and when a touch went down, to tell a quick tap from a slide. */
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

    /** Whether the two banks stand on the edges with the bar in the gap. */
    val sides: Boolean get() = !embedded && landscape && config.landscapeSides

    /** How wide each edge field is, which whatever sits in the gap must clear. */
    fun sideWidth(width: Float): Float = width * config.splitLandscape.coerceIn(0.15f, 0.5f)

    fun apply(fresh: Config) {
        config = fresh
        ring = Ring(order, fresh)
        chorder = Chorder(ring, fresh) { onStroke(it) }
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
        // keys off the glass, so the keys and the band give up room together.
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

    /**
     * Report the cell under the thumb, once per change.
     *
     * Where the thumb is becomes something to feel rather than something to
     * read, which is what using this without looking needs.
     */
    private fun tell(bank: Bank) {
        val cell = chorder.thumb(bank).cell
        if (told[bank] == cell) return
        told[bank] = cell
        if (cell != null) haptics.tick(bank)
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
        order[key].uppercaseChar() in "AOEU" -> Kind.VOWEL
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
        // Only the fields get a ground, and only where the keys are. Above them
        // and between them the app shows through, which is what the band is for.
        ink.style = Paint.Style.FILL
        ink.color = paint.back
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
        drawCells(canvas, thumb, paint, sketch)
        drawHere(canvas, thumb, paint, sketch)
        drawMark(canvas, thumb, paint, where)
        drawTaken(canvas, thumb, paint, where)
    }

    /** One cell as drawn: its outline, its keys, and where its label sits. */
    private class Cut(val cell: Cell, val keys: List<Int>, val path: Path, val middle: Point)

    /**
     * The shapes of one bank, which depend on the layout and the field alone.
     *
     * Only the colours change as a stroke is built, so the paths are cut once
     * per layout rather than once per frame. [Ring] is still the only source of
     * them: what is cached is the answer, not a second way of working it out.
     */
    private class Sketch(val cuts: List<Cut>, val field: Field, val unit: Float)

    private var sketches: Map<Bank, Sketch> = emptyMap()
    private var bands: Map<Bank, Sketch> = emptyMap()

    private fun cut() {
        sketches = Bank.values().associateWith { carve(it, chorder.thumb(it).field) }
        bands = if (keysTop <= 0) emptyMap()
        else Bank.values().associateWith { carve(it, band(it)) }
    }

    private fun carve(bank: Bank, field: Field): Sketch = Sketch(
        cuts = ring.cells(bank).mapNotNull { cell ->
            polygon(ring.outline(bank, field, cell))?.let {
                Cut(
                    cell, ring.keys(bank, cell), it,
                    ring.point(bank, field, ring.centre(bank, cell)),
                )
            }
        },
        field = field,
        unit = min(field.width, field.height),
    )

    private fun cutAt(sketch: Sketch, cell: Cell): Cut? = sketch.cuts.firstOrNull { it.cell == cell }

    /** Every cell of one bank: its ground, its outline, and its label. */
    private fun drawCells(canvas: Canvas, thumb: Thumb, paint: Palette, sketch: Sketch) {
        val held = (thumb.keys + chorder.sticky).toSet()
        for (cut in sketch.cuts) {
            // Three things a cell can be, and they are three different answers:
            // in the chord, still available, or spent by steno order.
            val state = when {
                cut.keys.all { it in held } -> 2
                cut.keys.any { ring.live(it, thumb.keys) } -> 1
                else -> 0
            }
            if (state == 0 && !config.showDeadCells) continue
            ink.style = Paint.Style.FILL
            // A spent key gives up its hue. Kind is a question about live keys,
            // so held and spent stay one step apart whatever kind it is.
            ink.color = if (state == 0) fade(paint.tints.getValue(Kind.PLAIN).face, 0.3f)
            else face(paint, cut.keys.first(), state == 2)
            canvas.drawPath(cut.path, ink)
            ink.style = Paint.Style.STROKE
            ink.strokeWidth = sketch.unit * if (state == 2) 0.009f else 0.005f
            ink.color = when (state) {
                2 -> paint.hot
                1 -> paint.edge
                else -> fade(paint.edge, 0.4f)
            }
            canvas.drawPath(cut.path, ink)
            if (!config.showLabels) continue
            ink.style = Paint.Style.FILL
            ink.color = when (state) {
                2 -> paint.hot
                1 -> paint.text
                else -> fade(paint.text, 0.3f)
            }
            label(canvas, cut.keys, cut.middle)
        }
    }

    /**
     * The cell the thumb is in, filled.
     *
     * What is filled is what a further step would take, which is the answer
     * before it is given. Over ground and over a cell the order has passed it
     * says where the thumb is, not what it would write.
     */
    private fun drawHere(canvas: Canvas, thumb: Thumb, paint: Palette, sketch: Sketch) {
        if (!thumb.live) return
        val cell = thumb.cell ?: return
        val cut = cutAt(sketch, cell) ?: return
        val warm = face(paint, cut.keys.first(), true)
        ink.style = Paint.Style.FILL
        ink.color = if (ring.taking(thumb.bank, cell, thumb.keys).isNotEmpty()) warm
        else fade(warm, 0.45f)
        canvas.drawPath(cut.path, ink)
        if (!config.showLabels) return
        ink.color = paint.text
        label(canvas, cut.keys, cut.middle)
    }

    /**
     * Where the thumb is.
     *
     * A place on the rings rather than a place on the glass, so the copy over
     * the app reads it in its own field and shows the same place.
     */
    private fun drawMark(canvas: Canvas, thumb: Thumb, paint: Palette, where: Field) {
        if (!thumb.live) return
        val at = ring.point(
            thumb.bank, where, ring.polar(thumb.bank, thumb.field, thumb.atX, thumb.atY)
        )
        ink.style = Paint.Style.FILL
        ink.color = paint.hot
        canvas.drawCircle(at.x, at.y, config.markRadius * min(where.width, where.height), ink)
    }

    /** A cell's keys, centred on it. */
    private fun label(canvas: Canvas, keys: List<Int>, at: Point) {
        if (keys.isEmpty()) return
        val text = keys.joinToString("") { order[it].toString() }
        canvas.drawText(text, at.x, at.y + ink.textSize * 0.35f, ink)
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
        val armed = chorder.sticky.filter { ring.bankOf(it) == thumb.bank }
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
        val field = chorder.thumb(bank).field
        val unit = min(field.width, field.height)
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
