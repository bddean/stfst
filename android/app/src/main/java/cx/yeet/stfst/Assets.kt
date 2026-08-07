package cx.yeet.stfst

import android.content.Context
import android.util.Log
import java.io.File

/**
 * The dictionaries onto the filesystem, once.
 *
 * `stfst-ol` maps its artifacts, and an asset inside an APK is not a file, so
 * they are copied out on first run. A marker file names the build that wrote
 * them, so a new APK replaces them and an ordinary launch does nothing.
 */
object Assets {

    fun unpack(context: Context): File {
        val root = File(context.filesDir, "runtime")
        val stamp = File(root, ".build")
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        } catch (e: Exception) {
            "unknown"
        }
        if (stamp.isFile && stamp.readText() == version) return root
        root.deleteRecursively()
        root.mkdirs()
        copy(context, "runtime", root)
        stamp.writeText(version)
        Log.i(StenoService.TAG, "unpacked the runtime into $root")
        return root
    }

    private fun copy(context: Context, from: String, to: File) {
        val names = context.assets.list(from) ?: return
        if (names.isEmpty()) {
            context.assets.open(from).use { source ->
                to.outputStream().use { source.copyTo(it) }
            }
            return
        }
        to.mkdirs()
        for (name in names) copy(context, "$from/$name", File(to, name))
    }
}
