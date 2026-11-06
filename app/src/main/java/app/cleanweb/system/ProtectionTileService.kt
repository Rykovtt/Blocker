package app.cleanweb.system

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.cleanweb.R
import app.cleanweb.ui.MainActivity
import app.cleanweb.vpn.BlockerState
import app.cleanweb.vpn.BlockerVpnService
import app.cleanweb.vpn.ProtectionStatus

/** Quick Settings tile: one tap turns protection on or off. */
class ProtectionTileService : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        if (BlockerState.status.value != ProtectionStatus.STOPPED) {
            BlockerVpnService.stop(this)
        } else if (VpnService.prepare(this) == null) {
            BlockerVpnService.start(this)
        } else {
            openApp() // VPN permission must be granted from an activity
        }
        refresh()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val status = BlockerState.status.value
        tile.state = if (status == ProtectionStatus.RUNNING) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = getString(
                when (status) {
                    ProtectionStatus.RUNNING -> R.string.tile_on
                    ProtectionStatus.PAUSED -> R.string.tile_paused
                    ProtectionStatus.STARTING -> R.string.tile_starting
                    ProtectionStatus.STOPPED -> R.string.tile_off
                },
            )
        }
        tile.updateTile()
    }

    companion object {
        fun requestUpdate(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, ProtectionTileService::class.java)) }
        }
    }
}
