package app.cleanweb.samsung

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import app.cleanweb.data.FilterRepository
import java.io.File

/**
 * Samsung Internet "Ad blockers" add-on (developer.samsung.com/browser/android/adblockers-guide).
 * The browser reads an Adblock Plus–format rules file from [ContentBlockerProvider] and applies it
 * inside pages: URL rules, element hiding, and `$popup` rules that stop ad tabs from opening at all.
 */
object SamsungBlocker {
    private val BROWSERS = listOf("com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta")
    private const val ACTION_UPDATE = "com.samsung.android.sbrowser.contentBlocker.ACTION_UPDATE"
    private const val ACTION_SETTING = "com.samsung.android.sbrowser.contentBlocker.ACTION_SETTING"
    private const val PREFS = "samsung_blocker"
    private const val K_LAST_READ = "last_read"

    fun file(context: Context) = File(context.filesDir, "samsung/filters.txt")

    fun isBrowserInstalled(context: Context) = BROWSERS.any {
        try {
            context.packageManager.getPackageInfo(it, 0); true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /** When Samsung Internet last fetched our rules (0 = never, i.e. the add-on is not turned on). */
    fun lastRead(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(K_LAST_READ, 0)

    internal fun markRead(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(K_LAST_READ, System.currentTimeMillis()).apply()

    fun settingsIntent(context: Context): Intent? {
        val i = Intent(ACTION_SETTING).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (context.packageManager.queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()) i else null
    }

    /** Rebuilds the rules file from the current lists and tells Samsung Internet to reload it. */
    fun rebuild(context: Context, filters: FilterRepository) {
        if (!isBrowserInstalled(context)) return
        val out = file(context)
        out.parentFile?.mkdirs()
        val tmp = File(out.parentFile, "filters.tmp")
        val enabled = filters.lists.value.filter { it.enabled }.map { it.id }.toSet()
        tmp.bufferedWriter().use { w ->
            w.write("[Adblock Plus 2.0]\n! Title: CleanWeb\n")
            fun line(s: String) { w.write(s); w.write("\n") }

            // Full browser lists: URL rules, element hiding, $popup rules.
            for (id in FilterRepository.BROWSER_LISTS) {
                if (id !in enabled) continue
                val f = filters.listFile(id)
                if (!f.exists()) continue
                f.forEachLine { if (it.isNotEmpty() && it[0] != '[' && it[0] != '!') line(it) }
            }
            // Domain-only lists become "don't open as a pop-up" rules.
            for (id in listOf("hagezi_popup", "hagezi_tlds")) {
                if (id !in enabled) continue
                val f = filters.listFile(id)
                if (f.exists()) f.forEachLine { popupRule(it)?.let(::line) }
            }
            context.assets.open("seed_blocklist.txt").bufferedReader().forEachLine { popupRule(it)?.let(::line) }
            // The user's own blocks: block the request and the pop-up.
            filters.userRules().lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("!") }.forEach {
                line(it)
                popupRule(it)?.let(::line)
            }
            // Protected sites (banks, payments, government) and the whitelist: never touch these.
            filters.protectedDomains().forEach { line("@@||$it^\$document") }
            filters.whitelist().lineSequence().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.forEach {
                line("@@||${it.removePrefix("@@").removePrefix("||").removeSuffix("^")}^\$document")
            }
        }
        if (!tmp.renameTo(out)) { out.delete(); tmp.renameTo(out) }
        notifyBrowser(context)
    }

    private val DOMAIN_RULE = Regex("^\\|\\|(?:\\*\\.)?([a-z0-9.-]+)\\^(?:\\$.*)?$")

    /** `||ads.example^` (or a hosts/plain domain line) → `||ads.example^$popup` */
    private fun popupRule(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty() || s.startsWith("!") || s.startsWith("#") || s.startsWith("@@")) return null
        val host = DOMAIN_RULE.matchEntire(s)?.groupValues?.get(1)
            ?: s.split(Regex("\\s+")).let { p -> if (p.size == 2 && (p[0] == "0.0.0.0" || p[0] == "127.0.0.1")) p[1] else if (p.size == 1 && !s.contains('/') && !s.contains('$') && s.contains('.')) s else null }
            ?: return null
        return "||$host^\$popup"
    }

    private fun notifyBrowser(context: Context) {
        for (pkg in BROWSERS) {
            val i = Intent(ACTION_UPDATE).setData(Uri.parse("package:" + context.packageName)).setPackage(pkg)
            runCatching { context.sendBroadcast(i) }
        }
    }
}
