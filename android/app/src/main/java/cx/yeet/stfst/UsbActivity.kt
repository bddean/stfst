package cx.yeet.stfst

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * What a plugged-in machine opens. Choosing it once with "always" is the only
 * way Android remembers the permission across replugs; the keyboard does the
 * rest, so this only tells it to look.
 */
class UsbActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        sendBroadcast(Intent(Machines.ACTION_SCAN).setPackage(packageName))
        finish()
    }
}
