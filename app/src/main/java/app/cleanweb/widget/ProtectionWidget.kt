package app.cleanweb.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.widget.RemoteViews
import app.cleanweb.AppGraph
import app.cleanweb.R
import app.cleanweb.data.QueryLog
import app.cleanweb.ui.MainActivity
import app.cleanweb.vpn.BlockerState
import app.cleanweb.vpn.BlockerVpnService
import app.cleanweb.vpn.ProtectionStatus
import kotlin.concurrent.thread

/** Home-screen widget: protection state, blocked-today counter, tap to turn on/off. */
class ProtectionWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext
        val pending = goAsync()
        thread {
            try {
                val today = AppGraph.get(app).log.countsSince(QueryLog.startOfToday()).blocked
                render(app, BlockerState.status.value, today)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE) {
            if (BlockerState.status.value != ProtectionStatus.STOPPED) {
                BlockerVpnService.stop(context)
            } else if (VpnService.prepare(context) == null) {
                BlockerVpnService.start(context)
            } else {
                context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        private const val ACTION_TOGGLE = "app.cleanweb.widget.TOGGLE"

        fun render(context: Context, status: ProtectionStatus, blockedToday: Int) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ProtectionWidget::class.java))
            if (ids.isEmpty()) return
            val on = status == ProtectionStatus.RUNNING
            val views = RemoteViews(context.packageName, R.layout.widget_protection).apply {
                setTextViewText(R.id.widget_count, blockedToday.toString())
                setTextViewText(
                    R.id.widget_status,
                    context.getString(
                        when (status) {
                            ProtectionStatus.RUNNING -> R.string.tile_on
                            ProtectionStatus.PAUSED -> R.string.tile_paused
                            ProtectionStatus.STARTING -> R.string.tile_starting
                            ProtectionStatus.STOPPED -> R.string.tile_off
                        },
                    ),
                )
                setInt(R.id.widget_icon, "setAlpha", if (on) 255 else 110)
                val toggle = PendingIntent.getBroadcast(
                    context, 0,
                    Intent(context, ProtectionWidget::class.java).setAction(ACTION_TOGGLE),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                setOnClickPendingIntent(R.id.widget_root, toggle)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
