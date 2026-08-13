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
    private var preview: CombView? = null
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
        Section("The comb",
            "A fixed honeycomb per thumb, walked downward in steno order and " +
                "read by where the focus is. A layout is one character per " +
                "cell: a dot is open ground, o is a hole, a space is off the " +
                "board, and anything else is the key of that name. Rows are " +
                "separated by a slash. Alternate columns hang half a cell " +
                "lower, so a column runs down the glass and the chain of the " +
                "order zigzags between two of them: a key touches the next in " +
                "the order and the one after it. Repeat a value in touching " +
                "cells and they meld into one button, which is all a big key " +
                "is. Entering a cell takes what it yields, in steno order, " +
                "and a spent cell is ground. A key that does not touch the " +
                "next key of the walk is a float, and steno order says nothing " +
                "about a float: that is what the modifiers are."),
        Text("Left comb", { it.combLeft }, { c, v -> c.combLeft = v },
            "rows separated by a slash"),
        Text("Right comb", { it.combRight }, { c, v -> c.combRight = v }),
        Section("The portals",
            "Two of a cell's six sides face the keys the walk can still reach. " +
                "The other four are portals, and each carries a stride, " +
                "${Comb.STRIDES.joinToString(" ") { "+$it" }}, ordered by how far it turns from " +
                "the way the walk is heading. A stride rests on the ground " +
                "beside the key it aims at and takes nothing, leaving several " +
                "keys one hop away; where no ground is worth the hop it lands " +
                "on the key itself. A key the thumb can already step onto is " +
                "not worth anything, so a landing never repeats the ground it " +
                "left. The four are chosen together, for the most keys they " +
                "put within two hops between them.\n\n" +
                "A stride is not spent by one jump. Keep sliding the way the " +
                "jump went and it goes again, so a side is a run of landings " +
                "and not one. A key on the way out is taken rather than " +
                "ridden past.\n\n" +
                "These four numbers are not a setting. Of every set of four " +
                "they reach the furthest: 153 of the 155 keys a stroke can " +
                "want, which is everything the shipped grids allow."),
        Range("Tilt", -45f, 45f, { it.tilt }, { c, v -> c.tilt = v },
            "how far the comb is turned. Zero runs the columns straight down " +
                "the glass; a thumb sweeps along an arc, so a few degrees can " +
                "put the walk under it. The right bank turns the other way, " +
                "because the two thumbs sweep mirrored arcs", unit = "\u00b0"),
        Flag("One thumb walks both combs", { it.oneThumb }, { c, v -> c.oneThumb = v },
            "a touch that arrives in the other comb's field is handed over to " +
                "it, and the comb it leaves keeps what it took, so one hand can " +
                "write a whole stroke. Only while the other thumb is up, so two " +
                "hands never meet it"),
        Range("Preview over the app", 0f, 0.5f, { it.previewHeight },
            { c, v -> c.previewHeight = v },
            "a copy of the combs above the keys, drawn over the app and taking " +
                "no touches. Shrink the keys and a thumb crosses cells in less " +
                "travel; this buys back the legibility that costs. Zero for none",
            unit = "× screen"),
        Range("Comb fill", 0.4f, 1f, { it.combFill }, { c, v -> c.combFill = v },
            "one fills the field. Less packs the comb within reach; a thumb " +
                "that overshoots the drawn edge still means the cell it " +
                "overshot", unit = "\u00d7"),
        Range("Comb position", 0.2f, 0.8f, { it.combAt }, { c, v -> c.combAt = v },
            "where the middle of the comb sits", unit = "down the field"),
        Flag("Teleport through a hole", { it.combHole }, { c, v -> c.combHole = v },
            "a focus that walks into a hole is thrown straight across the " +
                "comb's middle, and the offset holds until the lift. Off by " +
                "default: the shipped layouts close a ring around their hole, " +
                "so nothing walks into one and a portal is the only way in. A " +
                "portal can land on a hole either way"),

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
        Range("Double tap window (ms)", 0f, 500f, { it.doubleTapMs.toFloat() },
            { c, v -> c.doubleTapMs = v.toLong() },
            "two taps this close together are a double tap, so a quick tap's " +
                "stroke waits this long. Zero turns double taps off", unit = "ms"),
        Range("Stall (ms)", 200f, 3000f, { it.stallMs.toFloat() },
            { c, v -> c.stallMs = v.toLong() },
            "hold still this long and a focus a portal threw away comes back " +
                "to the thumb, writing nothing", unit = "ms"),

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
        Flag("Show the steno order", { it.showChain }, { c, v -> c.showChain = v },
            "a faint chain through the keys, which is the walk"),
        Flag("Show where the portals go", { it.showPortals },
            { c, v -> c.showPortals = v },
            "from the cell under the focus, a line out of each portal side to " +
                "the cell it lands on, with an arrow there pointing back at " +
                "the side to leave by. A ring means arriving takes that key; a " +
                "dashed ring means it comes to rest beside it"),
        Flag("Show cells already passed", { it.showDeadCells },
            { c, v -> c.showDeadCells = v },
            "faded. They are portals now, and they stop looking like keys"),
        Range("Label size (sp)", 8f, 28f, { it.labelSp }, { c, v -> c.labelSp = v },
            unit = "sp"),
        Range("Keyboard height", 0.2f, 0.8f, { it.keyboardHeight },
            { c, v -> c.keyboardHeight = v },
            "in portrait. A comb runs down, so it wants the room", unit = "of the screen"),
        Range("Field split", 0.2f, 0.8f, { it.fieldSplit }, { c, v -> c.fieldSplit = v },
            "where the left field ends, in portrait", unit = "of the width"),
        Flag("Combs on the edges in landscape", { it.landscapeSides },
            { c, v -> c.landscapeSides = v },
            "each comb hugs its own edge with the bar in the gap between them. " +
                "Off puts the two side by side in the middle"),
        Range("Landscape keyboard height", 0.3f, 0.95f, { it.landscapeHeight },
            { c, v -> c.landscapeHeight = v },
            "the app keeps everything above the band. Android describes the " +
                "app's room with a top edge alone, so there is no way to make " +
                "the app narrower and leave the combs full height beside it",
            unit = "of the screen"),
        Flag("The record in the landscape gap", { it.showLog },
            { c, v -> c.showLog = v },
            "the gap between two edge combs fills with what the keyboard has " +
                "just done: every stroke, the text it wrote, and the briefer " +
                "outlines under it. Off leaves the one line of briefs"),
        Range("Landscape field width", 0.15f, 0.5f, { it.splitLandscape },
            { c, v -> c.splitLandscape = v },
            "each field's share of the width, anchored to its own side",
            unit = "of the width"),
        Range("Thumb mark size", 0.008f, 0.06f, { it.markRadius },
            { c, v -> c.markRadius = v },
            "the dot at the focus, and the fainter one at the thumb once a " +
                "portal has parted them", unit = "\u00d7 field"),

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
        val view = CombView(this, config, "#@^+STKPWHRAO*EUfrpblgtsdz", {}, embedded = true)
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
