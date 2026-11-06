package app.cleanweb.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cleanweb.R
import app.cleanweb.system.DeviceChecks
import app.cleanweb.vpn.BlockerVpnService
import app.cleanweb.vpn.ProtectionStatus
import kotlinx.coroutines.delay
import java.text.NumberFormat

@Composable
fun HomeScreen(vm: MainViewModel, startProtection: ((Boolean) -> Unit) -> Unit, modifier: Modifier, openStats: () -> Unit) {
    var reporting by remember { mutableStateOf(false) }
    var pushHelp by remember { mutableStateOf(false) }
    val update by vm.update.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val status by vm.status.collectAsStateWithLifecycle()
    val stats by vm.homeStats.collectAsStateWithLifecycle()
    val rules by vm.ruleCount.collectAsStateWithLifecycle()
    val loading by vm.engineLoading.collectAsStateWithLifecycle()
    val error by vm.lastError.collectAsStateWithLifecycle()
    val privateDns by vm.privateDns.collectAsStateWithLifecycle()
    val batteryOk by vm.batteryOk.collectAsStateWithLifecycle()
    val pausedUntil by vm.pausedUntil.collectAsStateWithLifecycle()
    val nf = remember { NumberFormat.getIntegerInstance() }

    val on = status == ProtectionStatus.RUNNING || status == ProtectionStatus.PAUSED || status == ProtectionStatus.STARTING

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(24.dp))
        Surface(
            onClick = { if (on) BlockerVpnService.stop(ctx) else startProtection {} },
            shape = CircleShape,
            color = if (status == ProtectionStatus.RUNNING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (status == ProtectionStatus.RUNNING) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            shadowElevation = 6.dp,
            modifier = Modifier.size(184.dp).align(Alignment.CenterHorizontally),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(painterResource(R.drawable.ic_shield), null, Modifier.size(64.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(if (on) R.string.home_button_on else R.string.home_button_off),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(
                when (status) {
                    ProtectionStatus.RUNNING -> R.string.home_status_on
                    ProtectionStatus.PAUSED -> R.string.home_status_paused
                    ProtectionStatus.STARTING -> R.string.home_status_starting
                    ProtectionStatus.STOPPED -> R.string.home_status_off
                },
            ),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        if (status == ProtectionStatus.PAUSED) {
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(pausedUntil) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
            val left = ((pausedUntil - now) / 1000).coerceAtLeast(0)
            Text(
                stringResource(R.string.home_paused_left, left / 60, left % 60),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        Spacer(Modifier.height(8.dp))
        if (status == ProtectionStatus.RUNNING) {
            OutlinedButton(onClick = { BlockerVpnService.pause(ctx) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.action_pause_5))
            }
        } else if (status == ProtectionStatus.PAUSED) {
            OutlinedButton(onClick = { BlockerVpnService.resume(ctx) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.action_resume))
            }
        }
        Spacer(Modifier.height(16.dp))

        update?.let { u ->
            AlertCard(
                stringResource(R.string.update_title, u.version), stringResource(R.string.update_body),
                stringResource(R.string.update_download),
                { DeviceChecks.startSafely(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(u.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
            )
        }
        error?.let { AlertCard(stringResource(R.string.home_problem), stringResource(it), null, {}, error = true) }
        if (privateDns) {
            AlertCard(
                stringResource(R.string.check_private_dns_title), stringResource(R.string.check_private_dns_body),
                stringResource(R.string.action_open_settings),
                { DeviceChecks.startSafely(ctx, DeviceChecks.privateDnsSettingsIntent(), DeviceChecks.fallbackNetworkSettingsIntent()) },
            )
        }
        if (!batteryOk) {
            AlertCard(
                stringResource(R.string.check_battery_title), stringResource(R.string.check_battery_body),
                stringResource(R.string.action_allow),
                { DeviceChecks.startSafely(ctx, DeviceChecks.batteryOptimizationIntent(ctx), DeviceChecks.appDetailsIntent(ctx)) },
            )
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(stringResource(R.string.home_blocked_today), nf.format(stats.today.blocked), Modifier.weight(1f))
            StatCard(stringResource(R.string.home_queries_today), nf.format(stats.today.total), Modifier.weight(1f))
        }
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.home_mode_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.home_mode_dns), style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (loading) stringResource(R.string.home_rules_loading) else stringResource(R.string.home_rules, nf.format(rules)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(R.string.home_mode_hardcoded), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (stats.topBlocked.isNotEmpty()) {
            SectionTitle(stringResource(R.string.home_top_blocked))
            stats.topBlocked.forEach { (domain, n) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(domain, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(nf.format(n), color = BlockedColor)
                }
            }
        }
        Button(
            onClick = { ctx.startActivity(QuickBlockActivity.intent(ctx)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        ) { Text(stringResource(R.string.qb_home_button)) }
        Text(
            stringResource(R.string.qb_home_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        AlertCard(
            stringResource(R.string.push_card_title), stringResource(R.string.push_card_body),
            stringResource(R.string.push_card_action), { pushHelp = true },
        )
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = openStats, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.stats_title)) }
            OutlinedButton(onClick = { reporting = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.report_button)) }
        }
        Spacer(Modifier.height(16.dp))
    }
    if (reporting) ReportDialog { reporting = false }
    if (pushHelp) SiteNotificationsDialog { pushHelp = false }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
