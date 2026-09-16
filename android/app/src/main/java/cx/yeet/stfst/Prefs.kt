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
    private const val LAYOUT = 6

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
        size("tilt", { it.tilt }, { c, v -> c.tilt = v }),
        text("stickyLeft", { it.stickyLeft }, { c, v -> c.stickyLeft = v }, shipped = true),
        text("stickyRight", { it.stickyRight }, { c, v -> c.stickyRight = v }, shipped = true),
        letter("doubleTapLeft", { it.doubleTapLeft }, { c, v -> c.doubleTapLeft = v }),
        letter("doubleTapRight", { it.doubleTapRight }, { c, v -> c.doubleTapRight = v }),
        span("doubleTapMs", { it.doubleTapMs }, { c, v -> c.doubleTapMs = v }),
        flag("commitOnFirstLift", { it.commitOnFirstLift }, { c, v -> c.commitOnFirstLift = v }),
        flag("haptics", { it.haptics }, { c, v -> c.haptics = v }),
        flag("hapticTicks", { it.hapticTicks }, { c, v -> c.hapticTicks = v }),
        flag("hapticSplit", { it.hapticSplit }, { c, v -> c.hapticSplit = v }),
        size("hapticStrength", { it.hapticStrength }, { c, v -> c.hapticStrength = v }),
        span("hapticGapMs", { it.hapticGapMs }, { c, v -> c.hapticGapMs = v }),
        flag("showDeadCells", { it.showDeadCells }, { c, v -> c.showDeadCells = v }),
        flag("showLabels", { it.showLabels }, { c, v -> c.showLabels = v }),
        flag("cloud", { it.cloud }, { c, v -> c.cloud = v }),
        count("cloudSamples", { it.cloudSamples }, { c, v -> c.cloudSamples = v }),
        size("cloudAlpha", { it.cloudAlpha }, { c, v -> c.cloudAlpha = v }),
        flag("touchReadout", { it.touchReadout }, { c, v -> c.touchReadout = v }),
        flag("oneThumb", { it.oneThumb }, { c, v -> c.oneThumb = v }),
        size("fieldSplit", { it.fieldSplit }, { c, v -> c.fieldSplit = v }),
        flag("landscapeSides", { it.landscapeSides }, { c, v -> c.landscapeSides = v }),
        size("splitLandscape", { it.splitLandscape }, { c, v -> c.splitLandscape = v }),
        size("landscapeHeight", { it.landscapeHeight }, { c, v -> c.landscapeHeight = v }),
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
