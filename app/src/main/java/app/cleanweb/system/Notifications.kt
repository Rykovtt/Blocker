package app.cleanweb.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.cleanweb.R
import app.cleanweb.ui.MainActivity
import app.cleanweb.ui.QuickBlockActivity
import app.cleanweb.vpn.BlockerVpnService
import app.cleanweb.vpn.ProtectionStatus

object Notifications {
    const val ID_PROTECTION = 1
    private const val ID_REVOKED = 2
    private const val CH_PROTECTION = "protection"
    private const val CH_ALERTS = "alerts"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROTECTION, context.getString(R.string.channel_protection), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun serviceIntent(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getService(
        context, code, BlockerVpnService.intent(context, action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun protection(context: Context, status: ProtectionStatus, blockedToday: Int): Notification {
        val paused = status == ProtectionStatus.PAUSED
        val title = context.getString(if (paused) R.string.notif_paused else R.string.notif_active)
        val b = NotificationCompat.Builder(context, CH_PROTECTION)
            .setSmallIcon(R.drawable.ic_shield)
            .setColor(0xFF3B2DF2.toInt())
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notif_blocked_today, blockedToday))
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        b.addAction(
            0, context.getString(R.string.qb_notif_action),
            PendingIntent.getActivity(context, 4, QuickBlockActivity.intent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )
        if (paused) {
            b.addAction(0, context.getString(R.string.action_resume), serviceIntent(context, BlockerVpnService.ACTION_RESUME, 2))
        } else {
            b.addAction(0, context.getString(R.string.action_pause_5), serviceIntent(context, BlockerVpnService.ACTION_PAUSE, 1))
        }
        b.addAction(0, context.getString(R.string.action_turn_off), serviceIntent(context, BlockerVpnService.ACTION_STOP, 3))
        return b.build()
    }

    fun update(context: Context, status: ProtectionStatus, blockedToday: Int) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(ID_PROTECTION, protection(context, status, blockedToday))
    }

    fun showRevoked(context: Context) {
        val n = NotificationCompat.Builder(context, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(context.getString(R.string.notif_revoked_title))
            .setContentText(context.getString(R.string.error_vpn_revoked))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(ID_REVOKED, n) }
    }
}
