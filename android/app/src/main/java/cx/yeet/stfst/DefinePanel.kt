package cx.yeet.stfst

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Defining an outline, without leaving the keyboard.
 *
 * Two fields, and steno fills both: the outline field takes the chords as they
 * are written, and the translation field takes what they *write*, which is the
 * runtime's answer and not a second way of spelling one. Whichever field is lit
 * is where the next stroke goes.
 *
 * It decides nothing. The service owns the entry being made and hands the panel
 * the two strings after every change.
 */
class DefinePanel(context: Context) : LinearLayout(context) {

    enum class Field { OUTLINE, TRANSLATION }

    var onSave: () -> Unit = {}
    var onCancel: () -> Unit = {}
    /** One translation further back, or one less. */
    var onReach: (Int) -> Unit = {}
    var onFocus: (Field) -> Unit = {}

    var field = Field.TRANSLATION
        private set

    private val note = TextView(context)
    private val outline = row(context, Field.OUTLINE)
    private val translation = row(context, Field.TRANSLATION)
    private val save: Button
    private val less: Button
    private val more: Button

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#f2f2f2"))
        visibility = View.GONE

        note.apply {
            setPadding(24, 10, 24, 2)
            textSize = 12f
            setTextColor(Color.GRAY)
            setSingleLine(true)
        }
        addView(note)
        addView(outline)
        addView(translation)

        less = flat("−") { onReach(-1) }
        more = flat("+") { onReach(+1) }
        save = flat("Save") { onSave() }
        val cancel = flat("Cancel") { onCancel() }
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.END
            addView(less)
            addView(more)
            addView(TextView(context).apply {
                layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(cancel)
            addView(save)
        })
    }

    val open: Boolean
        get() = visibility == View.VISIBLE

    /**
     * `reach` is how many strokes back the outline came from, and zero is an
     * entry being written from nothing: then there is nothing to reach over and
     * nothing already written to correct.
     */
    fun open(field: Field, reach: Int) {
        this.field = field
        less.visibility = if (reach > 0) View.VISIBLE else View.GONE
        more.visibility = if (reach > 0) View.VISIBLE else View.GONE
        visibility = View.VISIBLE
    }

    fun close() {
        visibility = View.GONE
    }

    fun focus(field: Field) {
        this.field = field
        paint()
    }

    fun show(outline: String, translation: String, note: String) {
        this.outline.text = outline.ifEmpty { "…" }
        this.translation.text = translation.ifEmpty { "…" }
        this.note.text = note
        // An empty translation deletes the entry, so the button says so rather
        // than saving something that is not a saving.
        save.text = if (translation.isEmpty()) "Delete" else "Save"
        paint()
    }

    private fun paint() {
        for ((view, which) in listOf(outline to Field.OUTLINE, translation to Field.TRANSLATION)) {
            val lit = which == field
            view.setBackgroundColor(if (lit) Color.WHITE else Color.TRANSPARENT)
            view.setTextColor(if (lit) Color.BLACK else Color.DKGRAY)
        }
    }

    private fun row(context: Context, which: Field) = TextView(context).apply {
        setPadding(24, 12, 24, 12)
        textSize = 15f
        typeface = Typeface.MONOSPACE
        setSingleLine(true)
        setOnClickListener { onFocus(which) }
        layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun flat(label: String, click: () -> Unit) = Button(context).apply {
        text = label
        minWidth = 0
        minimumWidth = 0
        setPadding(26, 8, 26, 8)
        setOnClickListener { click() }
    }
}
