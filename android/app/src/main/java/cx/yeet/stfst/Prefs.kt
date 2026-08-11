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
            putBoolean("board", board)
            putString("boardIslandLeft", boardIslandLeft)
            putString("boardRingLeft", boardRingLeft)
            putString("boardIslandRight", boardIslandRight)
            putString("boardRingRight", boardRingRight)
            putString("boardPortals", boardPortals)
            putFloat("boardSpan", boardSpan)
            putFloat("boardCell", boardCell)
            putFloat("boardInner", boardInner)
            putFloat("boardOuter", boardOuter)
            putFloat("boardAt", boardAt)
            putFloat("boardStartLeft", boardStartLeft)
            putFloat("boardStartRight", boardStartRight)
            putInt("entryCols", entryCols)
            putFloat("entryWide", entryWide)
            putFloat("entryTall", entryTall)
            putFloat("entryAt", entryAt)
            putBoolean("entryCentral", entryCentral)
            putFloat("startFarLeft", startFarLeft)
            putFloat("startFarRight", startFarRight)
            putFloat("minSlide", minSlide)
            putFloat("farReach", farReach)
            putFloat("turnDegrees", turnDegrees)
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
            putString("preview", preview.name)
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
            board = prefs.getBoolean("board", d.board),
            boardIslandLeft = prefs.getString("boardIslandLeft", d.boardIslandLeft)!!,
            boardRingLeft = prefs.getString("boardRingLeft", d.boardRingLeft)!!,
            boardIslandRight = prefs.getString("boardIslandRight", d.boardIslandRight)!!,
            boardRingRight = prefs.getString("boardRingRight", d.boardRingRight)!!,
            boardPortals = prefs.getString("boardPortals", d.boardPortals)!!,
            boardSpan = prefs.getFloat("boardSpan", d.boardSpan),
            boardCell = prefs.getFloat("boardCell", d.boardCell),
            boardInner = prefs.getFloat("boardInner", d.boardInner),
            boardOuter = prefs.getFloat("boardOuter", d.boardOuter),
            boardAt = prefs.getFloat("boardAt", d.boardAt),
            boardStartLeft = prefs.getFloat("boardStartLeft", d.boardStartLeft),
            boardStartRight = prefs.getFloat("boardStartRight", d.boardStartRight),
            entryCols = prefs.getInt("entryCols", d.entryCols),
            entryWide = prefs.getFloat("entryWide", d.entryWide),
            entryTall = prefs.getFloat("entryTall", d.entryTall),
            entryAt = prefs.getFloat("entryAt", d.entryAt),
            entryCentral = prefs.getBoolean("entryCentral", d.entryCentral),
            startFarLeft = prefs.getFloat("startFarLeft", d.startFarLeft),
            startFarRight = prefs.getFloat("startFarRight", d.startFarRight),
            minSlide = prefs.getFloat("minSlide", d.minSlide),
            farReach = prefs.getFloat("farReach", d.farReach),
            turnDegrees = prefs.getFloat("turnDegrees", d.turnDegrees),
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
            preview = prefs.getString("preview", null)
                ?.let { name -> Preview.values().firstOrNull { it.name == name } } ?: d.preview,
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
