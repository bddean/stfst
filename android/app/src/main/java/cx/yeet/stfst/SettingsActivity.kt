package cx.yeet.stfst

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

/**
 * Every parameter, editable on the phone.
 *
 * The knob list is still the whole screen: one entry per parameter, each saying
 * how to read it and how to write it, so adding a parameter to [Config] and a
 * line here is the whole cost of exposing it.
 *
 * What the screen is built around is seeing the answer. A change lands in the
 * live keyboard below the knobs as it is made, mid-drag; the ones a bank cannot
 * show land in [Fit], which is the two screens at a glance; a buzz plays itself.
 * Parameters that bound each other say so: a knob shows the value that took
 * effect beside the value asked for, and the run of its slider that cannot be
 * reached is shaded, both recomputed whenever anything else moves.
 *
 * The prose is behind a `?` rather than in the column. A paragraph per knob is
 * worth reading once and is in the way every time after that.
 */
class SettingsActivity : Activity() {

    private lateinit var config: Config
    private lateinit var prefs: SharedPreferences

    /** The keyboard itself, pinned under the knobs, redrawn on every change. */
    private var preview: RingView? = null

    /** The two screens, pinned for the group that is about them. */
    private var fit: Fit? = null

    private var buzzer: Haptics? = null

    /** Whether the preview was asked for, and whether a text field wants its room. */
    private var wanted = true
    private var editing = false

    /** The open group's rows, and where they go. */
    private val shown = mutableListOf<() -> Unit>()
    private lateinit var page: LinearLayout
    private var tabs: LinearLayout? = null
    private var at = 0

    /** One side of a parameter that has one per bank, or the whole of one that does not. */
    private class Side<T>(val get: (Config) -> T, val set: (Config, T) -> Unit)

    /**
     * A parameter, and the two halves of getting at it.
     *
     * [play] is what the change is like, for the ones no picture can show: a
     * buzz is its own preview, and nothing else on the screen is.
     */
    private sealed class Knob(
        val label: String,
        val note: String,
        val prose: String,
        val play: (() -> Unit)?,
    )

    private class Range(
        label: String,
        val min: Float,
        val max: Float,
        val get: (Config) -> Float,
        val set: (Config, Float) -> Unit,
        note: String = "",
        prose: String = "",
        /** Printed after the value, because a bare 0.13 says nothing. */
        val unit: String = "",
        /** What the value comes out as after the others have had their say. */
        val cap: ((Config) -> Float)? = null,
        play: (() -> Unit)? = null,
    ) : Knob(label, note, prose, play)

    private class Flag(
        label: String,
        val get: (Config) -> Boolean,
        val set: (Config, Boolean) -> Unit,
        note: String = "",
        prose: String = "",
        play: (() -> Unit)? = null,
    ) : Knob(label, note, prose, play)

    /** Free text: a layout, or a set of keys. One field, or one per bank. */
    private class Text(
        label: String,
        val left: Side<String>,
        val right: Side<String>? = null,
        note: String = "",
        prose: String = "",
    ) : Knob(label, note, prose, null)

    /**
     * One of a short, closed set: a key a gesture can take, a voice, a theme.
     *
     * [play] is what the choice sounds or feels like, where it has one, which
     * is the whole of the feedback a buzz can give.
     */
    private class Pick(
        label: String,
        val left: Side<String>,
        val right: Side<String>? = null,
        val options: (Config) -> List<String> = { emptyList() },
        val chosen: ((String, Bank) -> Unit)? = null,
        note: String = "",
        prose: String = "",
    ) : Knob(label, note, prose, null)

    /**
     * A flag, and the knobs it governs.
     *
     * The number a flag turns off stays on the screen, greyed, rather than
     * going away: where it stood is part of how the screen is read, and what it
     * said is what turning the flag back on restores.
     */
    private class Switch(
        label: String,
        val get: (Config) -> Boolean,
        val set: (Config, Boolean) -> Unit,
        note: String = "",
        prose: String = "",
        play: (() -> Unit)? = null,
        val knobs: List<Knob> = emptyList(),
    ) : Knob(label, note, prose, play)

    /** A tab: the knobs it holds, and whether the two screens are pinned for it. */
    private class Group(
        val name: String,
        val prose: String,
        val fit: Boolean = false,
        val knobs: List<Knob>,
    )

    /** The keyboard's own order, spelled out: the preview draws glyphs and geometry only. */
    private val order = "#@^+STKPWHRAO*EUfrpblgtsdz"

    /** How deep the keys come out on this screen, which is what a cap asks. */
    private fun keysShare(c: Config, landscape: Boolean): Float {
        val metrics = resources.displayMetrics
        val short = kotlin.math.min(metrics.widthPixels, metrics.heightPixels).toFloat()
        val long = kotlin.math.max(metrics.widthPixels, metrics.heightPixels).toFloat()
        val width = if (landscape) long else short
        val screen = if (landscape) short else long
        return Ring(order, c).deep(width, screen, landscape).first / screen
    }

    private val groups: List<Group> = listOf(
        Group(
            "Rings",
            "Nested bands per thumb, read by where the thumb is. Entering a live " +
                "cell takes it, and everything else is ground: a cell the order " +
                "has passed, a cell holding nothing, and the hub once you have " +
                "left it. So sliding out to the next ring erases the ring behind, " +
                "turning through a ring adds keys, and two cells far apart on one " +
                "ring are joined by a straight line through the middle.\n\n" +
                "A layout is the hub first, then the rings outward, separated by " +
                "spaces. A slot is one character, or several keys between slashes, " +
                "a dot is ground, and > is the upgrade. A band whose keys repeat " +
                "is turned half a slot, so it is staggered against the band inside " +
                "it and a run straight out from the hub lands on one of its cells. " +
                "A key in two slots is nothing special, because a key is a set of " +
                "cells.\n\n" +
                "A slot no walk can spend is drawn as a stud: a circle between the " +
                "keys of its ring and a hair further out, so a thumb turning along " +
                "the ring passes under it. That is where the upgrade and the plus " +
                "sit, one between each group of repeats, which is what leaves -T " +
                "and -S next to each other.",
            knobs = listOf(
                Text(
                    "The rings",
                    Side({ it.ringLeft }, { c, v -> c.ringLeft = v }),
                    Side({ it.ringRight }, { c, v -> c.ringRight = v }),
                    note = "the hub, then the rings outward",
                ),
                Switch(
                    "The modifier row", { it.showRow }, { c, v -> c.showRow = v },
                    "the keys under the rings, one to a slot. Off gives the depth " +
                        "it stands on back to the rings",
                    "They are off the rings because steno order cannot kill one: a " +
                        "key no walk can spend is a key no walk should have to " +
                        "reach. A tap arms one for the next stroke, and holding it " +
                        "puts it in the stroke in hand.",
                    knobs = listOf(
                        Text(
                            "The keys",
                            Side({ it.modsLeft }, { c, v -> c.modsLeft = v }),
                            Side({ it.modsRight }, { c, v -> c.modsRight = v }),
                        ),
                        Range(
                            "Row height", 0.04f, 0.3f, { it.rowHeight },
                            { c, v -> c.rowHeight = v }, unit = "of the bank",
                        ),
                    ),
                ),
                Text(
                    "The radii",
                    Side({ it.radiiLeft }, { c, v -> c.radiiLeft = v }),
                    Side({ it.radiiRight }, { c, v -> c.radiiRight = v }),
                    note = "where the bands meet, outward. One number more than there are bands",
                ),
                Text(
                    "The floats", Side({ it.floats }, { c, v -> c.floats = v }),
                    note = "the keys steno order cannot kill",
                    prose = "A walk may take a float at any point, which is what lets " +
                        "one sit on a ring among the keys it can never spend.",
                ),
                Text(
                    "The upgrade", Side({ it.upgrade }, { c, v -> c.upgrade = v }),
                    note = "pairs: what each key writes while the upgrade is on",
                    prose = "Entering its stud turns it over, so a stroke can hold one " +
                        "lifted key and one plain one, and a tap on the stud alone " +
                        "writes both keys. The pinky writes -D for -T and -Z for " +
                        "-S one at a time, so the row carries one bit over the " +
                        "whole stroke rather than a ring of its own.",
                ),
                Range(
                    "Tilt", -45f, 45f, { it.tilt }, { c, v -> c.tilt = v },
                    "how far the rings are turned", unit = "\u00b0",
                    prose = "A thumb sweeps an arc and the rings are already arcs, so " +
                        "this may buy nothing. The right bank turns the other way, " +
                        "because the two thumbs sweep mirrored arcs.",
                ),
            ),
        ),

        Group(
            "Strokes",
            "Lifting either thumb writes the stroke. A lift writes only when a key " +
                "has been taken since the last one, because keeping keys and asking " +
                "for a stroke are different things.\n\n" +
                "Ring 2 is the last of the walk and its keys repeat round it, so a " +
                "chord neither turns backwards out there nor comes back in. Those " +
                "two moves are free to mean something else, and each takes a key. " +
                "Taking a key is what says a move is the chord, so neither fires on " +
                "one.",
            knobs = listOf(
                Flag(
                    "Write on the first lift", { it.commitOnFirstLift },
                    { c, v -> c.commitOnFirstLift = v },
                    "the thumb still down keeps its keys",
                    "Spelling a run of words that share a right-hand shape therefore " +
                        "costs one right-hand motion, not one per word. Off waits " +
                        "for both thumbs.",
                ),
                Flag(
                    "One thumb walks both banks", { it.oneThumb }, { c, v -> c.oneThumb = v },
                    "a touch that arrives in the other field is handed over to it",
                    "The bank it leaves keeps what it took, so one hand can write a " +
                        "whole stroke, slowly. It happens only while the other " +
                        "thumb is up, so two hands never meet it.",
                ),
                Text(
                    "Armed keys",
                    Side({ it.stickyLeft }, { c, v -> c.stickyLeft = v }),
                    Side({ it.stickyRight }, { c, v -> c.stickyRight = v }),
                    note = "a lift that took only these arms them for the next stroke",
                    prose = "Lifting on them again sends them alone. * and + are not " +
                        "among them: a bare * is the undo stroke, and arming a key " +
                        "is not undoing anything, so a tap on it has to write it.",
                ),
                Pick(
                    "Turn back",
                    Side({ it.turnBackLeft.toString() },
                        { c, v -> c.turnBackLeft = v.firstOrNull() ?: Config.NONE }),
                    Side({ it.turnBackRight.toString() },
                        { c, v -> c.turnBackRight = v.firstOrNull() ?: Config.NONE }),
                    options = { candidates() },
                    note = "the key a turn back past a whole cell of the outer ring takes",
                    prose = "A turn back is a whole cell rather than a step, because " +
                        "one step back is how the stud behind a key is reached. The " +
                        "ground inside the ring runs all the way round, so the move " +
                        "can be made with no key held at all.",
                ),
                Pick(
                    "Pull in",
                    Side({ it.pullInLeft.toString() },
                        { c, v -> c.pullInLeft = v.firstOrNull() ?: Config.NONE }),
                    Side({ it.pullInRight.toString() },
                        { c, v -> c.pullInRight = v.firstOrNull() ?: Config.NONE }),
                    options = { candidates() },
                    note = "the key a pull back in off the outer ring takes",
                ),
                Flag(
                    "Draw the two moves", { it.showMoves }, { c, v -> c.showMoves = v },
                    "from where the thumb is to where each would fire",
                    "The hint asks the same function the move does, so it cannot " +
                        "promise a move the thumb cannot make. The row carries the " +
                        "same two marks under the keys they take.",
                ),
                Switch(
                    "Double taps", { it.doubleTaps }, { c, v -> c.doubleTaps = v },
                    "two quick taps add a key on top of the key under them",
                    "Tap tap on -T releases as -*T. The second tap still strokes as " +
                        "itself, and a quick tap's stroke waits out the window, " +
                        "which is what a second tap cancels.",
                    knobs = listOf(
                        Pick(
                            "The key",
                            Side({ it.doubleTapLeft.toString() },
                                { c, v -> c.doubleTapLeft = v.firstOrNull() ?: Config.NONE }),
                            Side({ it.doubleTapRight.toString() },
                                { c, v -> c.doubleTapRight = v.firstOrNull() ?: Config.NONE }),
                            options = { candidates() },
                        ),
                        Range(
                            "The window", 80f, 500f, { it.doubleTapMs.toFloat() },
                            { c, v -> c.doubleTapMs = v.toLong() }, unit = "ms",
                        ),
                    ),
                ),
                Switch(
                    "Long presses", { it.longPresses }, { c, v -> c.longPresses = v },
                    "a touch standing still on its cell adds a key",
                    "The other way to a modifier without a trip to the row. Off by " +
                        "default, now that the two moves off the outer ring reach " +
                        "the same modifiers without waiting.",
                    knobs = listOf(
                        Pick(
                            "The key",
                            Side({ it.longPressLeft.toString() },
                                { c, v -> c.longPressLeft = v.firstOrNull() ?: Config.NONE }),
                            Side({ it.longPressRight.toString() },
                                { c, v -> c.longPressRight = v.firstOrNull() ?: Config.NONE }),
                            options = { candidates() },
                        ),
                        Range(
                            "The wait", 120f, 900f, { it.longPressMs.toFloat() },
                            { c, v -> c.longPressMs = v.toLong() }, unit = "ms",
                        ),
                    ),
                ),
                Switch(
                    "The briefs", { it.briefs }, { c, v -> c.briefs = v },
                    "a shorter way to write what was just written",
                    "After every stroke the runtime reads the trailing words back " +
                        "through every dictionary that carries a reverse lookup. " +
                        "What reaches the screen is one line, and the briefs alone, " +
                        "because a stroke you have just made is not news.",
                    knobs = listOf(
                        Range(
                            "How many fit", 1f, 16f, { it.briefsMost.toFloat() },
                            { c, v -> c.briefsMost = v.toInt() }, unit = "briefs",
                        ),
                        Range(
                            "A quiet spell clears it", 5f, 300f,
                            { it.briefsIdleMs / 1000f },
                            { c, v -> c.briefsIdleMs = (v * 1000f).toLong() }, unit = "s",
                        ),
                    ),
                ),
            ),
        ),

        Group(
            "Buzzes",
            "A buzz when a key is taken says a key was taken, which is a thing you " +
                "can already see. The buzz that matters is on the change of cell, " +
                "because that is what turns a slide into something to feel instead " +
                "of something to read.\n\n" +
                "A voice is one short thing the motor can do. Where the phone has " +
                "composition primitives it is one of those; where it has none the " +
                "same name comes out as a pulse of that length, strength and count, " +
                "because rhythm and amplitude are what survive when pitch does " +
                "not. Only the voices this phone can actually make are offered, " +
                "and only the ones short enough for the occasion: a tick fires on " +
                "every cell a thumb crosses, and a stroke a few times a second at " +
                "most.\n\n" +
                "A phone has one motor, so a voice per thumb is not a left channel " +
                "and a right channel. It is two buzzes from the same motor, which " +
                "is why they have to differ in something the motor has.\n\n" +
                "Every chip plays itself, and every change plays: left, then right.",
            knobs = listOf(
                Switch(
                    "Haptics", { it.haptics }, { c, v -> c.haptics = v },
                    knobs = listOf(
                        Switch(
                            "On a change of cell", { it.tickOn }, { c, v -> c.tickOn = v },
                            "where the thumb is, as something to feel rather than read",
                            play = { demo(Buzz.TICK) },
                            knobs = voice(Buzz.TICK),
                        ),
                        Switch(
                            "On a key taken", { it.takeOn }, { c, v -> c.takeOn = v },
                            play = { demo(Buzz.TAKE) },
                            knobs = voice(Buzz.TAKE),
                        ),
                        Switch(
                            "On a stroke", { it.strokeOn }, { c, v -> c.strokeOn = v },
                            "the one worth feeling clearly",
                            play = { demo(Buzz.STROKE) },
                            knobs = voice(Buzz.STROKE),
                        ),
                        Range(
                            "Shortest gap between buzzes", 5f, 120f,
                            { it.hapticGapMs.toFloat() }, { c, v -> c.hapticGapMs = v.toLong() },
                            "or a walk across several cells is one long buzz",
                            unit = "ms", play = { run() },
                        ),
                    ),
                ),
            ),
        ),

        Group(
            "Keys",
            "The board has no hue. What a key is shows in the texture of its face: a " +
                "fine dither for the vowels, a coarse one for a modifier, a plain " +
                "face for the consonants, which are the many. Which is which is read " +
                "off the glyph, so a layout that moves a key does not have to say so " +
                "twice.\n\n" +
                "A key is a body rather than a slice of its ring: one width for every " +
                "ring, drawn along the middle of its slot with rounded ends, and " +
                "everything around it is ground. That ground is what a slide passes " +
                "through on its way to a further key, and the width, the length and " +
                "the taper are what set how much of it there is.",
            knobs = listOf(
                Pick(
                    "Theme", Side({ it.theme }, { c, v -> c.theme = v }),
                    options = { listOf("light", "dark", "system") },
                    note = "the ground and the ink. Everything else is one of them, faded",
                ),
                Range(
                    "Key width", 0.05f, 0.4f, { it.keyWidth }, { c, v -> c.keyWidth = v },
                    "one width for every ring", unit = "of the reach",
                ),
                Range(
                    "Key length", 0f, 1f, { it.keySpread }, { c, v -> c.keySpread = v },
                    "how much of its slot a key runs along: 0 is a circle",
                    unit = "of the slot",
                ),
                Range(
                    "Key ends", 0f, 1f, { it.keyEnds }, { c, v -> c.keyEnds = v },
                    "1 is a rounded end, and less tapers it to a point",
                    unit = "of the width",
                ),
                Range(
                    "Stud width", 0.2f, 1f, { it.studWidth }, { c, v -> c.studWidth = v },
                    "a stud's width, as a share of a key's", unit = "\u00d7",
                    prose = "A cell no walk can spend is drawn as a stud: a circle " +
                        "between the keys of its ring and a hair further out, so a " +
                        "thumb turning along the ring passes under it rather than " +
                        "through it.",
                ),
                Range(
                    "Stud stands out", 0f, 0.8f, { it.studOut }, { c, v -> c.studOut = v },
                    "out of its band's middle", unit = "of the band",
                ),
                Range(
                    "Roundness at the hub", 2f, 8f, { it.shapeHub },
                    { c, v -> c.shapeHub = v },
                    "2 is a circle, and larger is closer to a rectangle",
                    prose = "The exponent rises outward, so the middle stays round, " +
                        "where a thumb turns, and the outer rings take the corners " +
                        "of the bank, where the room is.",
                ),
                Range(
                    "Roundness at the rim", 2f, 8f, { it.shapeRim },
                    { c, v -> c.shapeRim = v },
                ),
                Switch(
                    "Labels", { it.showLabels }, { c, v -> c.showLabels = v },
                    knobs = listOf(
                        Range(
                            "Label size", 8f, 28f, { it.labelSp }, { c, v -> c.labelSp = v },
                            unit = "sp",
                        ),
                    ),
                ),
                Switch(
                    "Dither", { it.dither }, { c, v -> c.dither = v },
                    "how far apart the dots on a cell's face are. A dot's size " +
                        "follows its spacing",
                    "The dither thins out at the cell's rim and around its label, " +
                        "because a texture running under a letter is what makes the " +
                        "letter hard to read. What the fade changes is how many " +
                        "dots there are, not how big they are.",
                    knobs = listOf(
                        Range(
                            "The vowels", 0.008f, 0.05f, { it.grainFine },
                            { c, v -> c.grainFine = v }, unit = "of the bank",
                        ),
                        Range(
                            "The modifiers", 0.008f, 0.05f, { it.grainCoarse },
                            { c, v -> c.grainCoarse = v }, unit = "of the bank",
                        ),
                    ),
                ),
                Flag(
                    "Show cells already passed", { it.showDeadCells },
                    { c, v -> c.showDeadCells = v },
                    "sunk towards the ground, with the label struck through",
                ),
                Range(
                    "Thumb mark size", 0.008f, 0.06f, { it.markRadius },
                    { c, v -> c.markRadius = v }, "the dot that shows where the thumb is",
                    unit = "\u00d7 field",
                ),
            ),
        ),

        Group(
            "Fit",
            "The keyboard takes a band off the bottom, as any keyboard does. " +
                "Android describes the app's room with a top edge alone, so there is " +
                "no way to ask an app to lay out narrower and keep the banks full " +
                "height beside it.\n\n" +
                "Square banks are what a thumb's sweep wants: a ring stretched to a " +
                "tall bank answers the same sweep with different keys at different " +
                "angles. So the keys take the depth asked for or the depth the wider " +
                "bank can use, whichever is less, and a height asked for past that " +
                "shows as the value it comes out as.\n\n" +
                "A split that is not even leaves one field wider than its bank can " +
                "use. That slack sits between the two banks rather than round the " +
                "wider one, because each thumb reaches the edge it starts from.",
            fit = true,
            knobs = listOf(
                Range(
                    "Keyboard height", 0.2f, 0.8f, { it.keyboardHeight },
                    { c, v -> c.keyboardHeight = v },
                    "in portrait", unit = "of the screen",
                    cap = { keysShare(it, landscape = false) },
                ),
                Range(
                    "Field split", 0.1f, 0.9f, { it.fieldSplit }, { c, v -> c.fieldSplit = v },
                    "where the left field ends, in portrait", unit = "of the width",
                ),
                Flag(
                    "Square banks", { it.squareBanks }, { c, v -> c.squareBanks = v },
                    "and take only the depth that needs",
                ),
                Range(
                    "Ground round a bank", 0f, 0.3f, { it.bankPad }, { c, v -> c.bankPad = v },
                    unit = "of the field",
                ),
                Range(
                    "Bank pulled off its edge", 0f, 1f, { it.bankPull },
                    { c, v -> c.bankPull = v },
                    "0 holds it against the thumb's own edge, 1 against the middle",
                    unit = "of the slack",
                ),
                Switch(
                    "Preview over the app", { it.band }, { c, v -> c.band = v },
                    "a copy of the banks above the keys, taking no touches",
                    "Shrink the keys and a thumb crosses cells in less travel, which " +
                        "is faster. What it costs is legibility, and the copy over " +
                        "the app buys that back. It is drawn only while a thumb is " +
                        "down, because a band that arrives at full strength reads " +
                        "as a flash over whatever the app was showing.",
                    knobs = listOf(
                        Range(
                            "How deep", 0.1f, 0.5f, { it.previewHeight },
                            { c, v -> c.previewHeight = v }, unit = "of the screen",
                        ),
                    ),
                ),
                Flag(
                    "Banks on the edges in landscape", { it.landscapeSides },
                    { c, v -> c.landscapeSides = v },
                    "with the bar in the gap between them",
                ),
                Range(
                    "Landscape keyboard height", 0.3f, 0.95f, { it.landscapeHeight },
                    { c, v -> c.landscapeHeight = v }, unit = "of the screen",
                    cap = { keysShare(it, landscape = true) },
                ),
                Range(
                    "Landscape field width", 0.15f, 0.5f, { it.splitLandscape },
                    { c, v -> c.splitLandscape = v },
                    "each field's share, anchored to its own side", unit = "of the width",
                ),
            ),
        ),

        Group(
            "Touch",
            "Whether a hard press can be told from a light one is a question about " +
                "the hardware, so it goes on the screen rather than being guessed at.",
            knobs = listOf(
                Switch(
                    "Draw the touched area", { it.cloud }, { c, v -> c.cloud = v },
                    "every sample, at the size and shape the driver gives it",
                    knobs = listOf(
                        Range(
                            "Samples kept", 20f, 1200f, { it.cloudSamples.toFloat() },
                            { c, v -> c.cloudSamples = v.toInt() }, unit = "samples",
                        ),
                        Range(
                            "Touch fade", 0.02f, 0.5f, { it.cloudAlpha },
                            { c, v -> c.cloudAlpha = v }, unit = "\u00d7",
                        ),
                    ),
                ),
                Flag(
                    "Print the touch numbers", { it.touchReadout },
                    { c, v -> c.touchReadout = v },
                    "pressure, size and the two contact axes",
                ),
            ),
        ),
    )

    /** What an occasion sounds like: how hard, and in which voice per thumb. */
    private fun voice(on: Buzz): List<Knob> = listOf(
        Range(
            "How hard", 0.05f, 1f, { it.power(on) },
            { c, v ->
                when (on) {
                    Buzz.TICK -> c.tickPower = v
                    Buzz.TAKE -> c.takePower = v
                    Buzz.STROKE -> c.strokePower = v
                }
            },
            unit = "\u00d7", play = { demo(on) },
        ),
        Pick(
            "The voice",
            Side({ it.voice(on, Bank.LEFT) }, { c, v -> put(c, on, Bank.LEFT, v) }),
            Side({ it.voice(on, Bank.RIGHT) }, { c, v -> put(c, on, Bank.RIGHT, v) }),
            options = { buzzer?.voices(on) ?: emptyList() },
            chosen = { name, bank ->
                buzzer?.apply(config)
                buzzer?.sample(name, config.power(on), bank)
            },
        ),
    )

    /** Which of the twelve voice slots this is: the occasion, and the thumb. */
    private fun put(c: Config, on: Buzz, bank: Bank, name: String) {
        val left = bank == Bank.LEFT
        when (on) {
            Buzz.TICK -> if (left) c.tickLeft = name else c.tickRight = name
            Buzz.TAKE -> if (left) c.takeLeft = name else c.takeRight = name
            Buzz.STROKE -> if (left) c.strokeLeft = name else c.strokeRight = name
        }
    }

    // ---- the screen --------------------------------------------------------

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        // The screen says its own name in the header, and the room the title
        // bar takes is a row of knobs.
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        prefs = getSharedPreferences(StenoService.PREFS, MODE_PRIVATE)
        config = Prefs.load(prefs)
        buzzer = Haptics(this, config)

        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad(16), 0, pad(16), pad(24))
        }
        val keys = RingView(this, config, order, {}, embedded = true)
        preview = keys
        val glance = Fit(this, config, order)
        fit = glance

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        // The preview is pinned to the foot of the screen, and the foot of the
        // screen is where the system draws its own bar.
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bottom = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                insets.getInsets(android.view.WindowInsets.Type.systemBars()).bottom
            } else {
                @Suppress("DEPRECATION") insets.systemWindowInsetBottom
            }
            view.setPadding(0, 0, 0, bottom)
            insets
        }
        root.addView(header())
        root.addView(tabStrip())
        root.addView(
            ScrollView(this).apply { addView(page) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f - PREVIEW)
        )
        root.addView(glance)
        // The keyboard asks for its share of the screen, and this screen has
        // knobs on it as well, so it is given what is left rather than what it
        // asked for. It measures itself down to whatever it is handed.
        root.addView(
            keys,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, PREVIEW)
        )
        setContentView(root)
        open(0)
    }

    private fun pad(dp: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
    ).toInt()

    private fun header(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad(16), pad(12), pad(16), pad(4))
        }
        bar.addView(TextView(this).apply {
            text = "Radial steno"
            textSize = 20f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        bar.addView(prose(
            "The keyboard\n\n" +
                "Enable it in Settings, Languages and input. Changes here reach a " +
                "keyboard that is already open the next time it opens.\n\n" +
                "The keyboard at the foot of this screen is live: it follows every " +
                "change as it is made, and what it strokes goes nowhere.\n\n" +
                "Every value says its unit. A length is a fraction of the thing it " +
                "names, so it means the same on any screen, and ° is degrees " +
                "clockwise with -90 pointing straight up.\n\n" +
                "On this device the motor gives: ${buzzer?.report()}"
        ))
        bar.addView(CheckBox(this).apply {
            text = "preview"
            isChecked = true
            setOnCheckedChangeListener { _, on ->
                wanted = on
                previewRoom()
            }
        })
        bar.addView(chip("defaults", false) {
            Prefs.save(Config(), prefs)
            recreate()
        })
        return bar
    }

    private fun tabStrip(): View {
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(pad(12), 0, pad(12), pad(8))
        }
        tabs = strip
        groups.forEachIndexed { index, group ->
            strip.addView(chip(group.name, index == at) { open(index) })
        }
        // What the open group is about, which is a paragraph and belongs
        // behind the same mark every other paragraph is behind.
        strip.addView(chip("?", false) { say(groups[at].prose) })
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(strip)
        }
    }

    /** Show one group. The tabs are what the column is, so it is rebuilt whole. */
    private fun open(index: Int) {
        at = index
        shown.clear()
        page.removeAllViews()
        tabs?.let { strip ->
            for (i in groups.indices) mark(strip.getChildAt(i) as TextView, i == index)
        }
        for (knob in groups[index].knobs) page.addView(row(knob))
        previewRoom()
        refresh()
    }

    /**
     * Who gets the foot of the screen.
     *
     * The live keyboard, unless it was turned off, and then the two screens at
     * a glance in its place, for the group that is about them. A text field
     * being edited takes the room from both, because the soft keyboard wants
     * it.
     */
    private fun previewRoom() {
        val keys = wanted && !editing
        preview?.visibility = if (keys) View.VISIBLE else View.GONE
        fit?.visibility = if (groups[at].fit && !keys && !editing) View.VISIBLE else View.GONE
    }

    private fun refresh() {
        preview?.apply(config)
        fit?.apply(config)
        // A knob that shows a value another knob bounds has to be told when that
        // other knob moves, and which those are is not worth tracking.
        for (sync in shown) sync()
    }

    private fun keep() {
        Prefs.save(config, prefs)
        refresh()
    }

    /** What an occasion feels like now: the left voice, then the right. */
    private fun demo(on: Buzz) {
        val motor = buzzer ?: return
        motor.apply(config)
        motor.sample(config.voice(on, Bank.LEFT), config.power(on), Bank.LEFT)
        page.postDelayed(
            { motor.sample(config.voice(on, Bank.RIGHT), config.power(on), Bank.RIGHT) }, 240L
        )
    }

    /** A run of ticks at the gap being set, which is what the gap is about. */
    private fun run() {
        val motor = buzzer ?: return
        motor.apply(config)
        repeat(6) { beat ->
            page.postDelayed(
                { motor.sample(config.voice(Buzz.TICK, Bank.LEFT), config.tickPower, Bank.LEFT) },
                beat * config.hapticGapMs,
            )
        }
    }

    // ---- the rows ----------------------------------------------------------

    private fun row(knob: Knob, under: () -> Boolean = { true }): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, pad(8), 0, pad(8))
        }
        val title = TextView(this).apply {
            text = knob.label
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val value = TextView(this).apply {
            textSize = 13f
            alpha = 0.75f
        }
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (knob is Switch) {
            line.addView(CheckBox(this).apply {
                isChecked = knob.get(config)
                setOnCheckedChangeListener { _, on ->
                    knob.set(config, on)
                    keep()
                    knob.play?.invoke()
                }
            })
        }
        line.addView(title)
        line.addView(value)
        if (knob.prose.isNotEmpty()) line.addView(ask("${knob.label}\n\n${knob.prose}"))
        box.addView(line)
        if (knob.note.isNotEmpty()) box.addView(note(knob.note))

        when (knob) {
            is Range -> box.addView(slider(knob, value))
            is Flag -> {
                value.visibility = View.GONE
                // Before the label, where a switch's own box is: a flag reads
                // the same whether or not it has knobs under it.
                line.addView(CheckBox(this).apply {
                    isChecked = knob.get(config)
                    setOnCheckedChangeListener { _, on ->
                        knob.set(config, on)
                        keep()
                        knob.play?.invoke()
                    }
                }, 0)
            }

            is Text -> {
                value.visibility = View.GONE
                box.addView(sides(knob.left, knob.right) { side -> field(side) })
            }

            is Pick -> {
                value.visibility = View.GONE
                box.addView(sides(knob.left, knob.right, wide = true) { side ->
                    picker(knob, side)
                })
            }

            is Switch -> {
                value.visibility = View.GONE
                val column = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(pad(20), 0, 0, 0)
                }
                val on = { under() && knob.get(config) }
                for (each in knob.knobs) column.addView(row(each, on))
                box.addView(column)
                // Whether what is under a flag can be touched is a thing to
                // see, so it is asked again whenever anything moves.
                fun sync() {
                    // Greyed for its own flag rather than for an ancestor's,
                    // which is already greying the whole of this.
                    column.alpha = if (knob.get(config)) 1f else 0.4f
                    enable(column, on())
                }
                shown.add { sync() }
                sync()
            }
        }
        return box
    }

    /**
     * Put a stop to touching what a flag has turned off.
     *
     * The `?` is the exception: what a flag does is the thing you would want to
     * read before turning it back on.
     */
    private fun enable(view: View, on: Boolean) {
        if (view.tag == PROSE) return
        view.isEnabled = on
        if (view is ViewGroup) for (i in 0 until view.childCount) enable(view.getChildAt(i), on)
    }

    /**
     * One editor, or one per bank under an L and an R.
     *
     * Two fields fit beside each other and a row of chips does not, so a strip
     * of choices takes the whole width and the other one goes under it.
     */
    private fun <T> sides(
        left: Side<T>, right: Side<T>?, wide: Boolean = false, one: (Side<T>) -> View,
    ): View {
        if (right == null) return one(left)
        val pair = LinearLayout(this).apply {
            orientation = if (wide) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        }
        for ((mark, side) in listOf("L" to left, "R" to right)) {
            pair.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = if (wide) LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ) else LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
                addView(TextView(this@SettingsActivity).apply {
                    text = mark
                    textSize = 11f
                    alpha = 0.5f
                    setPadding(0, 0, pad(8), 0)
                })
                addView(one(side).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                    )
                })
            })
        }
        return pair
    }

    /**
     * A slider, and the value it comes out as.
     *
     * Where another parameter bounds this one, the run past the bound is shaded
     * and the value says what it became, because a number on the screen that is
     * not the number in force is worse than no number at all.
     */
    private fun slider(knob: Range, value: TextView): View {
        val bar = Bounded(this).apply {
            max = 1000
            progress = (((knob.get(config) - knob.min) / (knob.max - knob.min)) * 1000).toInt()
        }
        fun sync() {
            val asked = knob.get(config)
            val got = knob.cap?.invoke(config)
            bar.cap = got?.let { (it - knob.min) / (knob.max - knob.min) }?.takeIf { it < 1f }
            val unit = if (knob.unit.isEmpty()) "" else " ${knob.unit}"
            value.text = if (got != null && got < asked - EPSILON) {
                "${show(asked)} → ${show(got)}$unit"
            } else "${show(asked)}$unit"
            bar.invalidate()
        }
        shown.add { sync() }
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar?, to: Int, byUser: Boolean) {
                knob.set(config, knob.min + (knob.max - knob.min) * to / 1000f)
                // Mid-drag too, which is what makes the keyboard below live.
                refresh()
            }

            override fun onStartTrackingTouch(seek: SeekBar?) = Unit
            override fun onStopTrackingTouch(seek: SeekBar?) {
                keep()
                knob.play?.invoke()
            }
        })
        sync()
        return bar
    }

    /** A layout or a set of keys, applied as it is typed. */
    private fun field(side: Side<String>): View = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        textSize = 14f
        setSingleLine(true)
        setText(side.get(config))
        addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(text: Editable?) {
                side.set(config, text.toString())
                keep()
            }

            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        })
        setOnFocusChangeListener { _, focused ->
            // The soft keyboard wants the room the preview is in.
            editing = focused
            previewRoom()
        }
    }

    /**
     * The whole of a closed choice, one chip each, and a tap plays it where it
     * has a sound.
     *
     * A free text field was offering a thousand answers to a question with
     * eight, and a spelling mistake in it was a setting that quietly did
     * nothing.
     */
    private fun picker(knob: Pick, side: Side<String>): View {
        val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val bank = if (side === knob.right) Bank.RIGHT else Bank.LEFT
        fun draw() {
            strip.removeAllViews()
            // What is set is on the strip whether or not it is on offer: a
            // phone that cannot make a voice still says which one it was asked
            // for, and still plays what that voice comes to here.
            val offered = knob.options(config)
            val chosen = side.get(config)
            for (option in if (chosen in offered) offered else offered + chosen) {
                val on = option == side.get(config)
                strip.addView(chip(if (option.isBlank()) "—" else option, on) {
                    side.set(config, option)
                    keep()
                    knob.chosen?.invoke(option, bank)
                    draw()
                })
            }
        }
        draw()
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(strip)
        }
    }

    /** The keys a gesture or a tap can stand for: none, and the ones no walk spends. */
    private fun candidates(): List<String> {
        val mods = (config.modsLeft + config.modsRight + config.floats)
            .filter { !it.isWhitespace() && it.code in 33..126 }
        return listOf(Config.NONE.toString()) + mods.toSortedSet().map { it.toString() }
    }

    // ---- the small parts ---------------------------------------------------

    private fun note(what: String) = TextView(this).apply {
        text = what
        textSize = 12f
        alpha = 0.6f
        setPadding(0, pad(2), 0, pad(4))
    }

    /** The `?` that opens the prose, which is worth reading once. */
    private fun ask(words: String) = TextView(this).apply {
        tag = PROSE
        text = "?"
        textSize = 14f
        alpha = 0.5f
        setPadding(pad(12), pad(6), pad(12), pad(6))
        setOnClickListener { say(words) }
    }

    /** The same, where it stands among chips. */
    private fun prose(words: String) = chip("?", false) { say(words) }

    private fun say(words: String) {
        AlertDialog.Builder(this).setMessage(words).setPositiveButton("ok", null).show()
    }

    private fun chip(what: String, on: Boolean, tap: () -> Unit) = TextView(this).apply {
        text = what
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(pad(10), pad(6), pad(10), pad(6))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { rightMargin = pad(6) }
        mark(this, on)
        setOnClickListener { tap() }
    }

    private fun mark(chip: TextView, on: Boolean) {
        chip.setBackgroundColor(if (on) Color.rgb(24, 24, 24) else Color.rgb(238, 238, 238))
        chip.setTextColor(if (on) Color.WHITE else Color.rgb(24, 24, 24))
    }

    private fun show(value: Float): String = when {
        kotlin.math.abs(value) >= 10f -> value.toInt().toString()
        kotlin.math.abs(value) >= 1f -> String.format("%.1f", value)
        else -> String.format("%.2f", value)
    }

    /**
     * A slider that says where its value stops taking effect.
     *
     * The stored value is the one asked for, not the one that fits, so turning
     * off whatever bounds it gives it back. The shading is that bound.
     */
    private class Bounded(context: android.content.Context) : SeekBar(context) {
        /** Where on the track the value stops mattering, or null for nowhere. */
        var cap: Float? = null

        private val shade = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val stop = cap?.coerceIn(0f, 1f) ?: return
            val run = (width - paddingLeft - paddingRight).toFloat()
            shade.color = Color.argb(40, 0, 0, 0)
            canvas.drawRect(
                paddingLeft + run * stop, 0f, (width - paddingRight).toFloat(),
                height.toFloat(), shade,
            )
        }
    }

    private companion object {
        const val EPSILON = 1e-4f

        /** What a `?` is marked with, since a flag never greys one out. */
        const val PROSE = "prose"

        /**
         * The share of what is left over that the preview keyboard is given.
         *
         * It asks for its share of the screen, and this screen has knobs on it
         * as well, so it is handed what is here instead and measures itself
         * down to that.
         */
        const val PREVIEW = 0.42f
    }
}
