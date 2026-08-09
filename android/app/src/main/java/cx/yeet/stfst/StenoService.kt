package cx.yeet.stfst

import android.content.Intent
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

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
    private var view: RadialView? = null
    private var status: TextView? = null

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
        if (handle != 0L) Native.close(handle)
        handle = 0L
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        config = Prefs.load(getSharedPreferences(PREFS, MODE_PRIVATE))
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val text = TextView(this).apply {
            setPadding(24, 12, 12, 12)
            textSize = 13f
            setTextColor(Color.GRAY)
            text = if (handle == 0L) "not started: $trouble" else ""
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        status = text
        val undo = Button(this).apply {
            setText("⌫")
            setOnClickListener { if (handle != 0L) show(Native.undo(handle)) }
        }
        val settings = Button(this).apply {
            setText("⚙")
            setOnClickListener {
                startActivity(
                    Intent(this@StenoService, SettingsActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        bar.addView(text)
        bar.addView(undo)
        bar.addView(settings)

        val radial = RadialView(this, config, order, ::stroke, ::key)
        view = radial
        column.addView(bar)
        column.addView(radial)
        return column
    }

    /** A fresh field means a fresh buffer: the old text belongs to the old field. */
    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (handle != 0L) Native.reset(handle)
        sent = ""
        view?.apply(Prefs.load(getSharedPreferences(PREFS, MODE_PRIVATE)))
    }

    private fun stroke(keys: Long) {
        if (handle == 0L) return
        show(Native.push(handle, keys))
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
        if (code == KeyEvent.KEYCODE_DEL || code == KeyEvent.KEYCODE_ENTER) {
            if (handle != 0L) Native.reset(handle)
            sent = ""
        }
    }

    companion object {
        const val TAG = "stfst"
        const val PREFS = "radial"
    }
}
