package cx.yeet.stfst

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
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
    private class Choice(
        label: String,
        val names: List<String>,
        val get: (Config) -> Int,
        val set: (Config, Int) -> Unit,
        note: String = "",
    ) : Knob(label, note)

    /** A heading, so the list reads as sections rather than as thirty sliders. */
    private class Section(label: String, note: String = "") : Knob(label, note)

    private val knobs: List<Knob> = listOf(
        Section("The board",
            "The default layout: fixed cells, read by where the thumb is. Each " +
                "bank is a ring in take order around an island of the keys taken " +
                "first, and any cell the thumb enters is taken. A spent cell is " +
                "ground, the space between cells takes nothing, and a dead island " +
                "cell is a doorway to the key after its '>'."),
        Flag("Fixed board", { it.board }, { c, v -> c.board = v },
            "off falls back to the radial layout below, which reads slides by " +
                "direction instead"),
        Text("Left island", { it.boardIslandLeft }, { c, v -> c.boardIslandLeft = v },
            "taken before the ring: the vowels, so the middle opens with the first " +
                "key. A stroke is a set of keys, so the outline stays steno order " +
                "whatever order the thumb walked"),
        Text("Left board ring", { it.boardRingLeft }, { c, v -> c.boardRingLeft = v },
            "clockwise from the board start angle"),
        Text("Right island", { it.boardIslandRight }, { c, v -> c.boardIslandRight = v }),
        Text("Right board ring", { it.boardRingRight }, { c, v -> c.boardRingRight = v }),
        Text("Doorways", { it.boardPortals }, { c, v -> c.boardPortals = v },
            "pairs like A>R: a dead A cell takes R, for as long as R can still be " +
                "taken"),
        Range("Board span", 3f, 7f, { it.boardSpan }, { c, v -> c.boardSpan = v },
            "cell pitches across the field's short side; fewer means bigger cells",
            unit = "pitches"),
        Range("Board cell size", 0.25f, 0.5f, { it.boardCell }, { c, v -> c.boardCell = v },
            "a cell's radius against the pitch. Under a half leaves ground between " +
                "the cells, which is the only way across without taking",
            unit = "× pitch"),
        Range("Board position", 0.2f, 0.8f, { it.boardAt }, { c, v -> c.boardAt = v },
            "where the middle of the board sits", unit = "down the field"),
        Range("Left board start", -180f, 180f, { it.boardStartLeft },
            { c, v -> c.boardStartLeft = v },
            "where the ring's first key sits; -90 is straight up", unit = "°"),
        Range("Right board start", -180f, 180f, { it.boardStartRight },
            { c, v -> c.boardStartRight = v }, unit = "°"),

        Section("The radial layout",
            "The fallback. A touch-down reads the honeycomb over the whole field, " +
                "because a tap has no direction to it. Every slide after that reads " +
                "the ring around the last key: which way, never how far, clockwise " +
                "from the start angle."),
        Range("Tap layout columns", 2f, 6f, { it.entryCols.toFloat() },
            { c, v -> c.entryCols = v.toInt() },
            "the widest row of the honeycomb. Three puts thirteen keys in rows of " +
                "3 2 3 2 3. More columns make every cell smaller", unit = "columns"),
        Range("Cell width", 0.3f, 1.2f, { it.entryWide }, { c, v -> c.entryWide = v },
            "against a honeycomb that fills the field. One fills it", unit = "×"),
        Range("Cell height", 0.3f, 1.2f, { it.entryTall }, { c, v -> c.entryTall = v },
            "the same for the rows. Less packs the keys within reach, and the cells " +
                "at the edge grow to cover the field the lattice no longer reaches, " +
                "because a tap out there is still nearest the key it always was",
            unit = "×"),
        Range("Honeycomb position", 0.2f, 0.8f, { it.entryAt }, { c, v -> c.entryAt = v },
            "where its middle sits, measured down the field", unit = "down the field"),
        Flag("Early keys in the middle rows", { it.entryCentral }, { c, v -> c.entryCentral = v },
            "an early key is where the sliding starts, so it sits in the middle of " +
                "the field with room to slide. Off is plain reading order"),
        Text("Left ring", { it.farLeft }, { c, v -> c.farLeft = v },
            "the slide keys, in steno order, clockwise from the start angle"),
        Text("Right ring", { it.farRight }, { c, v -> c.farRight = v }),
        Text("Bank split key", { it.splitKey.toString() },
            { c, v -> v.firstOrNull()?.let { c.splitKey = it } },
            "the first key the right thumb owns"),
        Range("Left start angle", -180f, 180f, { it.startFarLeft }, { c, v -> c.startFarLeft = v },
            "-90 is straight up", unit = "°"),
        Range("Right start angle", -180f, 180f, { it.startFarRight }, { c, v -> c.startFarRight = v }, unit = "°"),

        Section("How a slide is read",
            "All lengths are fractions of the thumb field's short side, so they mean " +
                "the same thing on any screen."),
        Range("Minimum slide", 0.01f, 0.25f, { it.minSlide }, { c, v -> c.minSlide = v },
            "shorter than this and a slide means nothing", unit = "× field"),
        Range("Ring reach", 0.15f, 0.6f, { it.farReach }, { c, v -> c.farReach = v },
            "how far out the keys sit", unit = "× field"),
        Range("Hold on to a key", 0f, 30f, { it.hysteresisDegrees }, { c, v -> c.hysteresisDegrees = v },
            "how much better a rival has to be before the answer changes. A thumb " +
                "resting on a boundary is nearer one key and then the other on every " +
                "touch sample, and this is what stops the answer swapping with it", unit = "°"),
        Range("Turn needed (degrees)", 0f, 44f, { it.turnDegrees }, { c, v -> c.turnDegrees = v },
            "how far a slide must turn before it takes another key, so one long " +
                "reach is one key", unit = "°"),
        Range("Stall (ms)", 200f, 3000f, { it.stallMs.toFloat() }, { c, v -> c.stallMs = v.toLong() },
            "hold still this long and the ring goes home", unit = "ms"),
        Range("Home point", 0.2f, 0.95f, { it.homeAt }, { c, v -> c.homeAt = v },
            "where in the field a stalled ring returns to, measured from the top", unit = "down the field"),

        Section("When a stroke is written"),
        Flag("Write on the first lift", { it.commitOnFirstLift }, { c, v -> c.commitOnFirstLift = v },
            "the thumb still down keeps its keys, so a shared right hand is stroked once"),
        Flag("Long press for modifiers", { it.longPress }, { c, v -> c.longPress = v },
            "off by default: a hold is easy to do by accident while learning"),
        Range("Long press (ms)", 150f, 1200f, { it.longPressMs.toFloat() }, { c, v -> c.longPressMs = v.toLong() }, unit = "ms"),

        Section("Look and feel",
            "Each key is drawn as the whole region that takes it, so the target is " +
                "the shape on the screen and not a point inside it."),
        Section("Buzzes"),
        Flag("Haptics", { it.haptics }, { c, v -> c.haptics = v }),
        Flag("Buzz on every change of key", { it.hapticTicks }, { c, v -> c.hapticTicks = v },
            "not only when a key is taken, so the aim of a slide is something to feel " +
                "rather than something to read"),
        Flag("A different buzz per thumb", { it.hapticSplit }, { c, v -> c.hapticSplit = v },
            "one motor cannot be on the left and on the right, so this is two voices " +
                "from the same motor. What is available is printed below"),
        Range("Buzz strength", 0.05f, 1f, { it.hapticStrength }, { c, v -> c.hapticStrength = v },
            unit = "×"),
        Range("Shortest gap between buzzes", 5f, 120f, { it.hapticGapMs.toFloat() },
            { c, v -> c.hapticGapMs = v.toLong() },
            "or a slide across several keys is one long buzz", unit = "ms"),
        Flag("Show keys already passed", { it.showDeadSlots }, { c, v -> c.showDeadSlots = v },
            "on the tap layout; a slide has no region for a key steno order has passed"),
        Flag("Show labels", { it.showLabels }, { c, v -> c.showLabels = v }),
        Range("Region outer edge", 0.3f, 0.8f, { it.farEdge }, { c, v -> c.farEdge = v },
            "how far out the far ring is drawn. Past it the same key is still taken", unit = "× field"),
        Range("Region step (degrees)", 0.5f, 10f, { it.sectorStep }, { c, v -> c.sectorStep = v },
            "how finely the drawn regions are measured. Smaller is more exact", unit = "°"),
        Choice("Copy of the ring", Preview.values().map { it.label },
            { it.preview.ordinal }, { c, v -> c.preview = Preview.values()[v] },
            "the thumb covers the ring it is choosing from, so the copy goes " +
                "somewhere the thumb is not. Floating over the app costs the app no " +
                "room: the strip is empty and untouchable until a thumb is down. A " +
                "strip above the keys takes the room instead and pushes the app up"),
        Range("Copy position", 0.05f, 0.6f, { it.mirrorAt }, { c, v -> c.mirrorAt = v },
            "where in the field the copy sits, from the top. It stays put, so it " +
                "cannot follow a thumb off the screen", unit = "down the field"),
        Range("Copy scale", 0.3f, 1f, { it.mirrorScale }, { c, v -> c.mirrorScale = v }, unit = "×"),
        Range("Copy fade", 0.2f, 1f, { it.mirrorDim }, { c, v -> c.mirrorDim = v },
            "one is as strong as the ring under the thumb", unit = "×"),

        Section("What the touch panel reports"),
        Flag("Draw the touched area", { it.cloud }, { c, v -> c.cloud = v },
            "every sample of the contact, at the size and shape the driver gives it"),
        Range("Samples kept", 20f, 1200f, { it.cloudSamples.toFloat() },
            { c, v -> c.cloudSamples = v.toInt() }, unit = "samples"),
        Range("Touch fade", 0.02f, 0.5f, { it.cloudAlpha }, { c, v -> c.cloudAlpha = v }, unit = "×"),
        Flag("Print the touch numbers", { it.touchReadout }, { c, v -> c.touchReadout = v },
            "pressure, size and the two contact axes. Whether a hard press can be " +
                "told from a light one is a question about the hardware, and these " +
                "are the numbers that answer it"),

        Section("Sizes"),
        Range("Field split", 0.2f, 0.8f, { it.fieldSplit }, { c, v -> c.fieldSplit = v },
            "where the left field ends", unit = "of the width"),
        Range("Thumb mark size", 0.008f, 0.06f, { it.markRadius }, { c, v -> c.markRadius = v },
            "the dot at the last key and the ring at the fingertip", unit = "× field"),
        Range("Label size (sp)", 8f, 28f, { it.labelSp }, { c, v -> c.labelSp = v }, unit = "sp"),
        Range("Keyboard height", 0.2f, 0.7f, { it.keyboardHeight }, { c, v -> c.keyboardHeight = v }, unit = "of the screen"),
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
        setContentView(ScrollView(this).apply { addView(column) })
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

        fun keep() = Prefs.save(config, prefs)

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
                        }

                        override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                        override fun onStopTrackingTouch(bar: SeekBar?) = keep()
                    })
                })
            }

            is Choice -> {
                title.text = knob.label
                box.addView(Spinner(this).apply {
                    adapter = ArrayAdapter(
                        this@SettingsActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        knob.names,
                    )
                    setSelection(knob.get(config))
                    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(p: AdapterView<*>?, v: View?, at: Int, id: Long) {
                            knob.set(config, at)
                            keep()
                        }

                        override fun onNothingSelected(p: AdapterView<*>?) = Unit
                    }
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
