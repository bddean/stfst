package cx.yeet.stfst

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Rect
import android.inputmethodservice.InputMethodService
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
    private var status: TextView? = null
    /** The field's own action key, renamed for whatever field is in front of us. */
    private var actionKey: Button? = null
    /** The running repeat of a held button, if any. */
    private var repeat: Runnable? = null

    /** What the keyboard has just done: the strokes, and briefer ways to make them. */
    private var notesBar: TextView? = null
    private val notes = Notes()
    private var notesClear: Runnable? = null

    /**
     * The reverse lookups and the peeks run here, off the stroke's path to the
     * editor. Every native call holds [runtime], because the handle is one
     * mutable board and a second reader racing a stroke would read it mid-write.
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
                notes.stroke("↶", "")
                render()
                suggest()
            }
        })
        bar.addView(repeating(button("⌫", null)) { erase() })
        actionKey = button("⏎") { act() }
        bar.addView(actionKey)

        val keys = CombView(this, config, order, ::stroke, ::chord)
        view = keys
        // One line, whatever room there is. The briefs are what a line is worth,
        // and anything taller is the app's room rather than this keyboard's.
        val panel = TextView(this).apply {
            setPadding(24, 6, 24, 6)
            textSize = 13f
            setTextColor(Color.GRAY)
            // Outlines read as a column when they line up, and only a fixed
            // pitch lines them up.
            typeface = Typeface.MONOSPACE
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.START
            visibility = View.GONE
        }
        notesBar = panel

        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            addView(panel)
            addView(bar)
        }

        if (keys.sides) {
            val edge = (resources.displayMetrics.widthPixels *
                config.splitLandscape.coerceIn(0.15f, 0.5f)).toInt()
            return FrameLayout(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                addView(keys)
                addView(
                    strip,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.BOTTOM,
                    ).apply {
                        leftMargin = edge
                        rightMargin = edge
                    }
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

    /**
     * What the app lays out around, and what our window takes touches for.
     *
     * The keys are both. The band the preview draws above them is neither: it
     * is over the app on purpose, so the app keeps its room and its touches up
     * there and the copy of the comb floats on top.
     */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val keys = view ?: return
        if (keys.keysTop <= 0) return
        val at = IntArray(2)
        keys.getLocationInWindow(at)
        val edge = at[1] + keys.keysTop
        outInsets.contentTopInsets = edge
        outInsets.visibleTopInsets = edge
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        val room = Rect()
        keys.rootView.getWindowVisibleDisplayFrame(room)
        outInsets.touchableRegion.set(0, edge, room.width(), room.height())
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        forget()
        notes.clear()
        render()
        actionKey?.text = actionName(info) ?: "⏎"
        config = Prefs.load(getSharedPreferences(PREFS, MODE_PRIVATE))
        view?.apply(config)
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

    /**
     * What this stroke would write, without writing it.
     *
     * The runtime is one mutable board, so the only way to ask is to make the
     * stroke and take it back. Nothing reaches the editor and the buffer ends
     * where it started. A stroke that means undo is answered by name instead,
     * because asking what an undo writes would perform one.
     */
    private fun peek(keys: Long): String {
        if (handle == 0L || keys == 0L) return ""
        val star = order.indexOf('*')
        if (star >= 0 && keys == 1L shl star) return "↶"
        return synchronized(runtime) {
            val was = Native.text(handle)
            val now = Native.push(handle, keys)
            Native.undo(handle)
            var common = 0
            while (common < now.length && common < was.length && now[common] == was[common]) common++
            // A longer match takes back what the strokes it covers wrote, so
            // say that rather than showing only what is left.
            (if (common < was.length) "⌫" else "") + now.substring(common)
        }
    }

    /**
     * The chord in hand changed, so answer with what it would write.
     *
     * Off the drawing's thread: making the stroke to find out is a dictionary
     * lookup, and a frame should not wait on one.
     */
    private fun chord(keys: Long) {
        if (hinter.isShutdown) return
        hinter.execute {
            val text = peek(keys)
            view?.post { view?.writes(keys, text) }
        }
    }

    private fun stroke(keys: Long) {
        if (handle == 0L) return
        // A bare star is the undo stroke, as on any steno machine.
        val star = order.indexOf('*')
        val back = star >= 0 && keys == 1L shl star
        val text = synchronized(runtime) {
            if (back) Native.undo(handle) else Native.push(handle, keys)
        }
        notes.stroke(outline(keys), show(text))
        render()
        suggest()
    }

    /** A stroke's keys as the outline it is written as, in steno order. */
    private fun outline(keys: Long): String =
        order.filterIndexed { i, _ -> (keys shr i) and 1L == 1L }

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
            notesBar?.post {
                if (notes.brief(found.lines())) render()
            }
        }
    }

    /**
     * Put the briefs on the screen.
     *
     * The line is worth the briefs alone: a stroke you have just made is not
     * news, and a shorter way to make it is.
     */
    private fun render() {
        val bar = notesBar ?: return
        bar.text = notes.briefs(BRIEFS)
        bar.visibility = if (bar.text.isEmpty()) View.GONE else View.VISIBLE
        // The record clears after a quiet spell: it is about what was just
        // written, and an hour-old line no longer is.
        notesClear?.let { bar.removeCallbacks(it) }
        val clear = Runnable {
            notes.clear()
            bar.text = ""
            bar.visibility = View.GONE
        }
        notesClear = clear
        bar.postDelayed(clear, NOTES_IDLE_MS)
    }

    /**
     * Send the editor the difference between the buffer and what it already
     * has, and answer with the text that appeared.
     */
    private fun show(text: String): String {
        status?.text = text.takeLast(60)
        var common = 0
        while (common < text.length && common < sent.length && text[common] == sent[common]) common++
        val added = text.substring(common)
        val editor = currentInputConnection ?: return added
        val stale = sent.length - common
        if (stale > 0) editor.deleteSurroundingText(stale, 0)
        if (added.isNotEmpty()) editor.commitText(added, 1)
        sent = text
        return added
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
        /** How many briefs one line holds, and how long a quiet spell keeps them. */
        const val BRIEFS = 8
        const val NOTES_IDLE_MS = 60_000L
    }
}
