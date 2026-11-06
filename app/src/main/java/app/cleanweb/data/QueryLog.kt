package app.cleanweb.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

enum class QueryStatus { ALLOWED, BLOCKED, WHITELISTED, FAILED }

data class LogEntry(
    val time: Long,
    val domain: String,
    val qtype: Int,
    val app: String?,
    val status: QueryStatus,
)

data class DayCounts(val total: Int, val blocked: Int)

/** On-device DNS query log. Writes are batched once a second on a single thread. */
class QueryLog(context: Context) {
    private val db = Helper(context).writableDatabase
    private val pending = ConcurrentLinkedQueue<LogEntry>()
    private val writer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "query-log").apply { isDaemon = true } }

    /** Bumped after each flush; the UI re-queries when it changes. */
    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes.asStateFlow()

    init {
        writer.scheduleWithFixedDelay({ flush() }, 1, 1, TimeUnit.SECONDS)
    }

    fun record(entry: LogEntry) {
        pending.add(entry)
    }

    private fun flush() {
        if (pending.isEmpty()) return
        db.beginTransaction()
        try {
            val cv = ContentValues()
            while (true) {
                val e = pending.poll() ?: break
                cv.clear()
                cv.put("ts", e.time)
                cv.put("domain", e.domain)
                cv.put("qtype", e.qtype)
                cv.put("app", e.app)
                cv.put("status", e.status.ordinal)
                db.insert("log", null, cv)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        _changes.value = System.currentTimeMillis()
    }

    fun recent(limit: Int, status: QueryStatus?, search: String): List<LogEntry> {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (status != null) { where += "status = ?"; args += status.ordinal.toString() }
        if (search.isNotBlank()) { where += "(domain LIKE ? OR app LIKE ?)"; args += "%$search%"; args += "%$search%" }
        val sql = "SELECT ts, domain, qtype, app, status FROM log" +
            (if (where.isEmpty()) "" else " WHERE " + where.joinToString(" AND ")) +
            " ORDER BY id DESC LIMIT $limit"
        return db.rawQuery(sql, args.toTypedArray()).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(LogEntry(c.getLong(0), c.getString(1), c.getInt(2), c.getString(3), QueryStatus.entries[c.getInt(4)]))
                }
            }
        }
    }

    fun countsSince(since: Long): DayCounts =
        db.rawQuery("SELECT COUNT(*), SUM(status = 1) FROM log WHERE ts >= ?", arrayOf(since.toString())).use { c ->
            if (c.moveToFirst()) DayCounts(c.getInt(0), c.getInt(1)) else DayCounts(0, 0)
        }

    fun topBlocked(since: Long, limit: Int): List<Pair<String, Int>> =
        db.rawQuery(
            "SELECT domain, COUNT(*) n FROM log WHERE status = 1 AND ts >= ? GROUP BY domain ORDER BY n DESC LIMIT $limit",
            arrayOf(since.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0) to c.getInt(1)) } }

    /** Blocked and total requests per time bucket, oldest first. */
    fun series(since: Long, bucketMs: Long, buckets: Int): List<DayCounts> {
        val out = MutableList(buckets) { DayCounts(0, 0) }
        db.rawQuery(
            "SELECT (ts - ?) / ? b, COUNT(*), SUM(status = 1) FROM log WHERE ts >= ? GROUP BY b",
            arrayOf(since.toString(), bucketMs.toString(), since.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                val b = c.getInt(0)
                if (b in 0 until buckets) out[b] = DayCounts(c.getInt(1), c.getInt(2))
            }
        }
        return out
    }

    fun topApps(since: Long, limit: Int): List<Pair<String, Int>> =
        db.rawQuery(
            "SELECT app, COUNT(*) n FROM log WHERE status = 1 AND ts >= ? AND app IS NOT NULL GROUP BY app ORDER BY n DESC LIMIT $limit",
            arrayOf(since.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0) to c.getInt(1)) } }

    data class RecentDomain(val domain: String, val lastSeen: Long, val app: String?, val blocked: Boolean)

    /** Distinct domains seen since [since], newest first. */
    fun recentDomains(since: Long, limit: Int): List<RecentDomain> =
        db.rawQuery(
            "SELECT domain, MAX(ts) t, MAX(app), MAX(status = 1) FROM log WHERE ts >= ? GROUP BY domain ORDER BY t DESC LIMIT $limit",
            arrayOf(since.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(RecentDomain(c.getString(0), c.getLong(1), c.getString(2), c.getInt(3) == 1)) } }

    fun purgeOlderThan(days: Int) {
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        writer.execute { db.delete("log", "ts < ?", arrayOf(cutoff.toString())) }
    }

    fun clear() {
        writer.execute {
            db.delete("log", null, null)
            _changes.value = System.currentTimeMillis()
        }
    }

    private class Helper(context: Context) : SQLiteOpenHelper(context, "querylog.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE log (id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, domain TEXT NOT NULL, qtype INTEGER NOT NULL, app TEXT, status INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX log_ts ON log(ts)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    companion object {
        fun startOfToday(): Long = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
