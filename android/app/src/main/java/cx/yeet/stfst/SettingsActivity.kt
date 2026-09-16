package cx.yeet.stfst

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

/**
 * Every parameter, editable on the phone.
 *
 * The knob list is the whole screen: one entry per parameter, each saying how to
 * read it and how to write it. Adding a parameter to [Config] and a line here is
 * the whole cost of exposing it, and no layout file has to agree with either.
 */
class SettingsActivity : Activity() {

    private lateinit var config: Config

    /** The keyboard itself, pinned under the knobs, redrawn on every change. */
    private var preview: RingView? = null
    private var previewToggle: CheckBox? = null

    private fun refresh() {
        preview?.apply(config)
    }

    /**
     * Show or hide the preview. `editing` overrides the toggle while a text
     * field has focus, so the soft keyboard and the preview never fight for
     * the same room.
     */
    private fun previewRoom(editing: Boolean) {
        preview?.visibility =
            if (!editing && previewToggle?.isChecked != false) View.VISIBLE else View.GONE
    }

    /** A parameter, and the two halves of getting at it. */
    private sealed class Knob(val label: String, val note: String = "")

    private class Range(
        label: String,
        val min: Float,
        val max: Float,
        val get: (Config) -> Float,
        val set: (Config, Float) -> Unit,
        note: String = "",
        /** Printed after the value, because a bare 0.13 says nothing. */
        val unit: String = "",
    ) : Knob(label, note)

    private class Flag(
        label: String,
        val get: (Config) -> Boolean,
        val set: (Config, Boolean) -> Unit,
        note: String = "",
    ) : Knob(label, note)

    private class Text(
        label: String,
        val get: (Config) -> String,
        val set: (Config, String) -> Unit,
        note: String = "",
    ) : Knob(label, note)

    /** One of a short, closed set of values. */
    private class Section(label: String, note: String = "") : Knob(label, note)

    private val knobs: List<Knob> = listOf(
        Section("The rings",
            "Nested bands per thumb, read by where the thumb is. Entering a " +
                "live cell takes it, and everything else is ground: a cell the " +
                "order has passed, a cell holding nothing, and the hub once " +
                "you have left it. So sliding out to the next ring erases the " +
                "ring behind, turning through a ring adds keys, and two cells " +
                "far apart on one ring are joined by a straight line through " +
                "the middle.\n\n" +
                "A layout is the hub first, then the rings outward, separated " +
                "by spaces. A slot is one character, or several keys between " +
                "slashes, and a dot is ground. A band whose keys repeat is " +
                "turned half a slot, so it is staggered against the band " +
                "inside it and a run straight out from the hub lands on one " +
                "of its cells. A key in two slots is nothing special, because " +
                "a key is a set of cells.\n\n" +
                "The modifiers are not on the rings: steno order cannot kill " +
                "one, so a key no walk can spend is a key no walk should have " +
                "to reach. They stand in a row under the rings."),
        Text("Left rings", { it.ringLeft }, { c, v -> c.ringLeft = v },
            "hub, then the rings outward"),
        Text("Right rings", { it.ringRight }, { c, v -> c.ringRight = v }),
        Text("Left row", { it.modsLeft }, { c, v -> c.modsLeft = v },
            "the modifiers under the rings, one key to a slot. A slot of > is " +
                "the upgrade: while it is on, the stroke writes -D for -T and " +
                "-Z for -S"),
        Text("Right row", { it.modsRight }, { c, v -> c.modsRight = v }),
        Text("The upgrade", { it.upgrade }, { c, v -> c.upgrade = v },
            "pairs: what each key writes while the upgrade is on"),
        Text("Left radii", { it.radiiLeft }, { c, v -> c.radiiLeft = v },
            "where the bands meet, outward, as fractions of the bank's reach. " +
                "One more number than there are bands"),
        Text("Right radii", { it.radiiRight }, { c, v -> c.radiiRight = v }),
        Range("Tilt", -45f, 45f, { it.tilt }, { c, v -> c.tilt = v },
            "how far the rings are turned. A thumb sweeps an arc and the rings " +
                "are already arcs, so this may buy nothing. The right bank " +
                "turns the other way, because the two thumbs sweep mirrored " +
                "arcs", unit = "\u00b0"),
        Flag("One thumb walks both banks", { it.oneThumb }, { c, v -> c.oneThumb = v },
            "a touch that arrives in the other bank's field is handed over to " +
                "it, and the bank it leaves keeps what it took, so one hand can " +
                "write a whole stroke. Only while the other thumb is up, so two " +
                "hands never meet it"),
        Range("Preview over the app", 0f, 0.5f, { it.previewHeight },
            { c, v -> c.previewHeight = v },
            "a copy of the banks above the keys, drawn over the app and taking " +
                "no touches. Shrink the keys and a thumb crosses cells in less " +
                "travel; this buys back the legibility that costs. Zero for none",
            unit = "× screen"),

        Section("Strokes"),
        Flag("Write on the first lift", { it.commitOnFirstLift },
            { c, v -> c.commitOnFirstLift = v },
            "the thumb still down keeps its keys, so a shared right hand is " +
                "stroked once"),
        Text("Left armed keys", { it.stickyLeft }, { c, v -> c.stickyLeft = v },
            "a lift that took only these arms them for the next stroke " +
                "instead of writing; lifting on them again sends them alone"),
        Text("Right armed keys", { it.stickyRight }, { c, v -> c.stickyRight = v }),
        Text("Left double tap", { it.doubleTapLeft.toString() },
            { c, v -> v.firstOrNull()?.let { c.doubleTapLeft = it } },
            "the key two quick taps add on top of the key under them: tap tap " +
                "on -T releases as -*T"),
        Text("Right double tap", { it.doubleTapRight.toString() },
            { c, v -> v.firstOrNull()?.let { c.doubleTapRight = it } }),
        Text("Left long press", { it.longPressLeft.toString() },
            { c, v -> v.firstOrNull()?.let { c.longPressLeft = it } },
            "the key a touch standing still on its cell adds, which is the " +
                "modifier a thumb already on its key can take"),
        Text("Right long press", { it.longPressRight.toString() },
            { c, v -> v.firstOrNull()?.let { c.longPressRight = it } }),
        Range("Long press (ms)", 0f, 900f, { it.longPressMs.toFloat() },
            { c, v -> c.longPressMs = v.toLong() },
            "zero turns long presses off", unit = "ms"),
        Range("Double tap window (ms)", 0f, 500f, { it.doubleTapMs.toFloat() },
            { c, v -> c.doubleTapMs = v.toLong() },
            "two taps this close together are a double tap, so a quick tap's " +
                "stroke waits this long. Zero turns double taps off", unit = "ms"),

        Section("Buzzes"),
        Flag("Haptics", { it.haptics }, { c, v -> c.haptics = v }),
        Flag("Buzz on every change of cell", { it.hapticTicks },
            { c, v -> c.hapticTicks = v },
            "not only when a key is taken, so where the focus is becomes " +
                "something to feel rather than something to read"),
        Flag("A different buzz per thumb", { it.hapticSplit },
            { c, v -> c.hapticSplit = v },
            "one motor cannot be on the left and on the right, so this is two " +
                "voices from the same motor. What is available is printed below"),
        Range("Buzz strength", 0.05f, 1f, { it.hapticStrength },
            { c, v -> c.hapticStrength = v }, unit = "\u00d7"),
        Range("Shortest gap between buzzes", 5f, 120f, { it.hapticGapMs.toFloat() },
            { c, v -> c.hapticGapMs = v.toLong() },
            "or a walk across several cells is one long buzz", unit = "ms"),

        Section("Looks and sizes"),
        Flag("Show labels", { it.showLabels }, { c, v -> c.showLabels = v }),
        Flag("Show cells already passed", { it.showDeadCells },
            { c, v -> c.showDeadCells = v },
            "sunk towards the ground, with the label struck through"),
        Flag("Square banks", { it.squareBanks }, { c, v -> c.squareBanks = v },
            "a thumb sweeps an arc, and a ring stretched to a tall bank " +
                "answers the same sweep with different keys at different " +
                "angles. The keyboard takes only the depth a square needs"),
        Range("Row height", 0f, 0.3f, { it.rowHeight }, { c, v -> c.rowHeight = v },
            "the modifier row's share of a bank", unit = "of the bank"),
        Range("Roundness at the hub", 2f, 8f, { it.shapeHub }, { c, v -> c.shapeHub = v },
            "2 is a circle, and the larger it grows the closer a ring comes " +
                "to a rectangle"),
        Range("Roundness at the rim", 2f, 8f, { it.shapeRim }, { c, v -> c.shapeRim = v },
            "it rises outward, so the middle stays round and the outer rings " +
                "take the corners, where the room is"),
        Range("Key width", 0.05f, 0.4f, { it.keyWidth }, { c, v -> c.keyWidth = v },
            "one width for every ring, so the ground between two rings grows " +
                "outward as the rings do. That ground is what a slide passes " +
                "through on its way to a further key", unit = "of the reach"),
        Range("Key length", 0f, 1f, { it.keySpread }, { c, v -> c.keySpread = v },
            "how much of its slot a key runs along: 0 is a circle, 1 leaves " +
                "its neighbours no ground between them", unit = "of the slot"),
        Range("Key ends", 0f, 1f, { it.keyEnds }, { c, v -> c.keyEnds = v },
            "the width left at a key's ends. 1 is a rounded end, and less " +
                "tapers it to a point, which widens the corridor between two " +
                "keys of one ring", unit = "of the width"),
        Range("Label size (sp)", 8f, 28f, { it.labelSp }, { c, v -> c.labelSp = v },
            unit = "sp"),
        Range("Keyboard height", 0.2f, 0.8f, { it.keyboardHeight },
            { c, v -> c.keyboardHeight = v },
            "in portrait. The radial budget is the tightest thing here, so the " +
                "bands want the room", unit = "of the screen"),
        Range("Field split", 0.2f, 0.8f, { it.fieldSplit }, { c, v -> c.fieldSplit = v },
            "where the left field ends, in portrait", unit = "of the width"),
        Flag("Banks on the edges in landscape", { it.landscapeSides },
            { c, v -> c.landscapeSides = v },
            "each bank hugs its own edge with the bar in the gap between them. " +
                "Off puts the two side by side in the middle"),
        Range("Landscape keyboard height", 0.3f, 0.95f, { it.landscapeHeight },
            { c, v -> c.landscapeHeight = v },
            "the app keeps everything above the band. Android describes the " +
                "app's room with a top edge alone, so there is no way to make " +
                "the app narrower and leave the banks full height beside it",
            unit = "of the screen"),
        Range("Landscape field width", 0.15f, 0.5f, { it.splitLandscape },
            { c, v -> c.splitLandscape = v },
            "each field's share of the width, anchored to its own side",
            unit = "of the width"),
        Range("Thumb mark size", 0.008f, 0.06f, { it.markRadius },
            { c, v -> c.markRadius = v }, "the dot that shows where the thumb is",
            unit = "\u00d7 field"),

        Section("The touch panel"),
        Flag("Draw the touched area", { it.cloud }, { c, v -> c.cloud = v },
            "every sample of the contact, at the size and shape the driver " +
                "gives it"),
        Range("Samples kept", 20f, 1200f, { it.cloudSamples.toFloat() },
            { c, v -> c.cloudSamples = v.toInt() }, unit = "samples"),
        Range("Touch fade", 0.02f, 0.5f, { it.cloudAlpha }, { c, v -> c.cloudAlpha = v },
            unit = "\u00d7"),
        Flag("Print the touch numbers", { it.touchReadout },
            { c, v -> c.touchReadout = v },
            "pressure, size and the two contact axes: whether a hard press " +
                "can be told from a light one is a question these numbers answer"),
    )

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val prefs = getSharedPreferences(StenoService.PREFS, MODE_PRIVATE)
        config = Prefs.load(prefs)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 64)
        }
        column.addView(heading("Radial steno"))
        column.addView(note("Enable the keyboard in Settings, Languages and input. Changes here " +
            "apply the next time the keyboard opens."))
        column.addView(note("The keyboard at the bottom is a live preview: it follows every " +
            "change as it happens, and what it strokes goes nowhere."))
        column.addView(note("Every value says its unit. × field is a fraction of the thumb " +
            "field's short side, so a length means the same thing on any screen. " +
            "° is degrees clockwise, and -90 points straight up."))
        column.addView(Button(this).apply {
            setText("Restore defaults")
            setOnClickListener {
                Prefs.save(Config(), prefs)
                recreate()
            }
        })
        for (knob in knobs) {
            column.addView(row(knob, prefs))
            // What the motor can do is a fact about the phone, so it is printed
            // rather than promised, next to the switch that asks for it.
            if (knob is Flag && knob.label == "A different buzz per thumb") {
                column.addView(note("On this device: ${Haptics(this, config).report()}"))
            }
        }
        // The engine's steno order, spelled out: the preview draws glyphs and
        // geometry only, so it does not need the engine.
        val view = RingView(this, config, "#@^+STKPWHRAO*EUfrpblgtsdz", {}, embedded = true)
        preview = view
        val toggle = CheckBox(this).apply {
            text = "Show the preview"
            isChecked = true
            setOnCheckedChangeListener { _, _ -> previewRoom(editing = false) }
        }
        previewToggle = toggle
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                ScrollView(this@SettingsActivity).apply { addView(column) },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
            )
            addView(toggle.apply { setPadding(32, 8, 32, 8) })
            addView(view)
        })
    }

    private fun heading(what: String) = TextView(this).apply {
        text = what
        textSize = 22f
        setPadding(0, 0, 0, 12)
    }

    private fun note(what: String) = TextView(this).apply {
        text = what
        textSize = 12f
        alpha = 0.7f
        setPadding(0, 0, 0, 24)
    }

    private fun row(knob: Knob, prefs: android.content.SharedPreferences): ViewGroup {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
        }
        val title = TextView(this).apply { textSize = 15f }
        box.addView(title)
        if (knob.note.isNotEmpty()) box.addView(note(knob.note))

        fun keep() {
            Prefs.save(config, prefs)
            refresh()
        }

        when (knob) {
            is Section -> {
                title.text = knob.label
                title.textSize = 18f
                box.setPadding(0, 48, 0, 0)
            }

            is Range -> {
                title.text = show(knob)
                box.addView(SeekBar(this).apply {
                    max = 1000
                    progress = (((knob.get(config) - knob.min) / (knob.max - knob.min)) * 1000).toInt()
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(bar: SeekBar?, at: Int, byUser: Boolean) {
                            val value = knob.min + (knob.max - knob.min) * at / 1000f
                            knob.set(config, value)
                            title.text = show(knob)
                            // Mid-drag too, which is what makes the preview live.
                            refresh()
                        }

                        override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                        override fun onStopTrackingTouch(bar: SeekBar?) = keep()
                    })
                })
            }

            is Flag -> {
                title.text = knob.label
                box.addView(CheckBox(this).apply {
                    isChecked = knob.get(config)
                    setOnCheckedChangeListener { _, on ->
                        knob.set(config, on)
                        keep()
                    }
                })
            }

            is Text -> {
                title.text = knob.label
                box.addView(EditText(this).apply {
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    setText(knob.get(config))
                    setOnFocusChangeListener { _, focused ->
                        // The soft keyboard needs the preview's room.
                        previewRoom(editing = focused)
                        if (!focused) {
                            knob.set(config, text.toString())
                            keep()
                        }
                    }
                })
            }
        }
        return box
    }

    private fun show(knob: Range): String {
        val value = knob.get(config)
        val pretty = if (value >= 10f) value.toInt().toString() else String.format("%.3f", value)
        return if (knob.unit.isEmpty()) "${knob.label}: $pretty"
        else "${knob.label}: $pretty ${knob.unit}"
    }
}
