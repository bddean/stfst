package cx.yeet.stfst

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config as Rob
import java.io.File

/**
 * Renders the keyboard to a PNG, so the drawing can be looked at without a
 * device. It asserts nothing; `app/build/preview-*.png` is the output.
 */
@RunWith(RobolectricTestRunner::class)
@Rob(sdk = [33], qualifiers = "w411dp-h915dp")
class PreviewTest {

    /** A phone-shaped bank: the keys take what the screen allows. */
    private val shot = Config(keyboardHeight = 0.95f)

    private val order = "#@^+STKPWHRAO*EUfrpblgtsdz"
    private val tall = Shape(1080, 900, false)
    private val wide = Shape(1800, 800, true)

    private class Shape(val width: Int, val height: Int, val landscape: Boolean)

    @Test
    fun shots() {
        shoot("idle", tall, shot) {}
        shoot("landscape", wide, shot) {}
        shoot("band", tall, shot.copy(previewHeight = 0.25f)) { view ->
            val h = at(view, tall, 'H', 1)
            send(view, MotionEvent.ACTION_DOWN, h.x, h.y)
        }
        shoot("upgrade", tall, shot) { view ->
            val at = ring().middle(Bank.RIGHT, bank(view, tall, Bank.RIGHT), Cell(Cell.ROW, 2))
            send(view, MotionEvent.ACTION_DOWN, at.x, at.y)
            send(view, MotionEvent.ACTION_UP, at.x, at.y)
        }
        shoot("armed", tall, shot) { view ->
            val p = at(view, tall, '#', Cell.ROW)
            send(view, MotionEvent.ACTION_DOWN, p.x, p.y)
            send(view, MotionEvent.ACTION_UP, p.x, p.y)
        }
        shoot("chord", tall, shot) { view ->
            val h = at(view, tall, 'H', 1)
            val a = at(view, tall, 'A', 2)
            send(view, MotionEvent.ACTION_DOWN, h.x, h.y)
            send(view, MotionEvent.ACTION_MOVE, a.x, a.y)
        }
    }

    private fun ring() = Ring(order, shot)

    private fun bank(view: RingView, shape: Shape, side: Bank): Field {
        val edge = shape.width * 0.5f
        return if (side == Bank.LEFT) Field(0f, view.keysTop.toFloat(), edge, shape.height.toFloat())
        else Field(edge, view.keysTop.toFloat(), shape.width.toFloat(), shape.height.toFloat())
    }

    /** Where a cell of the left bank is on the glass. */
    private fun at(view: RingView, shape: Shape, key: Char, level: Int): Point {
        val ring = ring()
        val cell = ring.cells(Bank.LEFT).first {
            it.level == level && ring.keys(Bank.LEFT, it).contains(order.indexOf(key))
        }
        return ring.middle(Bank.LEFT, bank(view, shape, Bank.LEFT), cell)
    }

    private fun send(view: RingView, action: Int, x: Float, y: Float) {
        val now = android.os.SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x, y, 0)
        view.onTouchEvent(event)
        event.recycle()
    }

    private fun shoot(name: String, shape: Shape, config: Config, act: (RingView) -> Unit) {
        val ctx = RuntimeEnvironment.getApplication()
        ctx.resources.configuration.orientation =
            if (shape.landscape) Configuration.ORIENTATION_LANDSCAPE
            else Configuration.ORIENTATION_PORTRAIT
        val view = RingView(ctx, config, order, {}, {})
        view.measure(
            View.MeasureSpec.makeMeasureSpec(shape.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(shape.height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, shape.width, shape.height)
        act(view)
        val bmp = Bitmap.createBitmap(shape.width, shape.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        File("build/preview-$name.png").outputStream()
            .use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
