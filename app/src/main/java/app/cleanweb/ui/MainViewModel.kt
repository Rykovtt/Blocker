package app.cleanweb.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.cleanweb.AppGraph
import app.cleanweb.R
import android.net.Uri
import app.cleanweb.BuildConfig
import app.cleanweb.data.Backup
import app.cleanweb.data.DayCounts
import app.cleanweb.data.DnsProtocol
import app.cleanweb.data.UpdateChecker
import app.cleanweb.data.UpdateInfo
import app.cleanweb.data.FilterUpdateWorker
import app.cleanweb.data.LogEntry
import app.cleanweb.data.QueryLog
import app.cleanweb.data.QueryStatus
import app.cleanweb.data.ThemeMode
import app.cleanweb.data.Upstream
import app.cleanweb.system.DeviceChecks
import app.cleanweb.vpn.BlockerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(val pkg: String, val label: String, val icon: Bitmap?)

data class StatsData(
    val total: Int,
    val blocked: Int,
    val series: List<DayCounts>,
    val topDomains: List<Pair<String, Int>>,
    val topApps: List<Pair<String, Int>>,
)

data class HomeStats(val today: DayCounts, val topBlocked: List<Pair<String, Int>>)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val g = AppGraph.get(app)

    val settings = g.settings.state
    val status = BlockerState.status
    val ruleCount = BlockerState.ruleCount
    val engineLoading = BlockerState.engineLoading
    val lastError = BlockerState.lastError
    val pausedUntil = BlockerState.pausedUntil
    val lists = g.filters.lists
    val updating = g.filters.updating

    /** Short messages for the snackbar (string resource ids). */
    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    val homeStats = g.log.changes.map {
        val since = QueryLog.startOfToday()
        HomeStats(g.log.countsSince(since), g.log.topBlocked(since, 5))
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeStats(DayCounts(0, 0), emptyList()))

    // ---- statistics ----
    private val _stats = MutableStateFlow<StatsData?>(null)
    val stats = _stats.asStateFlow()

    fun loadStats(period: StatsPeriod) = io {
        // Buckets end at the current hour (day view) or today (week/month view).
        val end = if (period == StatsPeriod.DAY) {
            (System.currentTimeMillis() / 3_600_000L + 1) * 3_600_000L
        } else {
            QueryLog.startOfToday() + 86_400_000L
        }
        val since = end - period.buckets * period.bucketMs
        val series = g.log.series(since, period.bucketMs, period.buckets)
        _stats.value = StatsData(
            total = series.sumOf { it.total },
            blocked = series.sumOf { it.blocked },
            series = series,
            topDomains = g.log.topBlocked(since, 10),
            topApps = g.log.topApps(since, 10),
        )
    }

    // ---- log ----
    val logFilter = MutableStateFlow<QueryStatus?>(null)
    val logSearch = MutableStateFlow("")
    val logEntries = combine(g.log.changes, logFilter, logSearch) { _, f, s -> g.log.recent(500, f, s.trim()) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<LogEntry>())

    fun blockDomain(domain: String) = io { g.filters.blockDomain(domain); _messages.emit(R.string.msg_domain_blocked) }
    fun allowDomain(domain: String) = io { g.filters.allowDomain(domain); _messages.emit(R.string.msg_domain_allowed) }
    fun clearLog() = g.log.clear()

    // ---- device checks (refreshed on resume) ----
    private val _privateDns = MutableStateFlow(false)
    val privateDns = _privateDns.asStateFlow()
    private val _batteryOk = MutableStateFlow(true)
    val batteryOk = _batteryOk.asStateFlow()

    fun refreshChecks() {
        val ctx = getApplication<Application>()
        _privateDns.value = runCatching { DeviceChecks.privateDnsActive(ctx) }.getOrDefault(false)
        _batteryOk.value = runCatching { DeviceChecks.ignoringBatteryOptimizations(ctx) }.getOrDefault(true)
    }

    // ---- filters ----
    fun updateFilters() = viewModelScope.launch {
        val failed = g.filters.updateAll()
        _messages.emit(if (failed == 0) R.string.msg_filters_updated else R.string.msg_filters_partial)
    }

    /** Fetch lists that are enabled but not downloaded yet (first run, or new lists after an update). */
    fun ensureFiltersDownloaded() {
        if (g.filters.hasMissingLists() && !g.filters.updating.value) updateFilters()
    }

    fun setListEnabled(id: String, enabled: Boolean) = io { g.filters.setEnabled(id, enabled) }
    fun removeList(id: String) = io { g.filters.remove(id) }
    fun addList(url: String, name: String) = viewModelScope.launch {
        val added = withContext(Dispatchers.IO) { g.filters.addCustom(url, name) }
        if (added == null) _messages.emit(R.string.msg_bad_url) else updateFilters()
    }

    suspend fun userRules(): String = withContext(Dispatchers.IO) { g.filters.userRules() }
    suspend fun whitelist(): String = withContext(Dispatchers.IO) { g.filters.whitelist() }
    fun saveUserRules(text: String) = io { g.filters.setUserRules(text); _messages.emit(R.string.msg_saved) }
    fun saveWhitelist(text: String) = io { g.filters.setWhitelist(text); _messages.emit(R.string.msg_saved) }

    // ---- apps ----
    private val _apps = MutableStateFlow<List<AppInfo>?>(null)
    val apps = _apps.asStateFlow()

    fun loadApps() {
        if (_apps.value != null) return
        io {
            val pm = getApplication<Application>().packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val own = getApplication<Application>().packageName
            val list = pm.queryIntentActivities(launcher, 0)
                .map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != own }
                .map { ai ->
                    val icon = runCatching { pm.getApplicationIcon(ai).toBitmap(96, 96) }.getOrNull()
                    AppInfo(ai.packageName, pm.getApplicationLabel(ai).toString(), icon)
                }
                .sortedBy { it.label.lowercase() }
            _apps.value = list
        }
    }

    fun setAppFiltered(pkg: String, filtered: Boolean) = g.settings.setAppExcluded(pkg, !filtered)

    // ---- settings ----
    fun setUpstream(u: Upstream, custom: String) = g.settings.setUpstream(u, custom)
    fun setProtocol(p: DnsProtocol) = g.settings.setProtocol(p)

    fun exportBackup(uri: Uri) = io {
        val ok = runCatching { Backup.write(getApplication(), uri, g.settings, g.filters) }.isSuccess
        _messages.emit(if (ok) R.string.msg_backup_saved else R.string.msg_backup_failed)
    }

    fun importBackup(uri: Uri) = io {
        val ok = runCatching { Backup.read(getApplication(), uri, g.settings, g.filters) }.isSuccess
        _messages.emit(if (ok) R.string.msg_backup_restored else R.string.msg_backup_bad)
    }

    // ---- updates ----
    private val _update = MutableStateFlow<UpdateInfo?>(null)
    val update = _update.asStateFlow()

    fun checkUpdates(manual: Boolean) = io {
        val info = runCatching { UpdateChecker.check(BuildConfig.VERSION_NAME) }.getOrNull()
        _update.value = info
        if (manual && info == null) _messages.emit(R.string.msg_no_update)
    }
    fun setAutostart(v: Boolean) = g.settings.setAutostart(v)
    fun setLogEnabled(v: Boolean) = g.settings.setLogEnabled(v)
    fun setTheme(t: ThemeMode) = g.settings.setTheme(t)
    fun setRetention(days: Int) {
        g.settings.setLogRetentionDays(days)
        g.log.purgeOlderThan(days)
    }
    fun setWifiOnly(v: Boolean) {
        g.settings.setWifiOnlyUpdates(v)
        FilterUpdateWorker.schedule(getApplication(), v)
    }
    fun finishOnboarding() = g.settings.setOnboardingDone()

    private fun io(block: suspend () -> Unit) = viewModelScope.launch(Dispatchers.IO) { block() }
}
