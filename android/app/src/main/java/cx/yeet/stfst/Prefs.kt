package cx.yeet.stfst

import android.content.SharedPreferences

/**
 * Config in and out of storage. Its own file because a parameter set and a
 * storage format are different things, and only one of them needs a device.
 *
 * One slot per parameter, naming its key once for both directions, so saving
 * and loading cannot disagree on where a value lives.
 */
object Prefs {

    /**
     * Bumped whenever the shipped layout changes: the rings, the radii, or
     * which keys a lift can arm.
     *
     * A saved value is the user's own choice and outlives an upgrade, but one
     * saved before the rings existed is the old default rather than a choice,
     * and keeping it would hide the new one behind a settings screen.
     */
    private const val LAYOUT = 8

    private class Slot<T>(
        val key: String,
        val get: (Config) -> T,
        val set: (Config, T) -> Unit,
        val put: SharedPreferences.Editor.(String, T) -> Any,
        val read: SharedPreferences.(String, T) -> T,
        /** A value saved before the shipped layout changed is not loaded. */
        val shipped: Boolean = false,
    ) {
        fun save(config: Config, out: SharedPreferences.Editor) = out.put(key, get(config))
        fun load(config: Config, stored: SharedPreferences, stale: Boolean) {
            if (shipped && stale) return
            set(config, stored.read(key, get(config)))
        }
    }

    private fun text(
        key: String, get: (Config) -> String, set: (Config, String) -> Unit,
        shipped: Boolean = false,
    ) = Slot(key, get, set, SharedPreferences.Editor::putString,
        { k, d -> getString(k, d) ?: d }, shipped)

    private fun letter(key: String, get: (Config) -> Char, set: (Config, Char) -> Unit) =
        Slot(key, get, set, { k, v -> putString(k, v.toString()) },
            { k, d -> getString(k, null)?.firstOrNull() ?: d })

    private fun flag(key: String, get: (Config) -> Boolean, set: (Config, Boolean) -> Unit) =
        Slot(key, get, set, SharedPreferences.Editor::putBoolean, SharedPreferences::getBoolean)

    private fun size(key: String, get: (Config) -> Float, set: (Config, Float) -> Unit) =
        Slot(key, get, set, SharedPreferences.Editor::putFloat, SharedPreferences::getFloat)

    private fun span(key: String, get: (Config) -> Long, set: (Config, Long) -> Unit) =
        Slot(key, get, set, SharedPreferences.Editor::putLong, SharedPreferences::getLong)

    private fun count(key: String, get: (Config) -> Int, set: (Config, Int) -> Unit) =
        Slot(key, get, set, SharedPreferences.Editor::putInt, SharedPreferences::getInt)

    private val slots: List<Slot<*>> = listOf(
        text("ringLeft", { it.ringLeft }, { c, v -> c.ringLeft = v }, shipped = true),
        text("ringRight", { it.ringRight }, { c, v -> c.ringRight = v }, shipped = true),
        text("radiiLeft", { it.radiiLeft }, { c, v -> c.radiiLeft = v }, shipped = true),
        text("radiiRight", { it.radiiRight }, { c, v -> c.radiiRight = v }, shipped = true),
        text("modsLeft", { it.modsLeft }, { c, v -> c.modsLeft = v }, shipped = true),
        text("modsRight", { it.modsRight }, { c, v -> c.modsRight = v }, shipped = true),
        text("floats", { it.floats }, { c, v -> c.floats = v }, shipped = true),
        text("upgrade", { it.upgrade }, { c, v -> c.upgrade = v }, shipped = true),
        text("deadLeft", { it.deadLeft }, { c, v -> c.deadLeft = v }, shipped = true),
        text("deadRight", { it.deadRight }, { c, v -> c.deadRight = v }, shipped = true),
        letter("longPressLeft", { it.longPressLeft }, { c, v -> c.longPressLeft = v }),
        letter("longPressRight", { it.longPressRight }, { c, v -> c.longPressRight = v }),
        span("longPressMs", { it.longPressMs }, { c, v -> c.longPressMs = v }),
        text("longPressFrom", { it.longPressFrom }, { c, v -> c.longPressFrom = v }),
        flag("tapSnap", { it.tapSnap }, { c, v -> c.tapSnap = v }),
        size("studWidth", { it.studWidth }, { c, v -> c.studWidth = v }),
        size("studOut", { it.studOut }, { c, v -> c.studOut = v }),
        flag("squareBanks", { it.squareBanks }, { c, v -> c.squareBanks = v }),
        size("rowHeight", { it.rowHeight }, { c, v -> c.rowHeight = v }),
        size("shapeHub", { it.shapeHub }, { c, v -> c.shapeHub = v }),
        size("shapeRim", { it.shapeRim }, { c, v -> c.shapeRim = v }),
        size("tilt", { it.tilt }, { c, v -> c.tilt = v }),
        text("stickyLeft", { it.stickyLeft }, { c, v -> c.stickyLeft = v }, shipped = true),
        text("stickyRight", { it.stickyRight }, { c, v -> c.stickyRight = v }, shipped = true),
        letter("doubleTapLeft", { it.doubleTapLeft }, { c, v -> c.doubleTapLeft = v }),
        letter("doubleTapRight", { it.doubleTapRight }, { c, v -> c.doubleTapRight = v }),
        span("doubleTapMs", { it.doubleTapMs }, { c, v -> c.doubleTapMs = v }),
        flag("doubleTaps", { it.doubleTaps }, { c, v -> c.doubleTaps = v }),
        flag("longPresses", { it.longPresses }, { c, v -> c.longPresses = v }),
        flag("commitOnFirstLift", { it.commitOnFirstLift }, { c, v -> c.commitOnFirstLift = v }),
        flag("haptics", { it.haptics }, { c, v -> c.haptics = v }),
        flag("tickOn", { it.tickOn }, { c, v -> c.tickOn = v }),
        size("tickPower", { it.tickPower }, { c, v -> c.tickPower = v }),
        text("tickLeft", { it.tickLeft }, { c, v -> c.tickLeft = v }),
        text("tickRight", { it.tickRight }, { c, v -> c.tickRight = v }),
        flag("takeOn", { it.takeOn }, { c, v -> c.takeOn = v }),
        size("takePower", { it.takePower }, { c, v -> c.takePower = v }),
        text("takeLeft", { it.takeLeft }, { c, v -> c.takeLeft = v }),
        text("takeRight", { it.takeRight }, { c, v -> c.takeRight = v }),
        flag("strokeOn", { it.strokeOn }, { c, v -> c.strokeOn = v }),
        size("strokePower", { it.strokePower }, { c, v -> c.strokePower = v }),
        text("strokeLeft", { it.strokeLeft }, { c, v -> c.strokeLeft = v }),
        text("strokeRight", { it.strokeRight }, { c, v -> c.strokeRight = v }),
        span("hapticGapMs", { it.hapticGapMs }, { c, v -> c.hapticGapMs = v }),
        flag("showDeadCells", { it.showDeadCells }, { c, v -> c.showDeadCells = v }),
        flag("showLabels", { it.showLabels }, { c, v -> c.showLabels = v }),
        text("theme", { it.theme }, { c, v -> c.theme = v }),
        flag("dither", { it.dither }, { c, v -> c.dither = v }),
        size("grainFine", { it.grainFine }, { c, v -> c.grainFine = v }),
        size("grainCoarse", { it.grainCoarse }, { c, v -> c.grainCoarse = v }),
        flag("showRow", { it.showRow }, { c, v -> c.showRow = v }),
        flag("briefs", { it.briefs }, { c, v -> c.briefs = v }),
        count("briefsMost", { it.briefsMost }, { c, v -> c.briefsMost = v }),
        span("briefsIdleMs", { it.briefsIdleMs }, { c, v -> c.briefsIdleMs = v }),
        flag("hideWithMachine", { it.hideWithMachine }, { c, v -> c.hideWithMachine = v }),
        text("serialMachine", { it.serialMachine }, { c, v -> c.serialMachine = v }),
        flag("cloud", { it.cloud }, { c, v -> c.cloud = v }),
        count("cloudSamples", { it.cloudSamples }, { c, v -> c.cloudSamples = v }),
        size("cloudAlpha", { it.cloudAlpha }, { c, v -> c.cloudAlpha = v }),
        flag("touchReadout", { it.touchReadout }, { c, v -> c.touchReadout = v }),
        text("crossMiddle", { it.crossMiddle }, { c, v -> c.crossMiddle = v }),
        size("fieldSplit", { it.fieldSplit }, { c, v -> c.fieldSplit = v }),
        size("bankPad", { it.bankPad }, { c, v -> c.bankPad = v }),
        size("bankPull", { it.bankPull }, { c, v -> c.bankPull = v }),
        flag("landscapeSides", { it.landscapeSides }, { c, v -> c.landscapeSides = v }),
        size("splitLandscape", { it.splitLandscape }, { c, v -> c.splitLandscape = v }),
        size("landscapeHeight", { it.landscapeHeight }, { c, v -> c.landscapeHeight = v }),
        flag("band", { it.band }, { c, v -> c.band = v }),
        size("previewHeight", { it.previewHeight }, { c, v -> c.previewHeight = v }),
        size("keyWidth", { it.keyWidth }, { c, v -> c.keyWidth = v }),
        size("keySpread", { it.keySpread }, { c, v -> c.keySpread = v }),
        size("keyEnds", { it.keyEnds }, { c, v -> c.keyEnds = v }),
        size("markRadius", { it.markRadius }, { c, v -> c.markRadius = v }),
        size("labelSp", { it.labelSp }, { c, v -> c.labelSp = v }),
        size("keyboardHeight", { it.keyboardHeight }, { c, v -> c.keyboardHeight = v }),
    )

    fun save(config: Config, prefs: SharedPreferences) {
        val out = prefs.edit()
        out.putInt("layout", LAYOUT)
        for (slot in slots) slot.save(config, out)
        out.apply()
    }

    fun load(prefs: SharedPreferences): Config {
        val stale = prefs.getInt("layout", 0) != LAYOUT
        val config = Config()
        for (slot in slots) slot.load(config, prefs, stale)
        return config
    }
}
