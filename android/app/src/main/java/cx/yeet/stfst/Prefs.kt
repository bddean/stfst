package cx.yeet.stfst

import android.content.SharedPreferences

/**
 * Config in and out of storage. Its own file because a parameter set and a
 * storage format are different things, and only one of them needs a device.
 */
object Prefs {

    fun save(config: Config, prefs: SharedPreferences) = with(config) {
        prefs.edit().apply {
            putString("splitKey", splitKey.toString())
            putString("farLeft", farLeft)
            putString("farRight", farRight)
            putString("nearLeft", nearLeft)
            putString("nearRight", nearRight)
            putBoolean("spread", spread)
            putInt("slotsFar", slotsFar)
            putInt("slotsNear", slotsNear)
            putInt("entryCols", entryCols)
            putBoolean("entryCentral", entryCentral)
            putFloat("startFarLeft", startFarLeft)
            putFloat("startNearLeft", startNearLeft)
            putFloat("startFarRight", startFarRight)
            putFloat("startNearRight", startNearRight)
            putFloat("minSlide", minSlide)
            putFloat("nearReach", nearReach)
            putFloat("farReach", farReach)
            putFloat("ringSplitAt", ringSplitAt)
            putLong("settleMs", settleMs)
            putFloat("turnDegrees", turnDegrees)
            putFloat("snapDegrees", snapDegrees)
            putFloat("hysteresisDegrees", hysteresisDegrees)
            putLong("stallMs", stallMs)
            putFloat("homeAt", homeAt)
            putBoolean("commitOnFirstLift", commitOnFirstLift)
            putBoolean("longPress", longPress)
            putLong("longPressMs", longPressMs)
            putBoolean("haptics", haptics)
            putBoolean("hapticTicks", hapticTicks)
            putBoolean("hapticSplit", hapticSplit)
            putFloat("hapticStrength", hapticStrength)
            putLong("hapticGapMs", hapticGapMs)
            putBoolean("showDeadSlots", showDeadSlots)
            putBoolean("showLabels", showLabels)
            putFloat("sectorStep", sectorStep)
            putFloat("farEdge", farEdge)
            putBoolean("mirror", mirror)
            putFloat("mirrorAt", mirrorAt)
            putFloat("mirrorScale", mirrorScale)
            putFloat("mirrorDim", mirrorDim)
            putBoolean("cloud", cloud)
            putInt("cloudSamples", cloudSamples)
            putFloat("cloudAlpha", cloudAlpha)
            putBoolean("touchReadout", touchReadout)
            putFloat("fieldSplit", fieldSplit)
            putFloat("markRadius", markRadius)
            putFloat("labelSp", labelSp)
            putFloat("keyboardHeight", keyboardHeight)
        }.apply()
    }

    fun load(prefs: SharedPreferences): Config {
        val d = Config()
        return Config(
            splitKey = prefs.getString("splitKey", null)?.firstOrNull() ?: d.splitKey,
            farLeft = prefs.getString("farLeft", d.farLeft)!!,
            farRight = prefs.getString("farRight", d.farRight)!!,
            nearLeft = prefs.getString("nearLeft", d.nearLeft)!!,
            nearRight = prefs.getString("nearRight", d.nearRight)!!,
            spread = prefs.getBoolean("spread", d.spread),
            slotsFar = prefs.getInt("slotsFar", d.slotsFar),
            slotsNear = prefs.getInt("slotsNear", d.slotsNear),
            entryCols = prefs.getInt("entryCols", d.entryCols),
            entryCentral = prefs.getBoolean("entryCentral", d.entryCentral),
            startFarLeft = prefs.getFloat("startFarLeft", d.startFarLeft),
            startNearLeft = prefs.getFloat("startNearLeft", d.startNearLeft),
            startFarRight = prefs.getFloat("startFarRight", d.startFarRight),
            startNearRight = prefs.getFloat("startNearRight", d.startNearRight),
            minSlide = prefs.getFloat("minSlide", d.minSlide),
            nearReach = prefs.getFloat("nearReach", d.nearReach),
            farReach = prefs.getFloat("farReach", d.farReach),
            ringSplitAt = prefs.getFloat("ringSplitAt", d.ringSplitAt),
            settleMs = prefs.getLong("settleMs", d.settleMs),
            turnDegrees = prefs.getFloat("turnDegrees", d.turnDegrees),
            snapDegrees = prefs.getFloat("snapDegrees", d.snapDegrees),
            hysteresisDegrees = prefs.getFloat("hysteresisDegrees", d.hysteresisDegrees),
            stallMs = prefs.getLong("stallMs", d.stallMs),
            homeAt = prefs.getFloat("homeAt", d.homeAt),
            commitOnFirstLift = prefs.getBoolean("commitOnFirstLift", d.commitOnFirstLift),
            longPress = prefs.getBoolean("longPress", d.longPress),
            longPressMs = prefs.getLong("longPressMs", d.longPressMs),
            haptics = prefs.getBoolean("haptics", d.haptics),
            hapticTicks = prefs.getBoolean("hapticTicks", d.hapticTicks),
            hapticSplit = prefs.getBoolean("hapticSplit", d.hapticSplit),
            hapticStrength = prefs.getFloat("hapticStrength", d.hapticStrength),
            hapticGapMs = prefs.getLong("hapticGapMs", d.hapticGapMs),
            showDeadSlots = prefs.getBoolean("showDeadSlots", d.showDeadSlots),
            showLabels = prefs.getBoolean("showLabels", d.showLabels),
            sectorStep = prefs.getFloat("sectorStep", d.sectorStep),
            farEdge = prefs.getFloat("farEdge", d.farEdge),
            mirror = prefs.getBoolean("mirror", d.mirror),
            mirrorAt = prefs.getFloat("mirrorAt", d.mirrorAt),
            mirrorScale = prefs.getFloat("mirrorScale", d.mirrorScale),
            mirrorDim = prefs.getFloat("mirrorDim", d.mirrorDim),
            cloud = prefs.getBoolean("cloud", d.cloud),
            cloudSamples = prefs.getInt("cloudSamples", d.cloudSamples),
            cloudAlpha = prefs.getFloat("cloudAlpha", d.cloudAlpha),
            touchReadout = prefs.getBoolean("touchReadout", d.touchReadout),
            fieldSplit = prefs.getFloat("fieldSplit", d.fieldSplit),
            markRadius = prefs.getFloat("markRadius", d.markRadius),
            labelSp = prefs.getFloat("labelSp", d.labelSp),
            keyboardHeight = prefs.getFloat("keyboardHeight", d.keyboardHeight),
        )
    }
}
