package app.cleanweb.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** Settings, lists, own rules and whitelist in one JSON file chosen by the user. */
object Backup {
    private const val FORMAT = 1

    fun write(context: Context, uri: Uri, settings: Settings, filters: FilterRepository) {
        val prefs = JSONObject()
        for ((k, v) in settings.export()) {
            prefs.put(k, if (v is Set<*>) JSONArray(v.toList()) else v)
        }
        val root = JSONObject()
            .put("app", "CleanWeb")
            .put("format", FORMAT)
            .put("created", System.currentTimeMillis())
            .put("settings", prefs)
            .put("filters", filters.exportJson())
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(root.toString(2).toByteArray()) }
    }

    /** Throws IllegalArgumentException if the file is not a CleanWeb backup. */
    fun read(context: Context, uri: Uri, settings: Settings, filters: FilterRepository) {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        val root = runCatching { JSONObject(text) }.getOrNull()
        require(root != null && root.optString("app") == "CleanWeb") { "not a CleanWeb backup" }
        val p = root.getJSONObject("settings")
        val values = HashMap<String, Any?>()
        for (k in p.keys()) {
            values[k] = when (val v = p.get(k)) {
                is JSONArray -> (0 until v.length()).map { v.getString(it) }.toSet()
                else -> v
            }
        }
        // Keep the onboarding flag and current on/off state of this phone.
        values["onboarding_done"] = true
        values.remove("protection_wanted")
        settings.import(values + ("protection_wanted" to settings.current.protectionWanted))
        filters.importJson(root.getJSONObject("filters"))
    }
}
