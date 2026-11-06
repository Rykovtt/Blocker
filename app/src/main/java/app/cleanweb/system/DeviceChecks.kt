package app.cleanweb.system

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/** System settings that silently break DNS filtering or kill the service. */
object DeviceChecks {
    /** True when Android "Private DNS" is on: then queries bypass our resolver. */
    fun privateDnsActive(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        // With our VPN up, the active network is the VPN; check the underlying ones too.
        return cm.allNetworks.any { n -> cm.getLinkProperties(n)?.isPrivateDnsActive == true }
    }

    fun ignoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** Opens the Private DNS screen where available, else general network settings. */
    fun privateDnsSettingsIntent(): Intent =
        Intent("android.settings.PRIVATE_DNS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun fallbackNetworkSettingsIntent(): Intent =
        Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun batteryOptimizationIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun vpnSettingsIntent(): Intent = Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun startSafely(context: Context, primary: Intent, fallback: Intent? = null) {
        try {
            context.startActivity(primary)
        } catch (_: Exception) {
            fallback?.let { runCatching { context.startActivity(it) } }
        }
    }
}
