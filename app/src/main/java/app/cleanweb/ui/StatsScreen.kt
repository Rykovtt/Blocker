package app.cleanweb.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cleanweb.R
import java.text.NumberFormat

enum class StatsPeriod(val label: Int, val buckets: Int, val bucketMs: Long) {
    DAY(R.string.stats_day, 24, 3_600_000L),
    WEEK(R.string.stats_week, 7, 86_400_000L),
    MONTH(R.string.stats_month, 30, 86_400_000L),
}

@Composable
fun StatsScreen(vm: MainViewModel, modifier: Modifier) {
    var period by remember { mutableStateOf(StatsPeriod.DAY) }
    LaunchedEffect(period) { vm.loadStats(period) }
    val stats by vm.stats.collectAsStateWithLifecycle()
    val nf = remember { NumberFormat.getIntegerInstance() }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsPeriod.entries.forEach { p -> FilterChip(period == p, { period = p }, { Text(stringResource(p.label)) }) }
        }
        val s = stats ?: return@Column
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric(stringResource(R.string.stats_blocked), nf.format(s.blocked), Modifier.weight(1f))
            Metric(stringResource(R.string.stats_share), "${if (s.total == 0) 0 else s.blocked * 100 / s.total}%", Modifier.weight(1f))
            Metric(stringResource(R.string.stats_saved), "≈ ${formatMb(s.blocked)}", Modifier.weight(1f))
        }
        Text(
            stringResource(R.string.stats_saved_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        SectionTitle(stringResource(if (period == StatsPeriod.DAY) R.string.stats_by_hour else R.string.stats_by_day))
        BarChart(s.series.map { it.blocked }, Modifier.fillMaxWidth().height(160.dp).padding(horizontal = 16.dp))
        Text(
            stringResource(R.string.stats_max, nf.format(s.series.maxOfOrNull { it.blocked } ?: 0)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        TopList(stringResource(R.string.stats_top_domains), s.topDomains, nf)
        TopList(stringResource(R.string.stats_top_apps), s.topApps, nf)
        Spacer(Modifier.height(24.dp))
    }
}

/** Rough estimate: an average blocked ad request (script, image, video chunk) ≈ 30 KB. */
private fun formatMb(blocked: Int): String {
    val mb = blocked * 30.0 / 1024
    return if (mb < 10) String.format(java.util.Locale.US, "%.1f MB", mb) else "${mb.toInt()} MB"
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BarChart(values: List<Int>, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
        val slot = size.width / values.size
        val bar = slot * 0.7f
        values.forEachIndexed { i, v ->
            val x = i * slot + (slot - bar) / 2
            drawRoundRect(track, Offset(x, 0f), Size(bar, size.height), CornerRadius(4f, 4f))
            val h = size.height * v / max
            if (h > 0) drawRoundRect(color, Offset(x, size.height - h), Size(bar, h), CornerRadius(4f, 4f))
        }
    }
}

@Composable
private fun TopList(title: String, items: List<Pair<String, Int>>, nf: NumberFormat) {
    if (items.isEmpty()) return
    SectionTitle(title)
    items.forEach { (name, n) ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(nf.format(n), color = BlockedColor)
        }
    }
}
