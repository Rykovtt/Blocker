package app.cleanweb

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import app.cleanweb.data.FilterRepository
import app.cleanweb.data.FilterUpdateWorker
import app.cleanweb.data.QueryLog
import app.cleanweb.data.Settings
import app.cleanweb.samsung.SamsungBlocker
import app.cleanweb.system.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CleanWebApp : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        val g = AppGraph.get(this)
        Notifications.createChannels(this)
        FilterUpdateWorker.schedule(this, g.settings.current.wifiOnlyUpdates)
        g.log.purgeOlderThan(g.settings.current.logRetentionDays)
        // Keep Samsung Internet's rules in step with every list or rule change.
        appScope.launch {
            g.filters.version.collectLatest {
                delay(1500)
                runCatching { SamsungBlocker.rebuild(this@CleanWebApp, g.filters) }
            }
        }
        // Lists saved by older versions kept only domain rules; fetch the full ones once.
        if (g.filters.needsFullRedownload()) appScope.launch { g.filters.updateAll(); g.filters.markFullDownloaded() }
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

/** Process-wide singletons. The VPN service and the UI run in the same process. */
class AppGraph private constructor(context: Context) {
    val settings = Settings(context)
    val filters = FilterRepository(context)
    val log = QueryLog(context)

    companion object {
        @Volatile private var instance: AppGraph? = null

        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) {
                instance ?: AppGraph(context.applicationContext).also { instance = it }
            }
    }
}
