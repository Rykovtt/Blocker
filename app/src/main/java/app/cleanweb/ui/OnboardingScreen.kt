package app.cleanweb.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cleanweb.R
import app.cleanweb.system.DeviceChecks

/** Three steps: what the app does → VPN permission → Samsung/Android settings. */
@Composable
fun OnboardingScreen(vm: MainViewModel, startProtection: ((Boolean) -> Unit) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    val privateDns by vm.privateDns.collectAsStateWithLifecycle()
    val batteryOk by vm.batteryOk.collectAsStateWithLifecycle()
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { step = 2; vm.refreshChecks() }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
            LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth())
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(painterResource(R.drawable.ic_shield), null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
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
                        CheckRow(
                            stringResource(R.string.check_private_dns_title),
                            stringResource(if (privateDns) R.string.onb3_private_dns_on else R.string.onb3_ok),
                            if (privateDns) stringResource(R.string.action_open_settings) else null,
                        ) { DeviceChecks.startSafely(ctx, DeviceChecks.privateDnsSettingsIntent(), DeviceChecks.fallbackNetworkSettingsIntent()) }
                        CheckRow(
                            stringResource(R.string.check_battery_title),
                            stringResource(if (batteryOk) R.string.onb3_ok else R.string.onb3_battery_off),
                            if (batteryOk) null else stringResource(R.string.action_allow),
                        ) { DeviceChecks.startSafely(ctx, DeviceChecks.batteryOptimizationIntent(ctx), DeviceChecks.appDetailsIntent(ctx)) }
                        Body(stringResource(R.string.onb3_samsung))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) TextButton(onClick = { step-- }) { Text(stringResource(R.string.back)) } else Spacer(Modifier.size(1.dp))
                when (step) {
                    0 -> Button(onClick = { step = 1 }) { Text(stringResource(R.string.next)) }
                    1 -> Button(onClick = {
                        startProtection { ok ->
                            if (!ok) return@startProtection
                            if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else { step = 2; vm.refreshChecks() }
                        }
                    }) { Text(stringResource(R.string.onb2_button)) }
                    else -> Button(onClick = { vm.finishOnboarding() }) { Text(stringResource(R.string.onb_finish)) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge)

@Composable
private fun CheckRow(title: String, state: String, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(state, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) OutlinedButton(onClick = onAction) { Text(action) }
    }
}
