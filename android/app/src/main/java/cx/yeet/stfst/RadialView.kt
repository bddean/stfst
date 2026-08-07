package cx.yeet.stfst

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The keyboard itself: two thumb fields, a ring around each thumb, and the touch
 * handling that turns slides into strokes.
 *
 * Drawing reads the chorder and decides nothing. Where a key sits comes from
 * `Ring`, so a setting changed in the panel moves the key and the drawing follows
 * without knowing why.
 */
@SuppressLint("ViewConstructor")
class RadialView(
    context: Context,
    private var config: Config,
    order: String,
    private val onStroke: (Long) -> Unit,
    private val onKey: (Int, Int) -> Unit,
) : View(context) {

    private var ring = Ring(order, config)
    private var chorder = Chorder(ring, config) { onStroke(it) }
    private var order = order

    /** Sticky modifiers a long press has turned on, as a `KeyEvent` meta mask. */
    private var meta = 0
    /** The thumb whose long press is open, if any. */
    private var modifying: Bank? = null

    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stalls = mutableMapOf<Bank, Runnable>()
    private val holds = mutableMapOf<Int, Runnable>()

    private var unit = 1f

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
     * Each field's home point, which is where a fan is measured from and where a
     * stalled pivot returns to. Low in the field, because that is where a thumb
     * reaches from.
     */
    private fun layoutFields() {
        val edge = width * config.fieldSplit
        unit = min(min(edge, width - edge), height.toFloat())
        chorder.place(Bank.LEFT, edge * 0.5f, height * 0.62f)
        chorder.place(Bank.RIGHT, edge + (width - edge) * 0.5f, height * 0.62f)
    }

    private fun bankAt(x: Float) = if (x < width * config.fieldSplit) Bank.LEFT else Bank.RIGHT

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val at = event.actionIndex
                val pointer = event.getPointerId(at)
                val bank = bankAt(event.getX(at))
                if (chorder.down(bank, pointer, event.getX(at), event.getY(at), unit)) buzz()
                armStall(bank)
                armHold(bank, pointer)
            }

            MotionEvent.ACTION_MOVE -> {
                for (at in 0 until event.pointerCount) {
                    val pointer = event.getPointerId(at)
                    val thumb = chorder.forPointer(pointer) ?: continue
                    // Any real movement means the press is a slide, not a hold.
                    holds.remove(pointer)?.let { removeCallbacks(it) }
                    val bank = modifying
                    if (bank == thumb.bank) {
                        pickModifier(thumb, event.getX(at), event.getY(at))
                        continue
                    }
                    if (chorder.move(pointer, event.getX(at), event.getY(at), unit)) buzz()
                    armStall(thumb.bank)
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
                    buzz()
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

    /** A pivot that has drifted goes home when the thumb holds still. */
    private fun armStall(bank: Bank) {
        stalls.remove(bank)?.let { removeCallbacks(it) }
        val task = Runnable {
            chorder.stall(bank)
            invalidate()
        }
        stalls[bank] = task
        postDelayed(task, config.stallMs)
    }

    private fun armHold(bank: Bank, pointer: Int) {
        if (!config.longPress) return
        val task = Runnable {
            modifying = bank
            chorder.thumb(bank).keys.clear()
            buzz()
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
        val distance = kotlin.math.hypot(dx, dy)
        if (distance < config.minSlide * unit) return
        val heading = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val near = distance < config.farRadius * unit
        val items: List<Pair<String, Int>> = if (near) modifiers else specials
        val step = 360f / items.size
        val start = if (near) config.startNearLeft else config.startFarLeft
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
        buzz()
        if (near) {
            meta = meta xor items[best].second
        } else {
            onKey(items[best].second, meta)
            meta = 0
            modifying = null
        }
    }

    private fun buzz() {
        if (config.haptics) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    override fun onDraw(canvas: Canvas) {
        val dark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val back = if (dark) Color.rgb(18, 18, 20) else Color.rgb(246, 246, 248)
        val face = if (dark) Color.rgb(38, 40, 46) else Color.WHITE
        val edge = if (dark) Color.rgb(70, 74, 84) else Color.rgb(206, 210, 218)
        val text = if (dark) Color.rgb(232, 234, 238) else Color.rgb(24, 26, 30)
        val hot = Color.rgb(64, 132, 240)
        canvas.drawColor(back)

        ink.style = Paint.Style.STROKE
        ink.strokeWidth = 1f
        ink.color = edge
        canvas.drawLine(width * config.fieldSplit, 0f, width * config.fieldSplit, height.toFloat(), ink)

        val radius = config.keyRadius * unit
        ink.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, config.labelSp, resources.displayMetrics
        )
        ink.textAlign = Paint.Align.CENTER

        for (bank in Bank.values()) {
            val thumb = chorder.thumb(bank)
            if (modifying == bank) {
                drawItems(canvas, thumb, modifiers, config.farRadius * unit * 0.62f, radius, face, edge, text, hot, true)
                drawItems(canvas, thumb, specials, config.farRadius * unit * 1.35f, radius, face, edge, text, hot, false)
                continue
            }
            val live = ring.live(bank, thumb.after)
            val dead = if (config.showDeadSlots) ring.keys(bank).filter { it <= thumb.after } else emptyList()
            for (key in dead) drawKey(canvas, thumb, key, radius, face, edge, edge, false)
            for (key in live) drawKey(canvas, thumb, key, radius, face, edge, text, false)
            // The pivot, and what this thumb has taken so far.
            ink.style = Paint.Style.FILL
            ink.color = if (thumb.down) hot else edge
            canvas.drawCircle(thumb.markX, thumb.markY, radius * 0.28f, ink)
            if (thumb.keys.isNotEmpty()) {
                ink.color = text
                canvas.drawText(
                    thumb.keys.map { order[it] }.joinToString(""),
                    thumb.markX,
                    thumb.markY - config.farRadius * unit - radius * 1.6f,
                    ink
                )
            }
        }
        if (meta != 0) {
            ink.color = hot
            canvas.drawText(
                modifiers.filter { meta and it.second != 0 }.joinToString(" ") { it.first },
                width * 0.5f,
                ink.textSize * 1.4f,
                ink
            )
        }
    }

    private fun drawKey(
        canvas: Canvas,
        thumb: Thumb,
        key: Int,
        radius: Float,
        face: Int,
        edge: Int,
        label: Int,
        highlight: Boolean,
    ) {
        val slot = ring.slotOf(key) ?: return
        val distance = if (slot.radius == Radius.FAR) config.farRadius * unit * 1.35f
        else config.farRadius * unit * 0.62f
        val radians = Math.toRadians(slot.angle.toDouble())
        val x = thumb.markX + (cos(radians) * distance).toFloat()
        val y = thumb.markY + (sin(radians) * distance).toFloat()
        ink.style = Paint.Style.FILL
        ink.color = if (highlight) label else face
        canvas.drawCircle(x, y, radius, ink)
        ink.style = Paint.Style.STROKE
        ink.color = edge
        canvas.drawCircle(x, y, radius, ink)
        if (!config.showLabels) return
        ink.style = Paint.Style.FILL
        ink.color = label
        canvas.drawText(order[key].toString(), x, y + ink.textSize * 0.35f, ink)
    }

    private fun drawItems(
        canvas: Canvas,
        thumb: Thumb,
        items: List<Pair<String, Int>>,
        distance: Float,
        radius: Float,
        face: Int,
        edge: Int,
        label: Int,
        hot: Int,
        sticky: Boolean,
    ) {
        val step = 360f / items.size
        val start = if (sticky) config.startNearLeft else config.startFarLeft
        for (i in items.indices) {
            val radians = Math.toRadians((start + i * step).toDouble())
            val x = thumb.markX + (cos(radians) * distance).toFloat()
            val y = thumb.markY + (sin(radians) * distance).toFloat()
            val on = sticky && (meta and items[i].second) != 0
            ink.style = Paint.Style.FILL
            ink.color = if (on) hot else face
            canvas.drawCircle(x, y, radius, ink)
            ink.style = Paint.Style.STROKE
            ink.color = edge
            canvas.drawCircle(x, y, radius, ink)
            ink.style = Paint.Style.FILL
            ink.color = if (on) Color.WHITE else label
            canvas.drawText(items[i].first, x, y + ink.textSize * 0.35f, ink)
        }
    }
}
