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

    /** The keyboard itself, pinned under the knobs, redrawn on every change. */
    private var preview: RadialView? = null

    private fun refresh() {
        preview?.apply(config)
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
            "The default layout: a staggered ring of slices round an island, " +
                "read by where the thumb is. Each ring key is one slice, and " +
                "every other slice sits further out, which opens two lanes: a " +
                "sweep along either one crosses every other key, a machine " +
                "row. Any cell the thumb enters is taken, in any order, and a " +
                "taken cell is ground. Island keys go before the ring, and " +
                "once the ring closes the island a dead island cell is a " +
                "doorway to the key after its '>'. The corridor inside the " +
                "ring and the field past its outer edge take nothing: they " +
                "are the roads past a live key. All radii are in pitches, and " +
                "the span sets what a pitch is."),
        Flag("Fixed board", { it.board }, { c, v -> c.board = v },
            "off falls back to the radial layout below, which reads slides by " +
                "direction instead"),
        Text("Left island", { it.boardIslandLeft }, { c, v -> c.boardIslandLeft = v },
            "taken before the ring, in any order, like the machine's thumb " +
                "cluster. A stroke is a set of keys, so the outline stays steno " +
                "order whatever order the thumb walked"),
        Text("Left board ring", { it.boardRingLeft }, { c, v -> c.boardRingLeft = v },
            "in steno order, one slice per key, clockwise from the start angle"),
        Text("Right island", { it.boardIslandRight }, { c, v -> c.boardIslandRight = v }),
        Text("Right board ring", { it.boardRingRight }, { c, v -> c.boardRingRight = v }),
        Text("Doorways", { it.boardPortals }, { c, v -> c.boardPortals = v },
            "pairs like A>R: a dead A cell takes R, for as long as R can still be " +
                "taken"),
        Range("Board span", 3f, 7f, { it.boardSpan }, { c, v -> c.boardSpan = v },
            "pitches across the field's short side; fewer means a bigger board",
            unit = "pitches"),
        Range("Ring inner edge", 1.0f, 2.2f, { it.boardInner }, { c, v -> c.boardInner = v },
            "where the slices begin. The gap between it and the island is the " +
                "corridor, so lower is deeper slices and a narrower road",
            unit = "pitches"),
        Range("Ring outer edge", 1.5f, 3.2f, { it.boardOuter }, { c, v -> c.boardOuter = v },
            "where the slices end. Past it is the outer road: nothing takes",
            unit = "pitches"),
        Range("Stagger", 0f, 0.8f, { it.boardStagger }, { c, v -> c.boardStagger = v },
            "how much further out every other slice sits. The stagger is the " +
                "height of the two lanes, and zero is a plain ring with none",
            unit = "pitches"),
        Flag("Top row inside", { it.boardTopIn }, { c, v -> c.boardTopIn = v },
            "the machine's top row (T P H, and F P L T D) on the inward " +
                "slices; off swaps the alternation"),
        Range("Island cell size", 0.25f, 0.5f, { it.boardCell }, { c, v -> c.boardCell = v },
            "an island disc's radius", unit = "pitches"),
        Range("Board position", 0.2f, 0.8f, { it.boardAt }, { c, v -> c.boardAt = v },
            "where the middle of the board sits", unit = "down the field"),
        Range("Left board start", -180f, 180f, { it.boardStartLeft },
            { c, v -> c.boardStartLeft = v },
            "where the ring's first key sits; -90 is straight up", unit = "\u00b0"),
        Range("Right board start", -180f, 180f, { it.boardStartRight },
            { c, v -> c.boardStartRight = v }, unit = "\u00b0"),

        Section("The copy",
            "The thumb covers the keys it is choosing from, so a copy of its " +
                "bank is drawn somewhere the thumb is not, at one steady scale " +
                "whatever the board's own knobs say."),
        Choice("Where the copy goes", Preview.values().map { it.label },
            { it.preview.ordinal }, { c, v -> c.preview = Preview.values()[v] },
            "floating over the app costs the app no room: the strip is empty and " +
                "untouchable until a thumb is down. A strip above the keys takes " +
                "the room instead and pushes the app up"),
        Range("Copy scale", 0.3f, 1f, { it.mirrorScale }, { c, v -> c.mirrorScale = v },
            "against the board under the thumb. The copy shrinks below this only " +
                "when the strip cannot hold it", unit = "\u00d7"),
        Range("Copy fade", 0.2f, 1f, { it.mirrorDim }, { c, v -> c.mirrorDim = v },
            "one is as strong as the board under the thumb", unit = "\u00d7"),
        Range("Copy position", 0.05f, 0.6f, { it.mirrorAt }, { c, v -> c.mirrorAt = v },
            "only when the copy sits in the thumb field itself", unit = "down the field"),

        Section("Strokes"),
        Flag("Write on the first lift", { it.commitOnFirstLift }, { c, v -> c.commitOnFirstLift = v },
            "the thumb still down keeps its keys, so a shared right hand is stroked once"),
        Flag("Long press for modifiers", { it.longPress }, { c, v -> c.longPress = v },
            "off by default: a hold is easy to do by accident while learning"),
        Range("Long press (ms)", 150f, 1200f, { it.longPressMs.toFloat() }, { c, v -> c.longPressMs = v.toLong() }, unit = "ms"),

        Section("Buzzes"),
        Flag("Haptics", { it.haptics }, { c, v -> c.haptics = v }),
        Flag("Buzz on every change of key", { it.hapticTicks }, { c, v -> c.hapticTicks = v },
            "not only when a key is taken, so the aim of a slide is something to feel " +
                "rather than something to read"),
        Flag("A different buzz per thumb", { it.hapticSplit }, { c, v -> c.hapticSplit = v },
            "one motor cannot be on the left and on the right, so this is two voices " +
                "from the same motor. What is available is printed below"),
        Range("Buzz strength", 0.05f, 1f, { it.hapticStrength }, { c, v -> c.hapticStrength = v },
            unit = "\u00d7"),
        Range("Shortest gap between buzzes", 5f, 120f, { it.hapticGapMs.toFloat() },
            { c, v -> c.hapticGapMs = v.toLong() },
            "or a slide across several keys is one long buzz", unit = "ms"),

        Section("Looks and sizes"),
        Flag("Show labels", { it.showLabels }, { c, v -> c.showLabels = v }),
        Range("Label size (sp)", 8f, 28f, { it.labelSp }, { c, v -> c.labelSp = v },
            "a label shrinks below this where its cell cannot hold it", unit = "sp"),
        Range("Keyboard height", 0.2f, 0.7f, { it.keyboardHeight }, { c, v -> c.keyboardHeight = v }, unit = "of the screen"),
        Range("Field split", 0.2f, 0.8f, { it.fieldSplit }, { c, v -> c.fieldSplit = v },
            "where the left field ends", unit = "of the width"),
        Range("Thumb mark size", 0.008f, 0.06f, { it.markRadius }, { c, v -> c.markRadius = v },
            "the dot at the last key and the ring at the fingertip", unit = "\u00d7 field"),

        Section("The radial fallback",
            "The other layout, when the board is off. A touch-down reads a " +
                "honeycomb over the whole field; every slide after that reads the " +
                "ring around the last key: which way, never how far. Lengths here " +
                "are fractions of the field's short side."),
        Range("Tap layout columns", 2f, 6f, { it.entryCols.toFloat() },
            { c, v -> c.entryCols = v.toInt() },
            "the widest row of the honeycomb. Three puts thirteen keys in rows of " +
                "3 2 3 2 3. More columns make every cell smaller", unit = "columns"),
        Range("Cell width", 0.3f, 1.2f, { it.entryWide }, { c, v -> c.entryWide = v },
            "against a honeycomb that fills the field. One fills it", unit = "\u00d7"),
        Range("Cell height", 0.3f, 1.2f, { it.entryTall }, { c, v -> c.entryTall = v },
            "less packs the keys within reach, and the cells at the edge grow to " +
                "cover the field the lattice no longer reaches", unit = "\u00d7"),
        Range("Honeycomb position", 0.2f, 0.8f, { it.entryAt }, { c, v -> c.entryAt = v },
            "where its middle sits, measured down the field", unit = "down the field"),
        Flag("Early keys in the middle rows", { it.entryCentral }, { c, v -> c.entryCentral = v },
            "an early key is where the sliding starts, so it sits in the middle of " +
                "the field with room to slide. Off is plain reading order"),
        Flag("Show keys already passed", { it.showDeadSlots }, { c, v -> c.showDeadSlots = v },
            "on the honeycomb; a slide has no region for a key steno order has passed"),
        Text("Left ring", { it.farLeft }, { c, v -> c.farLeft = v },
            "the slide keys, in steno order, clockwise from the start angle"),
        Text("Right ring", { it.farRight }, { c, v -> c.farRight = v }),
        Text("Bank split key", { it.splitKey.toString() },
            { c, v -> v.firstOrNull()?.let { c.splitKey = it } },
            "the first key the right thumb owns"),
        Range("Left start angle", -180f, 180f, { it.startFarLeft }, { c, v -> c.startFarLeft = v },
            "-90 is straight up", unit = "\u00b0"),
        Range("Right start angle", -180f, 180f, { it.startFarRight }, { c, v -> c.startFarRight = v }, unit = "\u00b0"),
        Range("Minimum slide", 0.01f, 0.25f, { it.minSlide }, { c, v -> c.minSlide = v },
            "shorter than this and a slide means nothing", unit = "\u00d7 field"),
        Range("Ring reach", 0.15f, 0.6f, { it.farReach }, { c, v -> c.farReach = v },
            "how far out the keys sit", unit = "\u00d7 field"),
        Range("Region outer edge", 0.3f, 0.8f, { it.farEdge }, { c, v -> c.farEdge = v },
            "how far out the ring is drawn. Past it the same key is still taken", unit = "\u00d7 field"),
        Range("Region step (degrees)", 0.5f, 10f, { it.sectorStep }, { c, v -> c.sectorStep = v },
            "how finely the drawn regions are measured. Smaller is more exact", unit = "\u00b0"),
        Range("Hold on to a key", 0f, 30f, { it.hysteresisDegrees }, { c, v -> c.hysteresisDegrees = v },
            "how much better a rival has to be before the answer changes, which is " +
                "what stops the answer swapping at a boundary", unit = "\u00b0"),
        Range("Turn needed (degrees)", 0f, 44f, { it.turnDegrees }, { c, v -> c.turnDegrees = v },
            "how far a slide must turn before it takes another key, so one long " +
                "reach is one key", unit = "\u00b0"),
        Range("Stall (ms)", 200f, 3000f, { it.stallMs.toFloat() }, { c, v -> c.stallMs = v.toLong() },
            "hold still this long and the ring goes home", unit = "ms"),
        Range("Home point", 0.2f, 0.95f, { it.homeAt }, { c, v -> c.homeAt = v },
            "where in the field a stalled ring returns to, measured from the top", unit = "down the field"),

        Section("The touch panel"),
        Flag("Draw the touched area", { it.cloud }, { c, v -> c.cloud = v },
            "every sample of the contact, at the size and shape the driver gives it"),
        Range("Samples kept", 20f, 1200f, { it.cloudSamples.toFloat() },
            { c, v -> c.cloudSamples = v.toInt() }, unit = "samples"),
        Range("Touch fade", 0.02f, 0.5f, { it.cloudAlpha }, { c, v -> c.cloudAlpha = v }, unit = "\u00d7"),
        Flag("Print the touch numbers", { it.touchReadout }, { c, v -> c.touchReadout = v },
            "pressure, size and the two contact axes: whether a hard press can be " +
                "told from a light one is a question these numbers answer"),
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
        // The engine's steno order. The preview draws glyphs and geometry only,
        // so it does not need the runtime the real keyboard asks for it.
        val view = RadialView(this, config, "#@^+STKPWHRAO*EUfrpblgtsdz", {}, { _, _ -> })
        preview = view
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                ScrollView(this@SettingsActivity).apply { addView(column) },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
            )
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
