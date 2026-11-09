package app.cleanweb.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cleanweb.R
import app.cleanweb.samsung.SamsungBlocker
import app.cleanweb.system.DeviceChecks
import app.cleanweb.vpn.BlockerVpnService
import app.cleanweb.vpn.ProtectionStatus
import kotlinx.coroutines.delay
import java.text.NumberFormat

@Composable
fun HomeScreen(vm: MainViewModel, startProtection: ((Boolean) -> Unit) -> Unit, modifier: Modifier, openStats: () -> Unit) {
    var pushHelp by remember { mutableStateOf(false) }
    val update by vm.update.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val c = Cw.colors
    val status by vm.status.collectAsStateWithLifecycle()
    val stats by vm.homeStats.collectAsStateWithLifecycle()
    val rules by vm.ruleCount.collectAsStateWithLifecycle()
    val loading by vm.engineLoading.collectAsStateWithLifecycle()
    val error by vm.lastError.collectAsStateWithLifecycle()
    val privateDns by vm.privateDns.collectAsStateWithLifecycle()
    val batteryOk by vm.batteryOk.collectAsStateWithLifecycle()
    val pausedUntil by vm.pausedUntil.collectAsStateWithLifecycle()
    val risky by vm.riskyBlocks.collectAsStateWithLifecycle()
    val nf = remember { NumberFormat.getIntegerInstance() }

    val on = status != ProtectionStatus.STOPPED
    val running = status == ProtectionStatus.RUNNING
    val share = if (stats.today.total == 0) 0f else stats.today.blocked.toFloat() / stats.today.total

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // ---- Protection card: ring + on/off button ----
        NeuCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), radius = 36.dp, padding = 24.dp) {
            Text(
                stringResource(
                    when (status) {
                        ProtectionStatus.RUNNING -> R.string.home_status_on
                        ProtectionStatus.PAUSED -> R.string.home_status_paused
                        ProtectionStatus.STARTING -> R.string.home_status_starting
                        ProtectionStatus.STOPPED -> R.string.home_status_off
                    },
                ).uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
                color = c.textMuted,
            )
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                GradientRing(if (on) share.coerceAtLeast(0.02f) else 0f, Modifier.size(220.dp), stroke = 14.dp, active = running)
                Box(
                    Modifier
                        .size(150.dp)
                        .neu(c, 75.dp, elevation = 10.dp)
                        .clip(CircleShape)
                        .clickable(
                            role = Role.Switch,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = androidx.compose.material3.ripple(),
                        ) { if (on) BlockerVpnService.stop(ctx) else startProtection {} },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconTile(
                            painter = painterResource(R.drawable.ic_shield),
                            accent = running,
                            size = 54.dp,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(if (on) R.string.home_button_on else R.string.home_button_off),
                            style = MaterialTheme.typography.titleLarge,
                            color = if (running) c.text else c.textMuted,
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        nf.format(stats.today.blocked),
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = c.text,
                    )
                    Text(stringResource(R.string.home_blocked_today), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("▲ ${(share * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = c.text)
                    Text(
                        stringResource(R.string.home_of_requests, nf.format(stats.today.total)),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textMuted,
                        textAlign = TextAlign.End,
                    )
                }
            }
            if (status == ProtectionStatus.PAUSED) {
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(pausedUntil) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
                val left = ((pausedUntil - now) / 1000).coerceAtLeast(0)
                Text(
                    stringResource(R.string.home_paused_left, left / 60, left % 60),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.warning,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            when (status) {
                ProtectionStatus.RUNNING -> NeuButton(
                    stringResource(R.string.action_pause_5), { BlockerVpnService.pause(ctx) },
                    Modifier.fillMaxWidth().padding(top = 18.dp), painter = painterResource(R.drawable.ic_pause),
                )
                ProtectionStatus.PAUSED -> GradientButton(
                    stringResource(R.string.action_resume), { BlockerVpnService.resume(ctx) },
                    Modifier.fillMaxWidth().padding(top = 18.dp),
                    leading = { Icon(Icons.Filled.PlayArrow, null, tint = Color.White) },
                )
                ProtectionStatus.STOPPED -> GradientButton(
                    stringResource(R.string.home_turn_on), { startProtection {} },
                    Modifier.fillMaxWidth().padding(top = 18.dp),
                )
                ProtectionStatus.STARTING -> Unit
            }
        }

        // ---- Problems that need the user ----
        if (risky.isNotEmpty()) {
            AlertCard(
                stringResource(R.string.risky_title),
                stringResource(R.string.risky_body, risky.joinToString(", ")),
                stringResource(R.string.risky_action),
                { vm.unblockRisky(risky) },
                error = true,
            )
        }
        error?.let { AlertCard(stringResource(R.string.home_problem), stringResource(it), null, {}, error = true) }
        if (privateDns) {
            AlertCard(
                stringResource(R.string.check_private_dns_title), stringResource(R.string.check_private_dns_body),
                stringResource(R.string.action_open_settings),
                { DeviceChecks.startSafely(ctx, DeviceChecks.privateDnsSettingsIntent(), DeviceChecks.fallbackNetworkSettingsIntent()) },
                error = true,
            )
        }
        if (!batteryOk) {
            AlertCard(
                stringResource(R.string.check_battery_title), stringResource(R.string.check_battery_body),
                stringResource(R.string.action_allow),
                { DeviceChecks.startSafely(ctx, DeviceChecks.batteryOptimizationIntent(ctx), DeviceChecks.appDetailsIntent(ctx)) },
            )
        }
        update?.let { u ->
            AlertCard(
                stringResource(R.string.update_title, u.version), stringResource(R.string.update_body),
                stringResource(R.string.update_download),
                { DeviceChecks.startSafely(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(u.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
            )
        }
        val samsungInstalled = remember { SamsungBlocker.isBrowserInstalled(ctx) }
        val samsungUsed = remember(stats) { SamsungBlocker.lastRead(ctx) > 0 }
        if (samsungInstalled && !samsungUsed) {
            AlertCard(
                stringResource(R.string.samsung_card_title), stringResource(R.string.samsung_card_body),
                stringResource(R.string.samsung_card_action),
                {
                    val i = SamsungBlocker.settingsIntent(ctx)
                    if (i != null) DeviceChecks.startSafely(ctx, i)
                    else DeviceChecks.startSafely(
                        ctx,
                        Intent(Intent.ACTION_VIEW, Uri.parse("internet://settings")).setPackage("com.sec.android.app.sbrowser").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        ctx.packageManager.getLaunchIntentForPackage("com.sec.android.app.sbrowser"),
                    )
                },
            )
        }

        // ---- Last 7 days ----
        NeuCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), onClick = openStats) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(0.42f)) {
                    Text(stringResource(R.string.home_last_7_days), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "▲ " + nf.format(stats.week.sumOf { it.blocked }),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = c.text,
                    )
                    Text(stringResource(R.string.stats_title) + " →", style = MaterialTheme.typography.bodySmall, color = c.accentEnd)
                }
                AreaChart(stats.week.map { it.blocked }.ifEmpty { listOf(0, 0) }, Modifier.weight(0.58f).height(72.dp))
            }
        }

        // ---- Actions ----
        SectionLabel(stringResource(R.string.home_actions))
        NeuButton(
            stringResource(R.string.qb_home_button), { ctx.startActivity(QuickBlockActivity.intent(ctx)) },
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), icon = Icons.Filled.Add, accentIcon = true,
        )
        Text(
            stringResource(R.string.qb_home_hint),
            style = MaterialTheme.typography.bodySmall,
            color = c.textMuted,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
        )
        NeuButton(
            stringResource(R.string.push_card_action), { pushHelp = true },
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), icon = Icons.Filled.Notifications,
        )
        NeuButton(
            stringResource(R.string.stats_title), openStats,
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), painter = painterResource(R.drawable.ic_chart),
        )

        // ---- Mode and rules ----
        NeuCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
            Text(stringResource(R.string.home_mode_title).uppercase(), style = MaterialTheme.typography.labelLarge, color = c.textMuted)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.home_mode_dns), style = MaterialTheme.typography.bodyMedium, color = c.text)
            Spacer(Modifier.height(6.dp))
            Text(
                if (loading) stringResource(R.string.home_rules_loading) else stringResource(R.string.home_rules, nf.format(rules)),
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
            )
            Text(stringResource(R.string.home_mode_hardcoded), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }

        if (stats.topBlocked.isNotEmpty()) {
            SectionLabel(stringResource(R.string.home_top_blocked))
            NeuGroup {
                stats.topBlocked.forEachIndexed { i, (domain, n) ->
                    if (i > 0) RowDivider()
                    NeuRow(domain) {
                        Text(nf.format(n), color = c.blocked, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (pushHelp) SiteNotificationsDialog { pushHelp = false }
}
