package app.cleanweb.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import app.cleanweb.AppGraph
import app.cleanweb.vpn.BlockerVpnService

/** Turns protection back on after a reboot or an app update, if it was on before. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val s = AppGraph.get(context).settings.current
        if (!s.autostart || !s.protectionWanted) return
        if (VpnService.prepare(context) != null) return // permission was revoked
        runCatching { BlockerVpnService.start(context) }
    }
}
