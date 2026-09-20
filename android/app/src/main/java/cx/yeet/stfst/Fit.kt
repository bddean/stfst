package cx.yeet.stfst

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.TypedValue
import android.view.View
import kotlin.math.min

/**
 * Both screens at a glance: how much of one the keyboard takes, and where the
 * banks sit in what it took.
 *
 * It stands in for the live keyboard where there is no room for one, so it is
 * small, and it says only what the keyboard cannot: the room left to the app,
 * what an uneven split does to the far bank, and landscape, which the preview
 * never shows. The shapes come from the same functions the keyboard measures
 * itself with.
 */
@SuppressLint("ViewConstructor")
class Fit(
    context: Context,
    private var config: Config,
    private val order: String,
) : View(context) {

    private var ring = Ring(order, config)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)

    fun apply(fresh: Config) {
        config = fresh
        ring = Ring(order, fresh)
        invalidate()
    }

    private fun dp(many: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, many, resources.displayMetrics
    )

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), dp(60f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val tall = dp(48f)
        val gap = dp(12f)
        val screen = resources.displayMetrics
        val ratio = min(screen.widthPixels, screen.heightPixels).toFloat() /
            kotlin.math.max(screen.widthPixels, screen.heightPixels)
        val wide = tall / ratio
        val across = tall * ratio + gap + wide
        val scale = min(1f, (width - dp(16f)) / across)
        var left = (width - across * scale) * 0.5f
        for (landscape in listOf(false, true)) {
            val w = (if (landscape) wide else tall * ratio) * scale
            phone(canvas, left, dp(6f), w, tall * scale, landscape)
            left += w + gap * scale
        }
    }

    /** One phone: the app's room, the keys, and the band the keys draw over it. */
    private fun phone(
        canvas: Canvas, x: Float, y: Float, w: Float, h: Float, landscape: Boolean,
    ) {
        val (keys, band) = ring.deep(w, h, landscape)
        val floor = y + h
        val top = floor - keys

        ink.style = Paint.Style.FILL
        ink.color = Color.rgb(246, 246, 246)
        canvas.drawRect(x, y, x + w, floor, ink)
        ink.color = Color.WHITE
        canvas.drawRect(x, top, x + w, floor, ink)
        // The band takes no room off the app: it is drawn over it, so it is
        // drawn over it here as well.
        if (band > 0f) {
            ink.color = Color.argb(60, 24, 24, 24)
            canvas.drawRect(x, top - band, x + w, top, ink)
        }

        val (leftSpan, rightSpan) = ring.spans(w, landscape && config.landscapeSides)
        for (bank in Bank.values()) {
            val span = if (bank == Bank.LEFT) leftSpan else rightSpan
            val field = Field(x + span.start, top, x + span.endInclusive, floor)
            val box = ring.box(bank, field)
            val rings = ring.rings(bank, field)
            ink.style = Paint.Style.STROKE
            ink.strokeWidth = dp(1f)
            ink.color = Color.rgb(150, 150, 150)
            canvas.drawOval(rings.left, rings.top, rings.right, rings.bottom, ink)
            if (box.bottom > rings.bottom) {
                canvas.drawRect(box.left, rings.bottom, box.right, box.bottom, ink)
            }
        }

        ink.style = Paint.Style.STROKE
        ink.strokeWidth = dp(1f)
        ink.color = Color.rgb(90, 90, 90)
        canvas.drawRect(x, y, x + w, floor, ink)

        ink.style = Paint.Style.FILL
    }
}
