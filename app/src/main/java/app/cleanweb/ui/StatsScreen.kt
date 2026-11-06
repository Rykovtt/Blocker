package app.cleanweb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cleanweb.R
import java.text.NumberFormat
import java.util.Locale

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
    val c = Cw.colors

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatsPeriod.entries.forEach { p -> NeuChip(period == p, stringResource(p.label)) { period = p } }
        }
        val s = stats ?: return@Column
        val share = if (s.total == 0) 0f else s.blocked.toFloat() / s.total

        // Share of blocked requests: number on the left, ring on the right.
        NeuCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), radius = 36.dp, padding = 24.dp) {
            Text(stringResource(R.string.stats_blocked).uppercase(), style = MaterialTheme.typography.labelLarge, color = c.textMuted)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(nf.format(s.blocked), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = c.text)
                    Text("▲ ${(share * 100).toInt()}% " + stringResource(R.string.stats_share), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    Spacer(Modifier.height(18.dp))
                    Legend(c.accentEnd, stringResource(R.string.stats_blocked), nf.format(s.blocked))
                    Spacer(Modifier.height(8.dp))
                    Legend(c.outline, stringResource(R.string.stats_allowed), nf.format(s.total - s.blocked))
                }
                Box(contentAlignment = Alignment.Center) {
                    GradientRing(share, Modifier.size(132.dp), stroke = 12.dp)
                    Box(Modifier.size(84.dp).neu(c, 42.dp, elevation = 6.dp), contentAlignment = Alignment.Center) {
                        Text("${(share * 100).toInt()}%", style = MaterialTheme.typography.titleLarge, color = c.text)
                    }
                }
            }
        }

        // Activity chart.
        NeuCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), radius = 36.dp, padding = 24.dp) {
            Text(
                stringResource(if (period == StatsPeriod.DAY) R.string.stats_by_hour else R.string.stats_by_day),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textMuted,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.stats_max, nf.format(s.series.maxOfOrNull { it.blocked } ?: 0)),
                style = MaterialTheme.typography.titleSmall,
                color = c.text,
            )
            Spacer(Modifier.height(16.dp))
            AreaChart(s.series.map { it.blocked }, Modifier.fillMaxWidth().height(150.dp))
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            NeuCard(Modifier.weight(1f), padding = 18.dp) {
                Text(nf.format(s.total), style = MaterialTheme.typography.titleLarge, color = c.text)
                Text(stringResource(R.string.home_queries_today_generic), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
            NeuCard(Modifier.weight(1f), padding = 18.dp) {
                Text("≈ " + formatMb(s.blocked), style = MaterialTheme.typography.titleLarge, color = c.text)
                Text(stringResource(R.string.stats_saved), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
        }
        Text(
            stringResource(R.string.stats_saved_hint),
            style = MaterialTheme.typography.bodySmall,
            color = c.textMuted,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
        )

        TopList(stringResource(R.string.stats_top_domains), s.topDomains, nf)
        TopList(stringResource(R.string.stats_top_apps), s.topApps, nf)
        Spacer(Modifier.height(28.dp))
    }
}

/** Rough estimate: an average blocked ad request (script, image, video chunk) ≈ 30 KB. */
private fun formatMb(blocked: Int): String {
    val mb = blocked * 30.0 / 1024
    return if (mb < 10) String.format(Locale.US, "%.1f MB", mb) else "${mb.toInt()} MB"
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, label: String, value: String) {
    val c = Cw.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            Text(value, style = MaterialTheme.typography.labelLarge, color = c.text)
        }
    }
}

@Composable
private fun TopList(title: String, items: List<Pair<String, Int>>, nf: NumberFormat) {
    if (items.isEmpty()) return
    val c = Cw.colors
    SectionLabel(title)
    NeuGroup {
        val max = items.maxOf { it.second }.coerceAtLeast(1)
        items.forEachIndexed { i, (name, n) ->
            if (i > 0) RowDivider()
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f), color = c.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(nf.format(n), color = c.text, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(8.dp))
                // Slider-style bar like the kit's "71%" control.
                Box(Modifier.fillMaxWidth().height(6.dp).background(c.track, CircleShape)) {
                    Box(Modifier.fillMaxWidth(n.toFloat() / max).height(6.dp).background(c.accentHorizontal, CircleShape))
                }
            }
        }
    }
}
