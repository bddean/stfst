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
            putInt("slotsFar", slotsFar)
            putInt("slotsNear", slotsNear)
            putFloat("startFarLeft", startFarLeft)
            putFloat("startNearLeft", startNearLeft)
            putFloat("startFarRight", startFarRight)
            putFloat("startNearRight", startNearRight)
            putFloat("minSlide", minSlide)
            putFloat("nearReach", nearReach)
            putFloat("farReach", farReach)
            putFloat("ringSplitAt", ringSplitAt)
            putFloat("snapDegrees", snapDegrees)
            putLong("stallMs", stallMs)
            putBoolean("commitOnFirstLift", commitOnFirstLift)
            putBoolean("longPress", longPress)
            putLong("longPressMs", longPressMs)
            putBoolean("haptics", haptics)
            putBoolean("showDeadSlots", showDeadSlots)
            putBoolean("showLabels", showLabels)
            putBoolean("mirror", mirror)
            putFloat("mirrorLift", mirrorLift)
            putFloat("mirrorScale", mirrorScale)
            putFloat("fieldSplit", fieldSplit)
            putFloat("keyRadius", keyRadius)
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
            slotsFar = prefs.getInt("slotsFar", d.slotsFar),
            slotsNear = prefs.getInt("slotsNear", d.slotsNear),
            startFarLeft = prefs.getFloat("startFarLeft", d.startFarLeft),
            startNearLeft = prefs.getFloat("startNearLeft", d.startNearLeft),
            startFarRight = prefs.getFloat("startFarRight", d.startFarRight),
            startNearRight = prefs.getFloat("startNearRight", d.startNearRight),
            minSlide = prefs.getFloat("minSlide", d.minSlide),
            nearReach = prefs.getFloat("nearReach", d.nearReach),
            farReach = prefs.getFloat("farReach", d.farReach),
            ringSplitAt = prefs.getFloat("ringSplitAt", d.ringSplitAt),
            snapDegrees = prefs.getFloat("snapDegrees", d.snapDegrees),
            stallMs = prefs.getLong("stallMs", d.stallMs),
            commitOnFirstLift = prefs.getBoolean("commitOnFirstLift", d.commitOnFirstLift),
            longPress = prefs.getBoolean("longPress", d.longPress),
            longPressMs = prefs.getLong("longPressMs", d.longPressMs),
            haptics = prefs.getBoolean("haptics", d.haptics),
            showDeadSlots = prefs.getBoolean("showDeadSlots", d.showDeadSlots),
            showLabels = prefs.getBoolean("showLabels", d.showLabels),
            mirror = prefs.getBoolean("mirror", d.mirror),
            mirrorLift = prefs.getFloat("mirrorLift", d.mirrorLift),
            mirrorScale = prefs.getFloat("mirrorScale", d.mirrorScale),
            fieldSplit = prefs.getFloat("fieldSplit", d.fieldSplit),
            keyRadius = prefs.getFloat("keyRadius", d.keyRadius),
            labelSp = prefs.getFloat("labelSp", d.labelSp),
            keyboardHeight = prefs.getFloat("keyboardHeight", d.keyboardHeight),
        )
    }
}
