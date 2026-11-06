package app.cleanweb.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cleanweb.R
import app.cleanweb.system.DeviceChecks

/** Three steps: what the app does → VPN permission → Samsung/Android settings. */
@Composable
fun OnboardingScreen(vm: MainViewModel, startProtection: ((Boolean) -> Unit) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    val c = Cw.colors
    val privateDns by vm.privateDns.collectAsStateWithLifecycle()
    val batteryOk by vm.batteryOk.collectAsStateWithLifecycle()
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { step = 2; vm.refreshChecks() }
    val progress by animateFloatAsState((step + 1) / 3f, label = "progress")

    Column(Modifier.fillMaxSize().background(c.background).safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
        // Gradient progress bar with a knob, like the kit's slider.
        Box(Modifier.fillMaxWidth().height(8.dp).background(c.track, CircleShape)) {
            Box(Modifier.fillMaxWidth(progress).height(8.dp).background(c.accentHorizontal, CircleShape))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            IconTile(painter = painterResource(R.drawable.ic_shield), size = 76.dp)
            NeuCard(Modifier.fillMaxWidth(), radius = 36.dp, padding = 26.dp) {
                when (step) {
                    0 -> {
                        Title(stringResource(R.string.onb1_title))
                        Body(stringResource(R.string.onb1_body))
                    }
                    1 -> {
                        Title(stringResource(R.string.onb2_title))
                        Body(stringResource(R.string.onb2_body))
                    }
                    else -> {
                        Title(stringResource(R.string.onb3_title))
                        Body(stringResource(R.string.onb3_body))
                    }
                }
            }
            if (step == 2) {
                NeuGroup(Modifier.padding(horizontal = 0.dp)) {
                    CheckRow(
                        stringResource(R.string.check_private_dns_title),
                        stringResource(if (privateDns) R.string.onb3_private_dns_on else R.string.onb3_ok),
                        ok = !privateDns,
                        action = if (privateDns) stringResource(R.string.action_open_settings) else null,
                    ) { DeviceChecks.startSafely(ctx, DeviceChecks.privateDnsSettingsIntent(), DeviceChecks.fallbackNetworkSettingsIntent()) }
                    RowDivider()
                    CheckRow(
                        stringResource(R.string.check_battery_title),
                        stringResource(if (batteryOk) R.string.onb3_ok else R.string.onb3_battery_off),
                        ok = batteryOk,
                        action = if (batteryOk) null else stringResource(R.string.action_allow),
                    ) { DeviceChecks.startSafely(ctx, DeviceChecks.batteryOptimizationIntent(ctx), DeviceChecks.appDetailsIntent(ctx)) }
                }
                Text(stringResource(R.string.onb3_samsung), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) {
                TextButton(onClick = { step-- }) { Text(stringResource(R.string.back), color = c.textMuted) }
                Spacer(Modifier.width(12.dp))
            }
            val modifier = Modifier.weight(1f)
            when (step) {
                0 -> GradientButton(stringResource(R.string.next), { step = 1 }, modifier)
                1 -> GradientButton(stringResource(R.string.onb2_button), {
                    startProtection { ok ->
                        if (!ok) return@startProtection
                        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else { step = 2; vm.refreshChecks() }
                    }
                }, modifier)
                else -> GradientButton(stringResource(R.string.onb_finish), { vm.finishOnboarding() }, modifier)
            }
        }
    }
}

@Composable
private fun Title(text: String) =
    Text(text, style = MaterialTheme.typography.headlineSmall, color = Cw.colors.text, modifier = Modifier.padding(bottom = 14.dp))

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge, color = Cw.colors.textMuted)

@Composable
private fun CheckRow(title: String, state: String, ok: Boolean, action: String?, onAction: () -> Unit) {
    val c = Cw.colors
    NeuRow(title, state, subtitleColor = if (ok) c.allowed else c.blocked, onClick = if (action != null) onAction else null) {
        if (action != null) Text(action, color = c.accentEnd, style = MaterialTheme.typography.labelLarge)
    }
}
