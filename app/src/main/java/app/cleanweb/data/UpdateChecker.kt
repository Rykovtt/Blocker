package app.cleanweb.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val version: String, val pageUrl: String)

/**
 * Checks the project's GitHub releases for a newer APK.
 * Release tags are expected as "v1.2.3" (or "1.2.3").
 */
object UpdateChecker {
    private const val LATEST = "https://api.github.com/repos/rykovtt/blocker/releases/latest"

    fun check(currentVersion: String): UpdateInfo? {
        val conn = URL(LATEST).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "CleanWeb")
            if (conn.responseCode != 200) return null
            val o = JSONObject(conn.inputStream.bufferedReader().readText())
            val tag = o.optString("tag_name").removePrefix("v")
            if (tag.isEmpty() || compare(tag, currentVersion) <= 0) return null
            return UpdateInfo(tag, o.optString("html_url"))
        } finally {
            conn.disconnect()
        }
    }

    fun compare(a: String, b: String): Int {
        val x = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }
}
