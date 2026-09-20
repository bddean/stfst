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
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

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

    /** Where each touch was last measured from, which is what a stall is read against. */
    private val marks = mutableMapOf<Int, Tap>()
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
    fun sideWidth(width: Float): Float = ring.spans(width, sides = true).first.endInclusive

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
        val room = if (MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED) null
        else MeasureSpec.getSize(heightSpec).toFloat()
        val (keys, band) = ring.deep(
            width.toFloat(), resources.displayMetrics.heightPixels.toFloat(),
            landscape && !embedded, room,
        )
        keysDeep = keys.toInt()
        setMeasuredDimension(width, keysDeep + band.toInt())
    }

    /**
     * The horizontal run each thumb's field covers. Portrait splits the whole
     * width; in landscape each field hugs its own side and the ground between
     * them belongs to neither.
     */
    private fun spans(
        width: Float,
    ): Pair<ClosedFloatingPointRange<Float>, ClosedFloatingPointRange<Float>> =
        ring.spans(width, landscape && config.landscapeSides)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = layoutFields()

    private fun layoutFields() {
        val (left, right) = spans(width.toFloat())
        val floor = height.toFloat()
        val top = keysTop + if (config.touchReadout) keysDeep * 0.04f else 0f
        chorder.place(Bank.LEFT, Field(left.start, top, left.endInclusive, floor))
        chorder.place(Bank.RIGHT, Field(right.start, top, right.endInclusive, floor))
        cut()
    }

    /** The same bank, drawn large in the band over the app, over its own half. */
    private fun band(bank: Bank): Field {
        val field = chorder.thumb(bank).field
        return Field(field.left, 0f, field.right, keysTop.toFloat())
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
                val bank = bankAt(x)?.let { chorder.bankFor(it) } ?: return false
                clouds[bank]?.clear()
                record(event, at, bank)
                // A double tap arms the side's key and then goes down as a
                // normal touch, so tap tap on -T releases as -*T. Its own lift
                // is not recorded as a tap, which writes it at once.
                if (!doubleTap(bank, x, y, event.eventTime)) {
                    chorder.flush()
                    downs[pointer] = Tap(x, y, event.eventTime)
                    marks[pointer] = Tap(x, y, event.eventTime)
                    armLongPress(bank, pointer)
                }
                if (chorder.down(bank, pointer, x, y)) haptics.take(bank)
                tell(bank)
            }

            MotionEvent.ACTION_MOVE -> {
                for (at in 0 until event.pointerCount) {
                    val pointer = event.getPointerId(at)
                    val thumb = chorder.forPointer(pointer) ?: continue
                    record(event, at, thumb.bank)
                    stir(pointer, thumb.bank, event.getX(at), event.getY(at))
                    // One hand writes a whole stroke: either the halves swap
                    // and the next touch walks the other bank, or the touch
                    // itself is handed over and keeps sliding.
                    val other = bankAt(event.getX(at))?.let { chorder.bankFor(it) }
                    if (other != null && other != thumb.bank) {
                        if (config.crossMiddle == Config.SWAP) {
                            if (chorder.swap(pointer)) {
                                haptics.take(thumb.bank)
                                dropLongPress(pointer)
                            }
                            continue
                        }
                        if (chorder.cross(pointer, other, event.getX(at), event.getY(at))) {
                            haptics.take(other)
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
                marks.remove(pointer)
                dropLongPress(pointer)
                val thumb = chorder.forPointer(pointer)
                val tap = config.doubleTapWindow() > 0 && down != null &&
                    event.eventTime - down.time <= config.doubleTapWindow()
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
                marks.clear()
                for (pointer in presses.keys.toList()) dropLongPress(pointer)
                lastTaps.clear()
                chorder.reset()
            }
        }
        // The halves change places on a crossing and back again on a stroke,
        // and the shapes are cut per layout, so the cut follows the swap.
        if (chorder.swapped != cutSwapped) cut()
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
        if (config.doubleTapWindow() <= 0) return false
        val tap = lastTaps.remove(bank) ?: return false
        if (now - tap.time > config.doubleTapWindow()) return false
        if (hypot(x - tap.x, y - tap.y) > height * 0.05f) return false
        val key = ring.keys.indexOf(config.doubleTap(bank))
        if (key < 0) return false
        chorder.cancelHeld()
        chorder.stick(key)
        haptics.take(bank)
        return true
    }

    /** The timers waiting to see a touch stand still long enough to be a press. */
    private val presses = mutableMapOf<Int, Runnable>()

    /**
     * A touch that stands still arms the bank's long-press key.
     *
     * The other way to a modifier without a trip to the row: the thumb is
     * already on the key it wants, and staying put costs it no travel.
     */
    private fun armLongPress(bank: Bank, pointer: Int) {
        if (config.longPressWait() <= 0) return
        val key = ring.keys.indexOf(config.longPress(bank))
        if (key < 0) return
        val task = Runnable {
            presses.remove(pointer)
            if (marks[pointer] == null || chorder.forPointer(pointer) == null) return@Runnable
            chorder.stick(key)
            haptics.take(bank)
            invalidate()
        }
        presses[pointer] = task
        postDelayed(task, config.longPressWait())
    }

    private fun dropLongPress(pointer: Int) {
        presses.remove(pointer)?.let { removeCallbacks(it) }
    }

    /**
     * A touch has moved off the spot it was last measured from: it is no longer
     * a tap, and what that does to the long press is which clock it waits on.
     *
     * From the touch going down, the press is a tap held, and moving gives it
     * up. From the last movement, it is a stall, so the clock starts again
     * wherever the thumb has arrived.
     */
    private fun stir(pointer: Int, bank: Bank, x: Float, y: Float) {
        val mark = marks[pointer] ?: return
        if (hypot(x - mark.x, y - mark.y) <= height * 0.02f) return
        downs.remove(pointer)
        dropLongPress(pointer)
        if (config.longPressFrom != Config.STILLNESS) {
            marks.remove(pointer)
            return
        }
        marks[pointer] = Tap(x, y, 0L)
        armLongPress(bank, pointer)
    }

    private fun armFlush() {
        flusher?.let { removeCallbacks(it) }
        val task = Runnable {
            chorder.flush()
            invalidate()
        }
        flusher = task
        postDelayed(task, config.doubleTapWindow())
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

    /**
     * Two tones, and the texture carries the kind: a key is an outline on the
     * ground until it is in the chord, and then it is filled.
     */
    private class Palette(
        /** The ground, and a label on a cell in the chord. */
        val ground: Int,
        /** Labels, outlines, and a cell in the chord. */
        val ink: Int,
    )

    /**
     * What a key is to the eye.
     *
     * The vowels are what a stroke turns on, and the modifiers are the keys
     * that spell nothing by themselves. Both are read off the glyph, so a
     * layout that moves them does not have to say so twice.
     */
    private enum class Kind { PLAIN, VOWEL, MOD }

    private fun kindOf(key: Int): Kind = when {
        key < 0 -> Kind.PLAIN
        ring.keys[key].uppercase() in VOWELS -> Kind.VOWEL
        !ring.keys[key].all { it.isLetter() } -> Kind.MOD
        else -> Kind.PLAIN
    }

    /**
     * The dither a kind of key is drawn with: how far apart the dots are and
     * how big they are, in fractions of the bank.
     *
     * The consonants are the many, and they get the plain face. The other two
     * differ in the size of the dot as well as the spacing, so they can be told
     * apart without reading either.
     */
    private class Grain(val spacing: Float, val radius: Float)

    private fun grainOf(kind: Kind): Grain? {
        if (!config.dither) return null
        val spacing = when (kind) {
            Kind.PLAIN -> return null
            Kind.VOWEL -> config.grainFine
            Kind.MOD -> config.grainCoarse
        }
        // The dot follows the spacing: a grain is one number, and a dither
        // whose dots outgrow their gaps is a fill.
        return Grain(spacing, spacing * DOT)
    }

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

    private fun labelSize(): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, config.labelSp, resources.displayMetrics
    )

    /**
     * The board's two colours. Everything else on it is one of them, faded.
     *
     * `system` asks the phone, which is the only one of the three that can
     * change under the keyboard, so it is read at every draw rather than kept.
     */
    private fun palette(): Palette {
        val dark = when (config.theme) {
            "dark" -> true
            "light" -> false
            else -> resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
        return if (dark) Palette(ground = Color.rgb(20, 20, 20), ink = Color.rgb(232, 232, 232))
        else Palette(ground = Color.WHITE, ink = Color.rgb(24, 24, 24))
    }

    override fun onDraw(canvas: Canvas) {
        val paint = palette()
        // Only the fields get a ground, and only where the keys are. Above them
        // and between them the app shows through, which is what the band is for.
        ink.style = Paint.Style.FILL
        ink.color = paint.ground
        for (bank in Bank.values()) {
            val field = chorder.thumb(bank).field
            canvas.drawRect(field.left, keysTop.toFloat(), field.right, height.toFloat(), ink)
        }
        ink.textSize = labelSize()
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

    /** One cell as drawn: its outline, its dither, its keys, and its label's place. */
    private class Cut(
        val cell: Cell,
        val keys: List<Int>,
        val path: Path,
        val grain: Path?,
        val middle: Point,
        /** How far the cell reaches from its middle, for what is drawn inside it. */
        val half: Float,
    )

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

    private var cutSwapped = false

    private fun cut() {
        cutSwapped = chorder.swapped
        // The dither is cut around the labels, so their size has to be known here.
        ink.textSize = labelSize()
        sketches = Bank.values().associateWith { carve(it, chorder.thumb(it).field) }
        bands = if (keysTop <= 0) emptyMap()
        else Bank.values().associateWith { carve(it, band(it)) }
    }

    private fun carve(bank: Bank, field: Field): Sketch {
        val unit = min(field.width, field.height)
        return Sketch(
            cuts = ring.cells(bank).mapNotNull { cell ->
                val outline = ring.outline(bank, field, cell)
                val path = polygon(outline) ?: return@mapNotNull null
                val keys = ring.keys(bank, cell)
                val middle = ring.middle(bank, field, cell)
                // The row says what it is by being the row, so the dither is
                // for the rings, where the kinds are mixed.
                val kind = when {
                    cell.isRow -> null
                    ring.isUpgrade(bank, cell) -> Kind.MOD
                    else -> keys.firstOrNull()?.let { kindOf(it) }
                }
                val name = if (ring.isUpgrade(bank, cell)) UPGRADE else spell(keys)
                val grain = kind?.let { grainOf(it) }
                    ?.let { stipple(outline, middle, name, unit, it) }
                Cut(
                    cell, keys, path, grain, middle,
                    outline.maxOf { hypot(it.x - middle.x, it.y - middle.y) },
                )
            },
            field = field,
            unit = unit,
        )
    }

    /** A candidate dot: where it is, how deep inside the cell, and its rank. */
    private class Dot(val x: Float, val y: Float, val deep: Float, val rank: Float)

    /**
     * The dither inside one cell.
     *
     * Dots of one size, placed by [Noise] so they neither line up nor clump,
     * thinning out to nothing at the cell's rim and around its label. A dither
     * running under a letter is what makes the letter hard to read, and the
     * fade is what buys the texture without paying that.
     *
     * What the fade changes is how many dots there are, not how big they are,
     * because a prefix of [Noise] is a blue-noise set at a lower density and a
     * shrinking dot is a blur.
     */
    private fun stipple(
        outline: List<Point>,
        middle: Point,
        text: String,
        unit: Float,
        grain: Grain,
    ): Path? {
        // One period of the pattern on the glass. It tiles seamlessly, so this
        // only sets how far apart a repeat of the same arrangement is.
        val tile = unit * 0.5f
        val perTile = ((tile / (grain.spacing * unit)).pow(2)).toInt().coerceIn(1, Noise.COUNT)
        val radius = grain.radius * unit
        val hole = unit * 0.015f
        val holeX = ink.measureText(text) * 0.5f + hole
        val holeY = ink.textSize * 0.5f + hole
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (p in outline) {
            left = min(left, p.x); right = max(right, p.x)
            top = min(top, p.y); bottom = max(bottom, p.y)
        }
        val points = Noise.points
        val dots = ArrayList<Dot>()
        var deepest = 0f
        for (ty in floor(top / tile).toInt()..floor(bottom / tile).toInt()) {
            for (tx in floor(left / tile).toInt()..floor(right / tile).toInt()) {
                for (i in 0 until perTile) {
                    val x = (tx + points[i].x) * tile
                    val y = (ty + points[i].y) * tile
                    if (x < left || x > right || y < top || y > bottom) continue
                    if (!inside(outline, x, y)) continue
                    val deep = edgeFar(outline, x, y)
                    deepest = max(deepest, deep)
                    dots.add(Dot(x, y, deep, (i + 0.5f) / perTile))
                }
            }
        }
        // The rim bands are thinner than the fade wants to be, and a fade wider
        // than the cell leaves a clump down the middle rather than a texture.
        val band = min(unit * 0.022f, deepest * 0.55f)
        val path = Path()
        var any = false
        for (dot in dots) {
            val rim = fringe(dot.deep / band - 0.3f)
            val far = hypot((dot.x - middle.x) / holeX, (dot.y - middle.y) / holeY)
            if (dot.rank > min(rim, fringe((far - 1f) / 0.7f))) continue
            path.addCircle(dot.x, dot.y, radius, Path.Direction.CW)
            any = true
        }
        return if (any) path else null
    }

    /** Smooth 0 below, 1 above, for fading a dither in over a distance. */
    private fun fringe(t: Float): Float =
        t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

    private fun inside(poly: List<Point>, x: Float, y: Float): Boolean {
        var hit = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[j]
            if ((a.y > y) != (b.y > y) &&
                x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x
            ) hit = !hit
            j = i
        }
        return hit
    }

    /** How far a point inside a polygon is from its boundary. */
    private fun edgeFar(poly: List<Point>, x: Float, y: Float): Float {
        var best = Float.MAX_VALUE
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len = dx * dx + dy * dy
            val t = if (len == 0f) 0f else (((x - a.x) * dx + (y - a.y) * dy) / len).coerceIn(0f, 1f)
            best = min(best, hypot(x - (a.x + t * dx), y - (a.y + t * dy)))
        }
        return best
    }

    private fun cutAt(sketch: Sketch, cell: Cell): Cut? = sketch.cuts.firstOrNull { it.cell == cell }

    /**
     * What a cell is to the chord in hand. Three different answers: in the
     * chord, still takeable, or spent by steno order.
     */
    private enum class State { HELD, LIVE, DEAD }

    private fun stateOf(thumb: Thumb, cell: Cell, keys: List<Int>): State = when {
        ring.isUpgrade(thumb.bank, cell) -> if (chorder.upgrade) State.HELD else State.LIVE
        keys.isEmpty() -> State.DEAD
        keys.all { it in thumb.keys || it in chorder.sticky } -> State.HELD
        keys.any { ring.live(it, thumb.keys) } -> State.LIVE
        else -> State.DEAD
    }

    /** Every cell of one bank: its face, its dither, its outline, and its label. */
    private fun drawCells(canvas: Canvas, thumb: Thumb, paint: Palette, sketch: Sketch) {
        for (cut in sketch.cuts) {
            // A cell the thumb has been through says what it flips rather than
            // its own key, because that is what entering it would do.
            val flip = ring.flip(thumb.bank, cut.cell, thumb.keys)
            val keys = if (flip < 0) cut.keys else listOf(flip)
            val state = stateOf(thumb, cut.cell, keys)
            if (state == State.DEAD && !config.showDeadCells) continue
            ink.style = Paint.Style.FILL
            ink.color = if (state == State.HELD) paint.ink else paint.ground
            canvas.drawPath(cut.path, ink)
            // The dither flips with the cell, so the kind of key still reads
            // once the cell is in the chord.
            cut.grain?.let {
                ink.color = when (state) {
                    State.HELD -> fade(paint.ground, 0.9f)
                    State.LIVE -> fade(paint.ink, 0.35f)
                    State.DEAD -> fade(paint.ink, 0.1f)
                }
                canvas.drawPath(it, ink)
            }
            if (state != State.HELD) {
                ink.style = Paint.Style.STROKE
                ink.strokeWidth = sketch.unit * 0.005f
                ink.color = fade(paint.ink, if (state == State.LIVE) 0.55f else 0.15f)
                canvas.drawPath(cut.path, ink)
            }
            // The toggle is a switch rather than a key, and the ring round it
            // says so: it is the one cell an entry turns over instead of
            // spending.
            if (ring.isUpgrade(thumb.bank, cut.cell)) {
                ink.style = Paint.Style.STROKE
                ink.strokeWidth = sketch.unit * 0.006f
                ink.color = if (state == State.HELD) paint.ground else fade(paint.ink, 0.5f)
                canvas.drawCircle(cut.middle.x, cut.middle.y, cut.half * 0.72f, ink)
            }
            if (!config.showLabels) continue
            ink.style = Paint.Style.FILL
            ink.color = when (state) {
                State.HELD -> paint.ground
                State.LIVE -> paint.ink
                State.DEAD -> fade(paint.ink, 0.25f)
            }
            label(canvas, text(thumb.bank, cut.cell, keys), cut.middle, markOf(keys, state))
        }
    }

    /**
     * The cell the thumb is in, filled.
     *
     * What is filled is what a further step would do, which is the answer
     * before it is given. Over ground and over a cell the order has passed it
     * says where the thumb is, not what it would write.
     */
    private fun drawHere(canvas: Canvas, thumb: Thumb, paint: Palette, sketch: Sketch) {
        if (!thumb.live) return
        val cell = thumb.cell ?: return
        val cut = cutAt(sketch, cell) ?: return
        val flip = ring.flip(thumb.bank, cell, thumb.keys)
        val keys = if (flip < 0) cut.keys else listOf(flip)
        val takes = flip >= 0 || ring.taking(thumb.bank, cell, thumb.keys).isNotEmpty()
        ink.style = Paint.Style.FILL
        ink.color = if (takes) paint.ink else fade(paint.ink, 0.35f)
        canvas.drawPath(cut.path, ink)
        cut.grain?.let {
            ink.color = fade(paint.ground, if (takes) 0.9f else 0.5f)
            canvas.drawPath(it, ink)
        }
        if (!config.showLabels) return
        ink.color = if (takes) paint.ground else paint.ink
        label(canvas, text(thumb.bank, cell, keys), cut.middle)
    }

    /**
     * Where the thumb is.
     *
     * A place on the rings rather than a place on the glass, so the copy over
     * the app reads it in its own field and shows the same place.
     */
    private fun drawMark(canvas: Canvas, thumb: Thumb, paint: Palette, where: Field) {
        if (!thumb.live) return
        val at = ring.mirror(thumb.bank, thumb.field, where, thumb.atX, thumb.atY)
        val r = config.markRadius * min(where.width, where.height)
        ink.style = Paint.Style.FILL
        ink.color = paint.ink
        canvas.drawCircle(at.x, at.y, r, ink)
        // A ring of the ground around it, so the mark shows on a cell whose
        // face is the ink as well as on one whose face is not.
        ink.style = Paint.Style.STROKE
        ink.strokeWidth = r * 0.4f
        ink.color = paint.ground
        canvas.drawCircle(at.x, at.y, r * 1.2f, ink)
    }

    /**
     * What a label says about its cell's state beyond its tone: a strike for a
     * key steno order has passed, an underline for one armed for the next
     * stroke.
     */
    private enum class Mark { NONE, SPENT, ARMED }

    private fun markOf(keys: List<Int>, state: State): Mark = when {
        state == State.DEAD -> Mark.SPENT
        keys.isNotEmpty() && keys.all { it in chorder.sticky } -> Mark.ARMED
        else -> Mark.NONE
    }

    private fun spell(keys: List<Int>): String = ring.keys.spell(keys)

    /**
     * What a cell says now. The upgrade names what it writes rather than the
     * key it is, and a key it has lifted is drawn as what it would write.
     */
    private fun text(bank: Bank, cell: Cell, keys: List<Int>): String =
        if (ring.isUpgrade(bank, cell)) UPGRADE else spell(keys.map { chorder.lift(it) })

    /** A cell's label, centred on it, under the mark its state calls for. */
    private fun label(canvas: Canvas, text: String, at: Point, mark: Mark = Mark.NONE) {
        if (text.isEmpty()) return
        canvas.drawText(text, at.x, at.y + ink.textSize * 0.35f, ink)
        if (mark == Mark.NONE) return
        val half = ink.measureText(text) * 0.5f + ink.textSize * 0.16f
        val y = at.y + if (mark == Mark.SPENT) 0f else ink.textSize * 0.72f
        ink.style = Paint.Style.STROKE
        ink.strokeWidth = ink.textSize * 0.075f
        canvas.drawLine(at.x - half, y, at.x + half, y, ink)
        ink.style = Paint.Style.FILL
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
        ink.color = fade(paint.ink, 0.8f)
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
        ink.color = paint.ink
        // In steno order, not take order, so what is shown is the outline.
        canvas.drawText(
            spell(keys.sorted().map { chorder.lift(it) }),
            where.centerX, where.top + ink.textSize, ink
        )
    }

    private fun drawCloud(canvas: Canvas, bank: Bank, paint: Palette) {
        val cloud = clouds[bank] ?: return
        val field = chorder.thumb(bank).field
        val unit = min(field.width, field.height)
        ink.style = Paint.Style.FILL
        ink.color = fade(paint.ink, config.cloudAlpha)
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
        ink.color = fade(paint.ink, 0.65f)
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
        /** What the upgrade's own cell says: the keys it writes, not one it is. */
        private const val UPGRADE = "dz"

        private val VOWELS = setOf("A", "O", "E", "U")

        /** A dither dot's radius, as a share of the spacing between dots. */
        private const val DOT = 0.165f

        /** How long the band takes to come and go. */
        private const val FADE_MS = 130f
    }
}
