package cx.yeet.stfst

import android.content.SharedPreferences

/**
 * Config in and out of storage. Its own file because a parameter set and a
 * storage format are different things, and only one of them needs a device.
 */
object Prefs {

    /**
     * Bumped whenever the shipped grids change shape.
     *
     * A saved grid is the user's own layout and outlives an upgrade, but one
     * saved before floats and hole rings existed is the old default rather than
     * a choice, and keeping it would hide the new one behind a settings screen.
     */
    private const val LAYOUT = 3

    fun save(config: Config, prefs: SharedPreferences) = with(config) {
        prefs.edit().apply {
            putInt("layout", LAYOUT)
            putString("combLeft", combLeft)
            putString("combRight", combRight)
            putFloat("tilt", tilt)
            putFloat("combFill", combFill)
            putFloat("combAt", combAt)
            putBoolean("combHole", combHole)
            putString("stickyLeft", stickyLeft)
            putString("stickyRight", stickyRight)
            putString("doubleTapLeft", doubleTapLeft.toString())
            putString("doubleTapRight", doubleTapRight.toString())
            putLong("doubleTapMs", doubleTapMs)
            putLong("stallMs", stallMs)
            putBoolean("commitOnFirstLift", commitOnFirstLift)
            putBoolean("haptics", haptics)
            putBoolean("hapticTicks", hapticTicks)
            putBoolean("hapticSplit", hapticSplit)
            putFloat("hapticStrength", hapticStrength)
            putLong("hapticGapMs", hapticGapMs)
            putBoolean("showDeadCells", showDeadCells)
            putBoolean("showLabels", showLabels)
            putBoolean("showChain", showChain)
            putBoolean("showPortals", showPortals)
            putBoolean("cloud", cloud)
            putInt("cloudSamples", cloudSamples)
            putFloat("cloudAlpha", cloudAlpha)
            putBoolean("touchReadout", touchReadout)
            putFloat("fieldSplit", fieldSplit)
            putBoolean("landscapeSides", landscapeSides)
            putFloat("splitLandscape", splitLandscape)
            putFloat("landscapeHeight", landscapeHeight)
            putBoolean("showLog", showLog)
            putFloat("markRadius", markRadius)
            putFloat("labelSp", labelSp)
            putFloat("keyboardHeight", keyboardHeight)
        }.apply()
    }

    fun load(prefs: SharedPreferences): Config {
        val d = Config()
        val fresh = prefs.getInt("layout", 0) != LAYOUT
        return Config(
            combLeft = if (fresh) d.combLeft else prefs.getString("combLeft", d.combLeft)!!,
            combRight = if (fresh) d.combRight else prefs.getString("combRight", d.combRight)!!,
            tilt = prefs.getFloat("tilt", d.tilt),
            combFill = prefs.getFloat("combFill", d.combFill),
            combAt = prefs.getFloat("combAt", d.combAt),
            combHole = prefs.getBoolean("combHole", d.combHole),
            stickyLeft = prefs.getString("stickyLeft", d.stickyLeft)!!,
            stickyRight = prefs.getString("stickyRight", d.stickyRight)!!,
            doubleTapLeft = prefs.getString("doubleTapLeft", null)?.firstOrNull()
                ?: d.doubleTapLeft,
            doubleTapRight = prefs.getString("doubleTapRight", null)?.firstOrNull()
                ?: d.doubleTapRight,
            doubleTapMs = prefs.getLong("doubleTapMs", d.doubleTapMs),
            stallMs = prefs.getLong("stallMs", d.stallMs),
            commitOnFirstLift = prefs.getBoolean("commitOnFirstLift", d.commitOnFirstLift),
            haptics = prefs.getBoolean("haptics", d.haptics),
            hapticTicks = prefs.getBoolean("hapticTicks", d.hapticTicks),
            hapticSplit = prefs.getBoolean("hapticSplit", d.hapticSplit),
            hapticStrength = prefs.getFloat("hapticStrength", d.hapticStrength),
            hapticGapMs = prefs.getLong("hapticGapMs", d.hapticGapMs),
            showDeadCells = prefs.getBoolean("showDeadCells", d.showDeadCells),
            showLabels = prefs.getBoolean("showLabels", d.showLabels),
            showChain = prefs.getBoolean("showChain", d.showChain),
            showPortals = prefs.getBoolean("showPortals", d.showPortals),
            cloud = prefs.getBoolean("cloud", d.cloud),
            cloudSamples = prefs.getInt("cloudSamples", d.cloudSamples),
            cloudAlpha = prefs.getFloat("cloudAlpha", d.cloudAlpha),
            touchReadout = prefs.getBoolean("touchReadout", d.touchReadout),
            fieldSplit = prefs.getFloat("fieldSplit", d.fieldSplit),
            landscapeSides = prefs.getBoolean("landscapeSides", d.landscapeSides),
            splitLandscape = prefs.getFloat("splitLandscape", d.splitLandscape),
            landscapeHeight = prefs.getFloat("landscapeHeight", d.landscapeHeight),
            showLog = prefs.getBoolean("showLog", d.showLog),
            markRadius = prefs.getFloat("markRadius", d.markRadius),
            labelSp = prefs.getFloat("labelSp", d.labelSp),
            keyboardHeight = prefs.getFloat("keyboardHeight", d.keyboardHeight),
        )
    }
}
