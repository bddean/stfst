package cx.yeet.stfst

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings

/**
 * What a plugged-in machine opens. Choosing it once with "always" is the only
 * way Android remembers the permission across replugs; the keyboard does the
 * rest, so this only tells it to look.
 *
 * Enabled only while the keyboard is the selected input method; see [offer].
 */
class UsbActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        // A process that died with the keyboard selected never ran Machines.stop.
        if (selected(this)) sendBroadcast(Intent(Machines.ACTION_SCAN).setPackage(packageName))
        else offer(this, false)
        finish()
    }

    companion object {
        /** Whether Android offers this activity for a plugged-in device. */
        fun offer(context: Context, on: Boolean) {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, UsbActivity::class.java),
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP,
            )
        }

        private fun selected(context: Context): Boolean {
            val ime = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            return ime != null && ComponentName.unflattenFromString(ime) == ComponentName(context, StenoService::class.java)
        }
    }
}
