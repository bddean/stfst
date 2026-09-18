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

    /**
     * Where the editable dictionaries live, seeded from what the APK carried.
     *
     * Not beside the compiled ones. The keyboard writes here, and external
     * storage is what `make install` can reach over adb: the desktop's
     * `user.json` is pushed in and what this keyboard defined is pulled out. So
     * the APK's copies overwrite what is there, except the file this keyboard
     * writes, which nothing else owns.
     *
     * A directory something else created first belongs to that something else,
     * and then this app cannot write in it at all. That is a keyboard that does
     * not start, so it falls back to its own private directory, where adb cannot
     * reach it and everything else still works.
     */
    fun editable(context: Context, runtime: File): File {
        val shared = File(context.getExternalFilesDir(null) ?: context.filesDir, "dicts")
        val dir = if (writable(shared)) shared else File(context.filesDir, "dicts").also {
            it.mkdirs()
            Log.w(StenoService.TAG, "cannot write in $shared; using $it, which adb cannot reach")
        }
        for (file in File(runtime, "editable").listFiles().orEmpty()) {
            val to = File(dir, file.name)
            try {
                if (to.isFile && to.readBytes().contentEquals(file.readBytes())) continue
                file.copyTo(to, overwrite = true)
            } catch (e: Exception) {
                // One dictionary that did not land is a dictionary that does not
                // answer, and the rest of the stack still does.
                Log.e(StenoService.TAG, "seeding ${file.name}: $e")
            }
        }
        return dir
    }

    /** Whether this app can put a file there, which is not what the mode bits say. */
    private fun writable(dir: File): Boolean = try {
        dir.mkdirs()
        val probe = File(dir, ".probe")
        probe.writeText("")
        probe.delete()
        true
    } catch (e: Exception) {
        false
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
