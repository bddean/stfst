package cx.yeet.stfst

import android.app.Activity
import android.os.Bundle
import android.text.InputType
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

    /** A heading, so the list reads as sections rather than as thirty sliders. */
    private class Section(label: String, note: String = "") : Knob(label, note)

    private val knobs: List<Knob> = listOf(
        Section("Which key is where",
            "Two layouts. A touch-down reads the honeycomb over the whole field, " +
                "because a tap has no direction to it. Every slide after that reads " +
                "the two rings around the last key: the far ring is the long slide " +
                "and the near ring the short one, clockwise from the start angle."),
        Range("Tap layout columns", 2f, 6f, { it.entryCols.toFloat() },
            { c, v -> c.entryCols = v.toInt() },
            "the widest row of the honeycomb. Three puts thirteen keys in rows of " +
                "3 2 3 2 3. More columns make every cell smaller", unit = "columns"),
        Flag("Early keys in the middle rows", { it.entryCentral }, { c, v -> c.entryCentral = v },
            "an early key is where the sliding starts, so it sits in the middle of " +
                "the field with room to slide. Off is plain reading order"),
        Text("Left far ring", { it.farLeft }, { c, v -> c.farLeft = v },
            "keys on the long slide, in steno order, clockwise from the start angle"),
        Text("Left near ring", { it.nearLeft }, { c, v -> c.nearLeft = v }),
        Text("Right far ring", { it.farRight }, { c, v -> c.farRight = v }),
        Text("Right near ring", { it.nearRight }, { c, v -> c.nearRight = v }),
        Text("Bank split key", { it.splitKey.toString() },
            { c, v -> v.firstOrNull()?.let { c.splitKey = it } },
            "the first key the right thumb owns"),

        Flag("Share each ring out evenly", { it.spread }, { c, v -> c.spread = v },
            "the keys still live on a ring divide it between them, earliest at the " +
                "start angle. Fixed slots squeeze a key with a live neighbour on each " +
                "side: three live keys on eight slots can leave the middle one 45 " +
                "degrees against 157 each. Even, all three hold 120"),
        Range("Far ring slots", 4f, 12f, { it.slotsFar.toFloat() }, { c, v -> c.slotsFar = v.toInt() },
            "only when the ring is not shared out evenly", unit = "slots"),
        Range("Near ring slots", 4f, 12f, { it.slotsNear.toFloat() }, { c, v -> c.slotsNear = v.toInt() }, unit = "slots"),
        Range("Left far start angle", -180f, 180f, { it.startFarLeft }, { c, v -> c.startFarLeft = v },
            "-90 is straight up", unit = "°"),
        Range("Left near start angle", -180f, 180f, { it.startNearLeft }, { c, v -> c.startNearLeft = v }, unit = "°"),
        Range("Right far start angle", -180f, 180f, { it.startFarRight }, { c, v -> c.startFarRight = v }, unit = "°"),
        Range("Right near start angle", -180f, 180f, { it.startNearRight }, { c, v -> c.startNearRight = v }, unit = "°"),

        Section("How far a slide has to go",
            "All lengths are fractions of the thumb field's short side, so they mean " +
                "the same thing on any screen."),
        Range("Minimum slide", 0.01f, 0.25f, { it.minSlide }, { c, v -> c.minSlide = v },
            "shorter than this and a slide means nothing", unit = "× field"),
        Range("Near ring reach", 0.05f, 0.3f, { it.nearReach }, { c, v -> c.nearReach = v },
            "how far out the short-slide keys sit, and what a short slide is", unit = "× field"),
        Range("Far ring reach", 0.15f, 0.6f, { it.farReach }, { c, v -> c.farReach = v },
            "the same for the long-slide keys", unit = "× field"),
        Range("Ring boundary", 0.1f, 0.9f, { it.ringSplitAt }, { c, v -> c.ringSplitAt = v },
            "where between the rings a slide stops being short; low favours the far ring", unit = "of the gap"),
        Section("How a slide is read"),
        Range("Snap limit", 10f, 180f, { it.snapDegrees }, { c, v -> c.snapDegrees = v },
            "how far off a slot's angle a slide may be; 180 always takes the nearest", unit = "°"),
        Range("Hold on to a key", 0f, 30f, { it.hysteresisDegrees }, { c, v -> c.hysteresisDegrees = v },
            "how much better a rival has to be before the answer changes. A thumb " +
                "resting on a boundary is nearer one key and then the other on every " +
                "touch sample, and this is what stops the answer swapping with it", unit = "°"),
        Range("Settle (ms)", 0f, 300f, { it.settleMs.toFloat() }, { c, v -> c.settleMs = v.toLong() },
            "how long a near-ring key waits before it is taken. A thumb reaching for " +
                "a consonant crosses the near ring on the way, and this is what stops " +
                "it collecting a vowel. Zero takes both rings at once", unit = "ms"),
        Range("Turn needed (degrees)", 0f, 44f, { it.turnDegrees }, { c, v -> c.turnDegrees = v },
            "how far a slide must turn before it takes another far key, so one long " +
                "reach is one key. Keep it under 45, the angle between two slots", unit = "°"),
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
        Flag("Copy the ring in the clear", { it.mirror }, { c, v -> c.mirror = v },
            "so the thumb is not covering the keys it is choosing between"),
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
