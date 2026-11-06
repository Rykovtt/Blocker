package app.cleanweb.data

import android.content.Context
import app.cleanweb.filter.FilterEngine
import app.cleanweb.filter.RuleSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class FilterList(
    val id: String,
    val name: String,
    val url: String,
    val builtin: Boolean,
    val enabled: Boolean,
    val lastUpdated: Long = 0,
    val ruleCount: Int = 0,
    val lastError: String? = null,
)

/**
 * Filter lists, the user's own rules and the whitelist.
 * Downloaded lists live in files/filters/<id>.txt; metadata lives in a JSON pref.
 */
class FilterRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("filters", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "filters").apply { mkdirs() }
    private val userRulesFile = File(context.filesDir, "user_rules.txt")
    private val whitelistFile = File(context.filesDir, "whitelist.txt")
    private val updateLock = Mutex()

    private val _lists = MutableStateFlow(loadLists())
    val lists: StateFlow<List<FilterList>> = _lists.asStateFlow()

    /** Bumped whenever rules change, so the VPN rebuilds its engine. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating.asStateFlow()

    // ---- lists ----

    private fun loadLists(): List<FilterList> {
        val saved = runCatching { JSONArray(prefs.getString(K_LISTS, "[]")) }.getOrDefault(JSONArray())
        val byId = (0 until saved.length()).map { saved.getJSONObject(it) }.associateBy { it.getString("id") }
        val builtins = BUILTIN.map { b ->
            byId[b.id]?.let { b.copy(enabled = it.optBoolean("enabled", b.enabled), lastUpdated = it.optLong("updated"), ruleCount = it.optInt("rules"), lastError = it.optString("error").ifEmpty { null }) } ?: b
        }
        val custom = byId.values.filter { o -> BUILTIN.none { it.id == o.getString("id") } }.map {
            FilterList(it.getString("id"), it.getString("name"), it.getString("url"), false, it.optBoolean("enabled", true), it.optLong("updated"), it.optInt("rules"), it.optString("error").ifEmpty { null })
        }
        return builtins + custom
    }

    private fun saveLists(lists: List<FilterList>) {
        val arr = JSONArray()
        lists.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url).put("enabled", it.enabled).put("updated", it.lastUpdated).put("rules", it.ruleCount).put("error", it.lastError ?: ""))
        }
        prefs.edit().putString(K_LISTS, arr.toString()).apply()
        _lists.value = lists
    }

    private fun modify(id: String, f: (FilterList) -> FilterList) = synchronized(this) {
        saveLists(_lists.value.map { if (it.id == id) f(it) else it })
    }

    fun setEnabled(id: String, enabled: Boolean) {
        modify(id) { it.copy(enabled = enabled) }
        bump()
    }

    fun addCustom(url: String, name: String): FilterList? {
        val u = url.trim()
        if (!u.startsWith("https://") && !u.startsWith("http://")) return null
        if (_lists.value.any { it.url == u }) return null
        val list = FilterList("c" + System.currentTimeMillis().toString(36), name.trim().ifEmpty { URL(u).host }, u, false, true)
        synchronized(this) { saveLists(_lists.value + list) }
        return list
    }

    fun remove(id: String) {
        synchronized(this) { saveLists(_lists.value.filterNot { it.id == id && !it.builtin }) }
        File(dir, "$id.txt").delete()
        bump()
    }

    fun hasAnyDownloaded(): Boolean = _lists.value.any { it.enabled && File(dir, "${it.id}.txt").exists() }

    /** Downloads every enabled list. Returns the number of lists that failed. */
    suspend fun updateAll(): Int = updateLock.withLock {
        _updating.value = true
        var failed = 0
        try {
            withContext(Dispatchers.IO) {
                for (list in _lists.value.filter { it.enabled }) {
                    val result = runCatching { download(list) }
                    result.onSuccess { count ->
                        modify(list.id) { it.copy(lastUpdated = System.currentTimeMillis(), ruleCount = count, lastError = null) }
                    }.onFailure { e ->
                        failed++
                        modify(list.id) { it.copy(lastError = e.message ?: e.javaClass.simpleName) }
                    }
                }
            }
        } finally {
            _updating.value = false
        }
        bump()
        failed
    }

    private fun download(list: FilterList): Int {
        val conn = (URL(list.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "CleanWeb/1.0")
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val tmp = File(dir, "${list.id}.tmp")
            val probe = RuleSet()
            var count = 0
            tmp.bufferedWriter().use { out ->
                conn.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        // Keep only lines DNS filtering can use: smaller files, faster loading.
                        if (probe.addLine(line)) {
                            out.write(line.trim())
                            out.write('\n'.code)
                            count++
                        }
                    }
                }
            }
            if (count == 0) {
                tmp.delete()
                error("no usable rules")
            }
            if (!tmp.renameTo(File(dir, "${list.id}.txt"))) error("cannot save")
            return count
        } finally {
            conn.disconnect()
        }
    }

    // ---- user rules and whitelist ----

    fun userRules(): String = userRulesFile.takeIf { it.exists() }?.readText() ?: ""
    fun whitelist(): String = whitelistFile.takeIf { it.exists() }?.readText() ?: ""

    fun setUserRules(text: String) { userRulesFile.writeText(text); bump() }
    fun setWhitelist(text: String) { whitelistFile.writeText(text); bump() }

    /** One-tap action from the log: block a domain (and drop it from the whitelist). */
    fun blockDomain(domain: String) {
        setWhitelist(removeDomainLine(whitelist(), domain))
        val rules = userRules()
        val rule = "||$domain^"
        if (rules.lines().none { it.trim() == rule }) setUserRules(appendLine(rules, rule))
    }

    /** One-tap action from the log: allow a domain (and drop a matching user block rule). */
    fun allowDomain(domain: String) {
        setUserRules(userRules().lines().filterNot { it.trim() == "||$domain^" || it.trim() == domain }.joinToString("\n"))
        val wl = whitelist()
        if (wl.lines().none { it.trim() == domain }) setWhitelist(appendLine(wl, domain))
    }

    private fun appendLine(text: String, line: String) =
        if (text.isBlank()) line else text.trimEnd() + "\n" + line

    private fun removeDomainLine(text: String, domain: String) =
        text.lines().filterNot { it.trim() == domain }.joinToString("\n")

    // ---- engine ----

    /** Builds a fresh engine from the bundled seed, enabled lists, user rules and whitelist. */
    fun buildEngine(): FilterEngine {
        val rules = RuleSet()
        context.assets.open("seed_blocklist.txt").bufferedReader().useLines { rules.addAll(it) }
        for (list in _lists.value.filter { it.enabled }) {
            val f = File(dir, "${list.id}.txt")
            if (f.exists()) f.bufferedReader().useLines { rules.addAll(it) }
        }
        userRules().lineSequence().forEach { rules.addLine(it) }
        whitelist().lineSequence().forEach { line ->
            val d = line.substringBefore('#').trim().removePrefix("@@").removePrefix("||").removeSuffix("^")
            if (d.isNotEmpty()) rules.allow(d)
        }
        return FilterEngine(rules)
    }

    private fun bump() { _version.value++ }

    companion object {
        private const val K_LISTS = "lists"

        val BUILTIN = listOf(
            FilterList("adguard_dns", "AdGuard DNS filter", "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt", true, true),
            FilterList("easylist", "EasyList", "https://easylist.to/easylist/easylist.txt", true, true),
            FilterList("easyprivacy", "EasyPrivacy", "https://easylist.to/easylist/easyprivacy.txt", true, true),
            FilterList("ruadlist", "RU AdList", "https://easylist-downloads.adblockplus.org/ruadlist.txt", true, true),
            FilterList("adguard_base", "AdGuard Base", "https://filters.adtidy.org/extension/ublock/filters/2_without_easylist.txt", true, true),
            FilterList("urlhaus", "URLhaus Malicious URL Blocklist", "https://malware-filter.gitlab.io/malware-filter/urlhaus-filter-agh.txt", true, true),
        )
    }
}
