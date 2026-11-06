package app.cleanweb

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import app.cleanweb.data.FilterRepository
import app.cleanweb.data.FilterUpdateWorker
import app.cleanweb.data.QueryLog
import app.cleanweb.data.Settings
import app.cleanweb.system.Notifications

class CleanWebApp : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        val g = AppGraph.get(this)
        Notifications.createChannels(this)
        FilterUpdateWorker.schedule(this, g.settings.current.wifiOnlyUpdates)
        g.log.purgeOlderThan(g.settings.current.logRetentionDays)
    }
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
