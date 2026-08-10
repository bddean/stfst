package cx.yeet.stfst

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * The keyboard itself: two thumb fields, a layout in each, and the touch handling
 * that turns taps and slides into strokes.
 *
 * Drawing reads the chorder and decides nothing. What is drawn is asked of `Ring`,
 * including which directions belong to which key, so the picture is the decision
 * function and cannot drift from it.
 */
@SuppressLint("ViewConstructor")
class RadialView(
    context: Context,
    private var config: Config,
    private val order: String,
    private val onStroke: (Long) -> Unit,
    private val onKey: (Int, Int) -> Unit,
) : View(context) {

    private var ring = Ring(order, config)
    private var chorder = Chorder(ring, config) { onStroke(it) }
    private val haptics = Haptics(context, config)

    /** The last key each thumb was told about, so a tick reports a change only. */
    private val told = mutableMapOf(Bank.LEFT to Thumb.NO_KEY, Bank.RIGHT to Thumb.NO_KEY)

    /** Sticky modifiers a long press has turned on, as a `KeyEvent` meta mask. */
    private var meta = 0
    /** The thumb whose long press is open, if any. */
    private var modifying: Bank? = null

    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stalls = mutableMapOf<Bank, Runnable>()
    private val settles = mutableMapOf<Bank, Runnable>()
    private val holds = mutableMapOf<Int, Runnable>()

    private var unit = 1f

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

    /** Every sample of the press in progress, per thumb. */
    private val clouds = mapOf(
        Bank.LEFT to ArrayDeque<Touch>(), Bank.RIGHT to ArrayDeque<Touch>()
    )

    /** What a long press offers: a sticky modifier, or a key to send. */
    private val modifiers = listOf(
        "⇧" to KeyEvent.META_SHIFT_ON,
        "⌃" to KeyEvent.META_CTRL_ON,
        "⌥" to KeyEvent.META_ALT_ON,
        "⌘" to KeyEvent.META_META_ON,
    )
    private val specials = listOf(
        "⏎" to KeyEvent.KEYCODE_ENTER,
        "⇥" to KeyEvent.KEYCODE_TAB,
        "⌫" to KeyEvent.KEYCODE_DEL,
        "⎋" to KeyEvent.KEYCODE_ESCAPE,
        "←" to KeyEvent.KEYCODE_DPAD_LEFT,
        "→" to KeyEvent.KEYCODE_DPAD_RIGHT,
        "↑" to KeyEvent.KEYCODE_DPAD_UP,
        "↓" to KeyEvent.KEYCODE_DPAD_DOWN,
    )

    fun apply(fresh: Config) {
        config = fresh
        ring = Ring(order, fresh)
        chorder = Chorder(ring, fresh) { onStroke(it) }
        haptics.apply(fresh)
        layoutFields()
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val width = MeasureSpec.getSize(widthSpec)
        val screen = resources.displayMetrics.heightPixels
        setMeasuredDimension(width, (screen * config.keyboardHeight).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = layoutFields()

    /**
     * The two fields. A field is where its thumb's keys are laid out for a tap, and
     * where its home point and its copy of the ring sit.
     */
    private fun layoutFields() {
        val edge = width * config.fieldSplit
        unit = min(min(edge, width - edge), height.toFloat())
        val top = if (config.touchReadout) unit * 0.09f else 0f
        chorder.place(Bank.LEFT, Field(0f, top, edge, height.toFloat()))
        chorder.place(Bank.RIGHT, Field(edge, top, width.toFloat(), height.toFloat()))
    }

    /** How far from the mark a ring is drawn, and picked at. */
    private fun reach(radius: Radius): Float = config.reach(radius) * unit

    private fun bankAt(x: Float) = if (x < width * config.fieldSplit) Bank.LEFT else Bank.RIGHT

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val at = event.actionIndex
                val pointer = event.getPointerId(at)
                val bank = bankAt(event.getX(at))
                clouds[bank]?.clear()
                record(event, at, bank)
                if (chorder.down(bank, pointer, event.getX(at), event.getY(at))) haptics.take(bank)
                tell(bank)
                armStall(bank)
                armSettle(bank)
                armHold(bank, pointer)
            }

            MotionEvent.ACTION_MOVE -> {
                for (at in 0 until event.pointerCount) {
                    val pointer = event.getPointerId(at)
                    val thumb = chorder.forPointer(pointer) ?: continue
                    record(event, at, thumb.bank)
                    // Any real movement means the press is a slide, not a hold.
                    holds.remove(pointer)?.let { removeCallbacks(it) }
                    val bank = modifying
                    if (bank == thumb.bank) {
                        pickModifier(thumb, event.getX(at), event.getY(at))
                        continue
                    }
                    if (chorder.move(pointer, event.getX(at), event.getY(at), unit, event.eventTime)) {
                        haptics.take(thumb.bank)
                    }
                    tell(thumb.bank)
                    armStall(thumb.bank)
                    armSettle(thumb.bank)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pointer = event.getPointerId(event.actionIndex)
                holds.remove(pointer)?.let { removeCallbacks(it) }
                val thumb = chorder.forPointer(pointer)
                if (thumb != null && modifying == thumb.bank) {
                    modifying = null
                    chorder.thumb(thumb.bank).keys.clear()
                    chorder.up(pointer)
                } else if (chorder.up(pointer) != 0L) {
                    haptics.stroke(thumb?.bank ?: Bank.LEFT)
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                modifying = null
                chorder.reset()
            }
        }
        invalidate()
        return true
    }

    /**
     * A move event carries the samples taken between frames, so reading the history
     * gives the whole touched path and not one point per frame.
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

    /** A ring that has drifted goes home when the thumb holds still. */
    private fun armStall(bank: Bank) {
        stalls.remove(bank)?.let { removeCallbacks(it) }
        val task = Runnable {
            chorder.stall(bank)
            invalidate()
        }
        stalls[bank] = task
        postDelayed(task, config.stallMs)
    }

    /**
     * A thumb that has stopped sends no more events, so the wait a near key serves
     * has to be ended here.
     */
    private fun armSettle(bank: Bank) {
        settles.remove(bank)?.let { removeCallbacks(it) }
        if (config.settleMs <= 0) return
        val task = Runnable {
            if (chorder.settle(bank, unit, SystemClock.uptimeMillis())) haptics.take(bank)
            tell(bank)
            invalidate()
        }
        settles[bank] = task
        postDelayed(task, config.settleMs + 8)
    }

    private fun armHold(bank: Bank, pointer: Int) {
        if (!config.longPress) return
        val task = Runnable {
            modifying = bank
            chorder.thumb(bank).keys.clear()
            haptics.stroke(bank)
            invalidate()
        }
        holds[pointer] = task
        postDelayed(task, config.longPressMs)
    }

    /**
     * In modifier mode the near ring toggles a sticky modifier and the far ring
     * sends a key with whatever is sticky, which is how `⌃` then `←` becomes one
     * key event and not two.
     */
    private fun pickModifier(thumb: Thumb, x: Float, y: Float) {
        val dx = x - thumb.markX
        val dy = y - thumb.markY
        val distance = hypot(dx, dy)
        if (distance < config.minSlide * unit) return
        val heading = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val band = if (distance < config.ringSplit() * unit) Radius.NEAR else Radius.FAR
        val near = band == Radius.NEAR
        val items: List<Pair<String, Int>> = if (near) modifiers else specials
        val step = 360f / items.size
        val start = config.startAngle(thumb.bank, band)
        var best = 0
        var bestOff = Float.MAX_VALUE
        for (i in items.indices) {
            val off = Ring.separation(start + i * step, heading)
            if (off < bestOff) {
                best = i
                bestOff = off
            }
        }
        thumb.markX = x
        thumb.markY = y
        haptics.take(thumb.bank)
        if (near) {
            meta = meta xor items[best].second
        } else {
            onKey(items[best].second, meta)
            meta = 0
            modifying = null
        }
    }

    /** The same colour, weaker, for the copy that is not being aimed at. */
    private fun fade(color: Int, dim: Float): Int =
        if (dim >= 1f) color else Color.argb(
            (Color.alpha(color) * dim).toInt(), Color.red(color), Color.green(color), Color.blue(color)
        )

    /**
     * Report the key under a thumb, once per change.
     *
     * The aim of a slide becomes something to feel rather than something to read,
     * which is what using this without looking needs. Nothing is reported when the
     * thumb is over no key, or the tick would fire twice for one flick: a key is
     * taken and the mark moves to the fingertip, which is over nothing at all.
     */
    private fun tell(bank: Bank) {
        val hover = chorder.hover(bank)
        if (told[bank] == hover) return
        told[bank] = hover
        if (hover != Thumb.NO_KEY) haptics.tick(bank)
    }

    private class Palette(
        val face: Int,
        val edge: Int,
        val text: Int,
        val hot: Int,
        val warm: Int,
    )

    override fun onDraw(canvas: Canvas) {
        val dark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val back = if (dark) Color.rgb(18, 18, 20) else Color.rgb(246, 246, 248)
        val paint = Palette(
            face = if (dark) Color.rgb(38, 40, 46) else Color.WHITE,
            edge = if (dark) Color.rgb(70, 74, 84) else Color.rgb(206, 210, 218),
            text = if (dark) Color.rgb(232, 234, 238) else Color.rgb(24, 26, 30),
            hot = Color.rgb(64, 132, 240),
            warm = if (dark) Color.rgb(30, 58, 104) else Color.rgb(206, 226, 255),
        )
        canvas.drawColor(back)

        ink.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, config.labelSp, resources.displayMetrics
        )
        ink.textAlign = Paint.Align.CENTER

        ink.style = Paint.Style.STROKE
        ink.strokeWidth = 1f
        ink.color = paint.edge
        canvas.drawLine(width * config.fieldSplit, 0f, width * config.fieldSplit, height.toFloat(), ink)

        for (bank in Bank.values()) {
            val thumb = chorder.thumb(bank)
            if (config.cloud) drawCloud(canvas, bank, paint)
            when {
                modifying == bank -> {
                    drawItems(canvas, thumb, modifiers, Radius.NEAR, paint)
                    drawItems(canvas, thumb, specials, Radius.FAR, paint)
                }
                // The layout the next touch will be read against, whichever that is.
                !thumb.down -> drawEntry(canvas, thumb, paint)
                else -> {
                    drawRing(canvas, thumb, thumb.markX, thumb.markY, 1f, 1f, paint)
                    if (config.mirror) drawCopy(canvas, thumb, paint)
                }
            }
            drawTaken(canvas, thumb, paint)
        }
        if (config.touchReadout) drawReadout(canvas, paint)
        if (meta != 0) {
            ink.style = Paint.Style.FILL
            ink.color = paint.hot
            canvas.drawText(
                modifiers.filter { meta and it.second != 0 }.joinToString(" ") { it.first },
                width * 0.5f,
                ink.textSize * 1.4f,
                ink
            )
        }
    }

    /**
     * A cell is the set of points nearer this key's centre than any other, so the
     * drawn polygon is the target and every part of the field belongs to some key.
     */
    private fun drawEntry(canvas: Canvas, thumb: Thumb, paint: Palette) {
        val live = ring.live(thumb.bank, thumb.after)
        for (cell in ring.entry(thumb.bank, thumb.field)) {
            val on = cell.key in live
            if (!on && !config.showDeadSlots) continue
            val dim = if (on) 1f else 0.45f
            // A key steno order has passed owns no ground, so it gets no shape.
            ring.cellOf(cell.key, thumb.bank, thumb.field, thumb.after)?.let { polygon ->
                val path = shrink(polygon, cell)
                ink.style = Paint.Style.FILL
                ink.color = fade(paint.face, dim)
                canvas.drawPath(path, ink)
                ink.style = Paint.Style.STROKE
                ink.color = fade(paint.edge, dim)
                canvas.drawPath(path, ink)
            }
            if (!config.showLabels) continue
            ink.style = Paint.Style.FILL
            ink.color = fade(paint.text, dim)
            canvas.drawText(order[cell.key].toString(), cell.x, cell.y + ink.textSize * 0.35f, ink)
        }
    }

    /** The cell, pulled in a little toward its own centre, so two cells read as two. */
    private fun shrink(polygon: List<Point>, cell: Cell): Path {
        val path = Path()
        val inset = 0.97f
        polygon.forEachIndexed { i, p ->
            val x = cell.x + (p.x - cell.x) * inset
            val y = cell.y + (p.y - cell.y) * inset
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    /**
     * One thumb's ring, as the directions each key owns rather than as circles.
     *
     * `scale` is what makes the copy a copy: the same sectors at whatever size
     * there is room for.
     */
    private fun drawRing(
        canvas: Canvas,
        thumb: Thumb,
        cx: Float,
        cy: Float,
        scale: Float,
        dim: Float,
        paint: Palette,
    ) {
        val hover = chorder.hover(thumb.bank)
        val waiting = chorder.waiting(thumb.bank)
        // Nothing is chosen inside this, which is why a short slide costs nothing.
        ink.style = Paint.Style.STROKE
        ink.color = fade(paint.edge, dim * 0.6f)
        canvas.drawCircle(cx, cy, config.minSlide * unit * scale, ink)

        for (sector in ring.sectors(thumb.bank, thumb.after)) {
            val band = config.bounds(sector.radius)
            val path = wedge(
                cx, cy, band.start * unit * scale, band.endInclusive * unit * scale,
                sector.from, sector.to - sector.from
            )
            ink.style = Paint.Style.FILL
            ink.color = fade(if (sector.key == hover) paint.warm else paint.face, dim)
            canvas.drawPath(path, ink)
            ink.style = Paint.Style.STROKE
            ink.color = fade(if (sector.key == hover) paint.hot else paint.edge, dim)
            canvas.drawPath(path, ink)
            if (!config.showLabels) continue
            val mid = Math.toRadians(((sector.from + sector.to) * 0.5f).toDouble())
            val at = reach(sector.radius) * scale
            ink.style = Paint.Style.FILL
            // The key a wait is waiting on, so the pause reads as a pause.
            ink.color = fade(if (sector.key == waiting) paint.hot else paint.text, dim)
            canvas.drawText(
                order[sector.key].toString(),
                cx + (cos(mid) * at).toFloat(),
                cy + (sin(mid) * at).toFloat() + ink.textSize * 0.35f,
                ink
            )
        }
        drawThumb(canvas, thumb, cx, cy, scale, dim, paint)
    }

    /**
     * Where the last key was taken, where the thumb is now, and the slide between.
     *
     * Drawn per copy of the ring, at that copy's centre and scale, so the copy shows
     * the slide as it happens and not just the keys it could reach.
     */
    private fun drawThumb(
        canvas: Canvas,
        thumb: Thumb,
        cx: Float,
        cy: Float,
        scale: Float,
        dim: Float,
        paint: Palette,
    ) {
        val dot = config.markRadius * unit * scale
        val x = cx + (thumb.atX - thumb.markX) * scale
        val y = cy + (thumb.atY - thumb.markY) * scale
        ink.style = Paint.Style.STROKE
        ink.strokeWidth = 2f
        ink.color = fade(paint.hot, dim)
        canvas.drawLine(cx, cy, x, y, ink)
        ink.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, dot * 0.6f, ink)
        // The fingertip, live, so a slide that has not been read yet still shows.
        ink.style = Paint.Style.STROKE
        ink.strokeWidth = 3f
        canvas.drawCircle(x, y, dot, ink)
        ink.strokeWidth = 1f
    }

    /**
     * The same ring again, in the clear.
     *
     * Fixed in the field, not carried above the thumb: a copy that follows a thumb
     * which has slid to the edge goes off the screen with it.
     */
    private fun drawCopy(canvas: Canvas, thumb: Thumb, paint: Palette) {
        val room = config.farEdge * unit * config.mirrorScale + ink.textSize * 1.6f
        val cx = thumb.field.centerX.coerceIn(
            min(room, width * 0.5f), width - min(room, width * 0.5f)
        )
        val cy = (thumb.field.top + thumb.field.height * config.mirrorAt)
            .coerceIn(min(room, height * 0.5f), height - min(room, height * 0.5f))
        drawRing(canvas, thumb, cx, cy, config.mirrorScale, config.mirrorDim, paint)
    }

    /** An annular sector: the part of one ring a key owns. */
    private fun wedge(cx: Float, cy: Float, inner: Float, outer: Float, from: Float, sweep: Float): Path {
        val path = Path()
        if (sweep >= 359.5f) {
            path.addCircle(cx, cy, outer, Path.Direction.CW)
            path.addCircle(cx, cy, inner, Path.Direction.CCW)
            return path
        }
        // A hair off each edge, so two sectors read as two.
        val gap = if (sweep > 6f) 1.5f else 0f
        val start = from + gap * 0.5f
        val span = sweep - gap
        path.arcTo(RectF(cx - outer, cy - outer, cx + outer, cy + outer), start, span, true)
        path.arcTo(RectF(cx - inner, cy - inner, cx + inner, cy + inner), start + span, -span, false)
        path.close()
        return path
    }

    /** The keys this thumb holds, at the top of its field where nothing covers them. */
    private fun drawTaken(canvas: Canvas, thumb: Thumb, paint: Palette) {
        if (thumb.keys.isEmpty()) return
        ink.style = Paint.Style.FILL
        ink.color = paint.hot
        canvas.drawText(
            thumb.keys.map { order[it] }.joinToString(""),
            thumb.field.centerX,
            thumb.field.top + ink.textSize,
            ink
        )
    }

    private fun drawCloud(canvas: Canvas, bank: Bank, paint: Palette) {
        val cloud = clouds[bank] ?: return
        ink.style = Paint.Style.FILL
        ink.color = fade(paint.hot, config.cloudAlpha)
        for (touch in cloud) {
            // Panels report one, the other, or neither. Fall through to the dot size.
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
     * Many panels report a constant pressure and a real contact size, so whether a
     * firm press can be told from a light one is a question these numbers answer.
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
                field.centerX,
                ink.textSize,
                ink
            )
        }
    }

    private fun drawItems(
        canvas: Canvas,
        thumb: Thumb,
        items: List<Pair<String, Int>>,
        band: Radius,
        paint: Palette,
    ) {
        val distance = reach(band)
        val radius = config.markRadius * unit * 2f
        val sticky = band == Radius.NEAR
        val step = 360f / items.size
        val start = config.startAngle(thumb.bank, band)
        for (i in items.indices) {
            val radians = Math.toRadians((start + i * step).toDouble())
            val x = thumb.markX + (cos(radians) * distance).toFloat()
            val y = thumb.markY + (sin(radians) * distance).toFloat()
            val on = sticky && (meta and items[i].second) != 0
            ink.style = Paint.Style.FILL
            ink.color = if (on) paint.hot else paint.face
            canvas.drawCircle(x, y, radius, ink)
            ink.style = Paint.Style.STROKE
            ink.color = paint.edge
            canvas.drawCircle(x, y, radius, ink)
            ink.style = Paint.Style.FILL
            ink.color = if (on) Color.WHITE else paint.text
            canvas.drawText(items[i].first, x, y + ink.textSize * 0.35f, ink)
        }
    }
}
