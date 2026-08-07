package cx.yeet.stfst

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
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

    private val knobs: List<Knob> = listOf(
        Text("Left far ring", { it.farLeft }, { c, v -> c.farLeft = v },
            "keys on the long slide, in steno order, clockwise from the start angle"),
        Text("Left near ring", { it.nearLeft }, { c, v -> c.nearLeft = v }),
        Text("Right far ring", { it.farRight }, { c, v -> c.farRight = v }),
        Text("Right near ring", { it.nearRight }, { c, v -> c.nearRight = v }),
        Text("Bank split key", { it.splitKey.toString() },
            { c, v -> v.firstOrNull()?.let { c.splitKey = it } },
            "the first key the right thumb owns"),

        Range("Far ring slots", 4f, 12f, { it.slotsFar.toFloat() }, { c, v -> c.slotsFar = v.toInt() }),
        Range("Near ring slots", 4f, 12f, { it.slotsNear.toFloat() }, { c, v -> c.slotsNear = v.toInt() }),
        Range("Left far start angle", -180f, 180f, { it.startFarLeft }, { c, v -> c.startFarLeft = v },
            "-90 is straight up"),
        Range("Left near start angle", -180f, 180f, { it.startNearLeft }, { c, v -> c.startNearLeft = v }),
        Range("Right far start angle", -180f, 180f, { it.startFarRight }, { c, v -> c.startFarRight = v }),
        Range("Right near start angle", -180f, 180f, { it.startNearRight }, { c, v -> c.startNearRight = v }),

        Range("Minimum slide", 0.01f, 0.25f, { it.minSlide }, { c, v -> c.minSlide = v },
            "shorter than this and a slide means nothing"),
        Range("Ring boundary", 0.05f, 0.4f, { it.farRadius }, { c, v -> c.farRadius = v },
            "where the near ring ends and the far ring starts"),
        Range("Snap limit", 10f, 180f, { it.snapDegrees }, { c, v -> c.snapDegrees = v },
            "how far off a slot's angle a slide may be; 180 always takes the nearest"),
        Range("Stall (ms)", 200f, 3000f, { it.stallMs.toFloat() }, { c, v -> c.stallMs = v.toLong() },
            "hold still this long and the ring goes home"),

        Flag("Write on the first lift", { it.commitOnFirstLift }, { c, v -> c.commitOnFirstLift = v },
            "the thumb still down keeps its keys, so a shared right hand is stroked once"),
        Flag("Long press for modifiers", { it.longPress }, { c, v -> c.longPress = v },
            "off by default: a hold is easy to do by accident while learning"),
        Range("Long press (ms)", 150f, 1200f, { it.longPressMs.toFloat() }, { c, v -> c.longPressMs = v.toLong() }),

        Flag("Haptics", { it.haptics }, { c, v -> c.haptics = v }),
        Flag("Show keys already passed", { it.showDeadSlots }, { c, v -> c.showDeadSlots = v }),
        Flag("Show labels", { it.showLabels }, { c, v -> c.showLabels = v }),

        Range("Field split", 0.2f, 0.8f, { it.fieldSplit }, { c, v -> c.fieldSplit = v },
            "where the left field ends"),
        Range("Key size", 0.02f, 0.12f, { it.keyRadius }, { c, v -> c.keyRadius = v }),
        Range("Label size (sp)", 8f, 28f, { it.labelSp }, { c, v -> c.labelSp = v }),
        Range("Keyboard height", 0.2f, 0.7f, { it.keyboardHeight }, { c, v -> c.keyboardHeight = v }),
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
        for (knob in knobs) column.addView(row(knob, prefs))
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
            is Range -> {
                title.text = "${knob.label}: ${pretty(knob.get(config))}"
                box.addView(SeekBar(this).apply {
                    max = 1000
                    progress = (((knob.get(config) - knob.min) / (knob.max - knob.min)) * 1000).toInt()
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(bar: SeekBar?, at: Int, byUser: Boolean) {
                            val value = knob.min + (knob.max - knob.min) * at / 1000f
                            knob.set(config, value)
                            title.text = "${knob.label}: ${pretty(knob.get(config))}"
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

    private fun pretty(value: Float): String =
        if (value >= 10f) value.toInt().toString() else String.format("%.3f", value)
}
