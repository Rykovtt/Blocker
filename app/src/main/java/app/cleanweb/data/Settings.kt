package app.cleanweb.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Upstream(val id: String, val label: String, val dohUrl: String, val dotHost: String, val plainIp: String) {
    CLOUDFLARE("cloudflare", "Cloudflare", "https://cloudflare-dns.com/dns-query", "one.one.one.one", "1.1.1.1"),
    QUAD9("quad9", "Quad9", "https://dns.quad9.net/dns-query", "dns.quad9.net", "9.9.9.9"),
    ADGUARD("adguard", "AdGuard DNS", "https://dns.adguard-dns.com/dns-query", "dns.adguard-dns.com", "94.140.14.14"),
    GOOGLE("google", "Google", "https://dns.google/dns-query", "dns.google", "8.8.8.8"),
    CUSTOM("custom", "", "", "", "1.1.1.1");

    companion object {
        fun byId(id: String?) = entries.firstOrNull { it.id == id } ?: CLOUDFLARE
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class DnsProtocol { DOH, DOT }

data class SettingsState(
    val onboardingDone: Boolean,
    val protectionWanted: Boolean,
    val autostart: Boolean,
    val wifiOnlyUpdates: Boolean,
    val logEnabled: Boolean,
    val logRetentionDays: Int,
    val upstream: Upstream,
    val protocol: DnsProtocol,
    val customUpstream: String,
    val theme: ThemeMode,
    val excludedApps: Set<String>,
)

/** User settings in SharedPreferences, mirrored into a StateFlow for the UI. */
class Settings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<SettingsState> = _state.asStateFlow()
    val current: SettingsState get() = _state.value

    private fun read() = SettingsState(
        onboardingDone = prefs.getBoolean(K_ONBOARDING, false),
        protectionWanted = prefs.getBoolean(K_WANTED, false),
        autostart = prefs.getBoolean(K_AUTOSTART, true),
        wifiOnlyUpdates = prefs.getBoolean(K_WIFI_ONLY, false),
        logEnabled = prefs.getBoolean(K_LOG, true),
        logRetentionDays = prefs.getInt(K_RETENTION, 7),
        upstream = Upstream.byId(prefs.getString(K_UPSTREAM, null)),
        protocol = runCatching { DnsProtocol.valueOf(prefs.getString(K_PROTOCOL, "DOH")!!) }.getOrDefault(DnsProtocol.DOH),
        customUpstream = prefs.getString(K_CUSTOM, "") ?: "",
        theme = runCatching { ThemeMode.valueOf(prefs.getString(K_THEME, "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
        excludedApps = prefs.getStringSet(K_EXCLUDED, null) ?: DEFAULT_EXCLUDED_APPS,
    )

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _state.value = read()
    }

    fun setOnboardingDone() = edit { putBoolean(K_ONBOARDING, true) }
    fun setProtectionWanted(v: Boolean) = edit { putBoolean(K_WANTED, v) }
    fun setAutostart(v: Boolean) = edit { putBoolean(K_AUTOSTART, v) }
    fun setWifiOnlyUpdates(v: Boolean) = edit { putBoolean(K_WIFI_ONLY, v) }
    fun setLogEnabled(v: Boolean) = edit { putBoolean(K_LOG, v) }
    fun setLogRetentionDays(v: Int) = edit { putInt(K_RETENTION, v) }
    fun setUpstream(u: Upstream, custom: String = current.customUpstream) =
        edit { putString(K_UPSTREAM, u.id); putString(K_CUSTOM, custom.trim()) }
    fun setProtocol(p: DnsProtocol) = edit { putString(K_PROTOCOL, p.name) }
    fun setTheme(t: ThemeMode) = edit { putString(K_THEME, t.name) }
    fun setAppExcluded(pkg: String, excluded: Boolean) = edit {
        val set = current.excludedApps.toMutableSet()
        if (excluded) set.add(pkg) else set.remove(pkg)
        putStringSet(K_EXCLUDED, set)
    }

    /** All settings as plain values, for the backup file. */
    fun export(): Map<String, Any?> = prefs.all

    fun import(values: Map<String, Any?>) = edit {
        clear()
        for ((k, v) in values) when (v) {
            is Boolean -> putBoolean(k, v)
            is Int -> putInt(k, v)
            is String -> putString(k, v)
            is Set<*> -> putStringSet(k, v.map { it.toString() }.toSet())
        }
    }

    companion object {
        private const val K_ONBOARDING = "onboarding_done"
        private const val K_WANTED = "protection_wanted"
        private const val K_AUTOSTART = "autostart"
        private const val K_WIFI_ONLY = "wifi_only_updates"
        private const val K_LOG = "log_enabled"
        private const val K_RETENTION = "log_retention_days"
        private const val K_UPSTREAM = "upstream"
        private const val K_CUSTOM = "custom_upstream"
        private const val K_THEME = "theme"
        private const val K_PROTOCOL = "dns_protocol"
        private const val K_EXCLUDED = "excluded_apps"

        /** Banking and government apps that are kept outside the VPN by default. */
        val DEFAULT_EXCLUDED_APPS = setOf(
            "com.ftband.mono",            // Monobank
            "ua.privatbank.ap24",         // Privat24
            "ua.gov.diia.app",            // Diia
            "ua.raiffeisen.myraif",       // Raiffeisen
            "com.ukrsibbank.client.android",
            "ua.oschadbank.online",
            "ru.sberbankmobile",
            "com.idamob.tinkoff.android",
            "ru.vtb24.mobilebanking.android",
            "ru.alfabank.mobile.android",
            "com.google.android.apps.walletnfcrel", // Google Wallet
            "com.samsung.android.spay",             // Samsung Wallet
        )
    }
}
