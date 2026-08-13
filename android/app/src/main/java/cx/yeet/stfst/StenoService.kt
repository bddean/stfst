package cx.yeet.stfst

import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.InputMethodService.Insets
import android.os.SystemClock
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The keyboard. It owns the editor and the native handle, and nothing else.
 *
 * The runtime returns the whole buffer after every stroke, so what reaches the
 * editor is the difference between that and what was last sent. A longer match
 * that supersedes three strokes therefore needs no special case here: the buffer
 * simply comes back shorter, and the difference is a deletion.
 */
class StenoService : InputMethodService() {

    private var handle = 0L
    private var sent = ""
    private var order = ""
    /** Why the runtime did not start, for the bar to show instead of dying. */
    private var trouble = ""
    private lateinit var config: Config
    private var view: CombView? = null
    /** The hints and the bar, which have to stay touchable over the app. */
    private var controls: View? = null
    private var status: TextView? = null
    /** The field's own action key, renamed for whatever field is in front of us. */
    private var actionKey: Button? = null
    /** The running repeat of a held button, if any. */
    private var repeat: Runnable? = null

    /** The hints bar: recent briefer outlines for what was just written. */
    private var hintsBar: TextView? = null
    private val recentHints = ArrayDeque<String>()
    private var hintsClear: Runnable? = null
    /**
     * The reverse lookups run here, off the stroke's path to the editor.
     * Every native call holds [runtime], because the handle is one mutable
     * board and a lookup racing a stroke would read it mid-write.
     */
    private val hinter: ExecutorService = Executors.newSingleThreadExecutor()
    private val runtime = Any()

    override fun onCreate() {
        super.onCreate()
        config = Prefs.load(getSharedPreferences(PREFS, MODE_PRIVATE))
        // Unpacking is 22 MB of copying and the library may not load at all, and
        // an input method that throws in `onCreate` takes the text field with it.
        // A keyboard that cannot start is still better than no keyboard.
        try {
            val root = Assets.unpack(this)
            handle = Native.open(
                File(root, "dicts").absolutePath,
                File(root, "stenotype-extended.toml").absolutePath,
                File(root, "morph").absolutePath,
            )
            if (handle == 0L) trouble = Native.lastError()
            else order = Native.order(handle)
        } catch (e: Throwable) {
            trouble = "${e.javaClass.simpleName}: ${e.message}"
        }
        if (trouble.isNotEmpty()) Log.e(TAG, "the runtime did not start: $trouble")
    }

    override fun onDestroy() {
        hinter.shutdownNow()
        synchronized(runtime) {
            if (handle != 0L) Native.close(handle)
            handle = 0L
        }
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        config = Prefs.load(getSharedPreferences(PREFS, MODE_PRIVATE))
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val text = TextView(this).apply {
            setPadding(24, 12, 12, 12)
            textSize = 13f
            setTextColor(Color.GRAY)
            // One line, newest end visible: the bar must not grow into the app.
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.START
            text = if (handle == 0L) "not started: $trouble" else ""
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        status = text
        bar.addView(text)
        bar.addView(button("⚙") {
            startActivity(
                Intent(this@StenoService, SettingsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        })
        bar.addView(button("⌄") { requestHideSelf(0) })
        // Undoing a stroke and deleting a character are different things, and the
        // one button used to be labelled as the other.
        bar.addView(button("↶") {
            if (handle != 0L) {
                show(synchronized(runtime) { Native.undo(handle) })
                suggest()
            }
        })
        bar.addView(repeating(button("⌫", null)) { erase() })
        actionKey = button("⏎") { act() }
        bar.addView(actionKey)

        // Briefer outlines for what was just written, rolling, newest at the
        // right. Gone while it has nothing to say, so it costs no room.
        val hints = TextView(this).apply {
            setPadding(24, 6, 24, 6)
            textSize = 13f
            setTextColor(Color.GRAY)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.START
            visibility = View.GONE
        }
        hintsBar = hints

        val keys = CombView(this, config, order, ::stroke)
        view = keys
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            addView(hints)
            addView(bar)
        }
        controls = strip

        // With a full-height half per thumb the app keeps the whole screen, so
        // the strip has to float at the bottom of the ground between them
        // rather than sit under keys that reach the foot of the glass.
        if (keys.sides) {
            return FrameLayout(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
                addView(keys)
                addView(
                    strip,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                    )
                )
            }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(keys)
            addView(strip)
        }
    }

    /** A fresh field means a fresh buffer: the old text belongs to the old field. */
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        forget()
        actionKey?.text = actionName(info) ?: "⏎"
        view?.apply(Prefs.load(getSharedPreferences(PREFS, MODE_PRIVATE)))
    }

    /**
     * Let the app keep the whole screen while the two combs stand on its edges.
     *
     * A comb runs down, so in landscape each thumb takes a full-height side and
     * the app belongs to the ground between them. Android has no left or right
     * inset to hand out: the content area is described by a top edge alone. So
     * the app is told the keyboard costs it nothing, and the touchable region
     * is cut back to the two combs and the strip, which is what keeps a tap in
     * the middle reaching the app rather than a keyboard that draws nothing
     * there.
     */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val keys = view ?: return
        if (!keys.sides) return
        val root = keys.rootView
        outInsets.contentTopInsets = root.height
        outInsets.visibleTopInsets = root.height
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.setEmpty()
        val at = IntArray(2)
        keys.getLocationInWindow(at)
        for (rect in keys.touchable()) {
            outInsets.touchableRegion.union(
                Rect(rect.left + at[0], rect.top + at[1], rect.right + at[0], rect.bottom + at[1])
            )
        }
        controls?.let {
            it.getLocationInWindow(at)
            outInsets.touchableRegion.union(
                Rect(at[0], at[1], at[0] + it.width, at[1] + it.height)
            )
        }
    }

    private fun button(label: String, click: (() -> Unit)?) = Button(this).apply {
        text = label
        minWidth = 0
        minimumWidth = 0
        setPadding(26, 8, 26, 8)
        click?.let { what -> setOnClickListener { what() } }
    }

    /**
     * Hold the button and it keeps going.
     *
     * A backspace that fires once per tap is no way to clear a field. The first hit
     * lands on the press, so a tap is still one of whatever this does.
     */
    private fun repeating(button: Button, what: () -> Unit): Button {
        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    what()
                    val tick = object : Runnable {
                        override fun run() {
                            what()
                            view.postDelayed(this, 55L)
                        }
                    }
                    repeat = tick
                    view.postDelayed(tick, 320L)
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    repeat?.let { view.removeCallbacks(it) }
                    repeat = null
                }
            }
            true
        }
        return button
    }

    /**
     * What this field calls its action key, or null when it has none.
     *
     * The app may name it itself, and a browser usually does not, so the name comes
     * from the action it asked for.
     */
    private fun actionName(info: EditorInfo?): String? {
        info?.actionLabel?.let { if (it.isNotEmpty()) return it.toString() }
        return when (info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)) {
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_DONE -> "Done"
            EditorInfo.IME_ACTION_PREVIOUS -> "Back"
            else -> null
        }
    }

    /**
     * Ask the field to do its own thing: submit the form, run the search, move on.
     *
     * A chord writes text and has no way to ask for this, so a browser field was a
     * dead end. A field with no action of its own gets a plain Enter.
     */
    private fun act() {
        val editor = currentInputConnection ?: return
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        if (action == null || action == EditorInfo.IME_ACTION_UNSPECIFIED ||
            action == EditorInfo.IME_ACTION_NONE
        ) {
            key(KeyEvent.KEYCODE_ENTER, 0)
            return
        }
        editor.performEditorAction(action)
        forget()
    }

    /** One character, and by code point, so a pair is not cut in half. */
    private fun erase() {
        currentInputConnection?.deleteSurroundingTextInCodePoints(1, 0) ?: return
        forget()
    }

    /**
     * Give up the tracked buffer.
     *
     * What reaches the editor is the difference between the runtime's buffer and
     * what was last sent, so an edit made any other way leaves that difference
     * describing text nobody has. The next stroke then starts from the field as it
     * now is, which is what losing the context means.
     */
    private fun forget() {
        if (handle != 0L) synchronized(runtime) { Native.reset(handle) }
        sent = ""
    }

    private fun stroke(keys: Long) {
        if (handle == 0L) return
        // A bare star is the undo stroke, as on any steno machine.
        val star = order.indexOf('*')
        if (star >= 0 && keys == 1L shl star) {
            show(synchronized(runtime) { Native.undo(handle) })
        } else {
            show(synchronized(runtime) { Native.push(handle, keys) })
        }
        suggest()
    }

    /**
     * Ask the runtime for briefer outlines for what was just written, and fold
     * them into the hints bar: the trailing word and the trailing phrases both,
     * so a two-word brief is offered even when each word was stroked alone.
     *
     * Off the stroke's thread, because the reverse lookups walk every
     * dictionary backwards and the editor should not wait on them.
     */
    private fun suggest() {
        if (hinter.isShutdown) return
        hinter.execute {
            val found = synchronized(runtime) {
                if (handle == 0L) "" else Native.hints(handle)
            }
            hintsBar?.post { absorb(found) }
        }
    }

    /** Newest at the right, no repeats, at most [HINTS] entries. */
    private fun absorb(found: String) {
        for (entry in found.lines()) {
            if (entry.isEmpty() || entry in recentHints) continue
            recentHints.addLast(entry)
            while (recentHints.size > HINTS) recentHints.removeFirst()
        }
        val bar = hintsBar ?: return
        bar.text = recentHints.joinToString("   ")
        bar.visibility = if (recentHints.isEmpty()) View.GONE else View.VISIBLE
        // The buffer clears after a quiet minute: a hint is about what was
        // just written, and an hour-old one no longer is.
        hintsClear?.let { bar.removeCallbacks(it) }
        val clear = Runnable {
            recentHints.clear()
            bar.text = ""
            bar.visibility = View.GONE
        }
        hintsClear = clear
        bar.postDelayed(clear, HINTS_IDLE_MS)
    }

    /**
     * Send the editor the difference between the buffer and what it already has.
     */
    private fun show(text: String) {
        status?.text = text.takeLast(60)
        val editor = currentInputConnection ?: return
        var common = 0
        while (common < text.length && common < sent.length && text[common] == sent[common]) common++
        val stale = sent.length - common
        if (stale > 0) editor.deleteSurroundingText(stale, 0)
        if (common < text.length) editor.commitText(text.substring(common), 1)
        sent = text
    }

    /** A key a long press asked for, with whatever modifiers were sticky. */
    private fun key(code: Int, meta: Int) {
        val editor = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            editor.sendKeyEvent(KeyEvent(now, now, action, code, 0, meta))
        }
        // The editor may have moved or deleted text of its own, so the buffer this
        // keyboard is tracking is no longer what is on screen.
        if (code == KeyEvent.KEYCODE_DEL || code == KeyEvent.KEYCODE_ENTER) forget()
    }

    companion object {
        const val TAG = "stfst"
        const val PREFS = "radial"
        /** How many hints the bar holds, and how long a quiet spell keeps them. */
        const val HINTS = 8
        const val HINTS_IDLE_MS = 60_000L
    }
}
