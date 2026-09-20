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

    private val order = "🦐#@^+STKPWHRAO*EUfrpblgtsdz"
    private val keys = Keys.of(order)
    private val tall = Shape(1080, 900, false)
    private val wide = Shape(1800, 800, true)

    private class Shape(val width: Int, val height: Int, val landscape: Boolean)

    /** The settings screen, group by group, at phone size. */
    @Test
    fun settings() {
        val activity = org.robolectric.Robolectric.buildActivity(SettingsActivity::class.java)
            .setup().get()
        val root = activity.window.decorView
        val w = 1080
        val h = 2000
        val tabs = tabs(root)
        // The last chip of the strip is the open group's prose, not a tab.
        for (tab in 0 until tabs.childCount - 1) {
            tabs.getChildAt(tab).performClick()
            root.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, w, h)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            root.draw(Canvas(bmp))
            val name = (tabs.getChildAt(tab) as android.widget.TextView).text
            File("build/preview-settings-$name.png").outputStream()
                .use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (name == "Fit") glance(root, w, h)
        }
    }

    /** The two screens at a glance, which stand in for the keyboard preview. */
    private fun glance(root: View, w: Int, h: Int) {
        val box = find(root) { it is android.widget.CheckBox } as android.widget.CheckBox
        box.isChecked = false
        root.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.WHITE)
        root.draw(Canvas(bmp))
        File("build/preview-settings-glance.png").outputStream()
            .use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun find(root: View, want: (View) -> Boolean): View {
        if (want(root)) return root
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) {
                runCatching { return find(root.getChildAt(i), want) }
            }
        }
        throw IllegalStateException("nothing matched")
    }

    /** The tab strip: the one horizontal row of chips on the screen. */
    private fun tabs(root: View): android.view.ViewGroup {
        if (root is android.widget.HorizontalScrollView) {
            return root.getChildAt(0) as android.view.ViewGroup
        }
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) {
                runCatching { return tabs(root.getChildAt(i)) }
            }
        }
        throw IllegalStateException("no tab strip")
    }

    @Test
    fun shots() {
        shoot("idle", tall, shot) {}
        shoot("landscape", wide, shot) {}
        // An uneven split: each bank keeps its own edge, and the slack is in
        // the middle rather than round the wider one.
        shoot("split", tall, shot.copy(fieldSplit = 0.38f)) {}
        shoot("band", tall, shot.copy(band = true, previewHeight = 0.25f)) { view ->
            val h = at(view, tall, 'H', 1)
            send(view, MotionEvent.ACTION_DOWN, h.x, h.y)
        }
        shoot("upgrade", tall, shot) { view ->
            val ring = ring()
            val field = bank(view, tall, Bank.RIGHT)
            val stud = ring.cells(Bank.RIGHT).first { ring.isUpgrade(Bank.RIGHT, it) }
            val at = ring.middle(Bank.RIGHT, field, stud)
            send(view, MotionEvent.ACTION_DOWN, at.x, at.y)
        }
        // The gestures are drawn where the thumb would make them, so the shot
        // that shows them is one with a thumb out on the rim.
        shoot("gestures", tall, shot) { view ->
            val ring = ring()
            val field = bank(view, tall, Bank.LEFT)
            val (inner, rim) = ring.edges(Bank.LEFT, ring.outer(Bank.LEFT))
            val at = ring.point(Bank.LEFT, field, Polar(inner + (rim - inner) * 0.2f, 150f))
            send(view, MotionEvent.ACTION_DOWN, at.x, at.y)
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
            it.level == level && ring.keys(Bank.LEFT, it).contains(keys.indexOf(key))
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
