package app.cleanweb.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cleanweb.BuildConfig
import app.cleanweb.R
import app.cleanweb.data.ThemeMode
import app.cleanweb.data.Upstream
import app.cleanweb.system.DeviceChecks

@Composable
fun SettingsScreen(vm: MainViewModel, modifier: Modifier) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var custom by remember(s.customUpstream) { mutableStateOf(s.customUpstream) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle(stringResource(R.string.settings_dns))
        Upstream.entries.forEach { u ->
            RadioRow(
                text = if (u == Upstream.CUSTOM) stringResource(R.string.settings_dns_custom) else u.label,
                selected = s.upstream == u,
            ) { vm.setUpstream(u, custom) }
        }
        if (s.upstream == Upstream.CUSTOM) {
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it },
                label = { Text(stringResource(R.string.settings_dns_custom_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp)) {
                androidx.compose.material3.TextButton(onClick = { vm.setUpstream(Upstream.CUSTOM, custom) }) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }

        SectionTitle(stringResource(R.string.settings_general))
        SwitchRow(stringResource(R.string.settings_autostart), stringResource(R.string.settings_autostart_hint), s.autostart, vm::setAutostart)
        SwitchRow(stringResource(R.string.settings_wifi_only), stringResource(R.string.settings_wifi_only_hint), s.wifiOnlyUpdates, vm::setWifiOnly)
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_always_on)) },
            supportingContent = { Text(stringResource(R.string.settings_always_on_hint)) },
            modifier = Modifier.clickable { DeviceChecks.startSafely(ctx, DeviceChecks.vpnSettingsIntent()) },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.check_battery_title)) },
            supportingContent = { Text(stringResource(R.string.settings_battery_hint)) },
            modifier = Modifier.clickable {
                DeviceChecks.startSafely(ctx, DeviceChecks.batteryOptimizationIntent(ctx), DeviceChecks.appDetailsIntent(ctx))
            },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.check_private_dns_title)) },
            supportingContent = { Text(stringResource(R.string.settings_private_dns_hint)) },
            modifier = Modifier.clickable {
                DeviceChecks.startSafely(ctx, DeviceChecks.privateDnsSettingsIntent(), DeviceChecks.fallbackNetworkSettingsIntent())
            },
        )

        SectionTitle(stringResource(R.string.settings_log))
        SwitchRow(stringResource(R.string.settings_log_enabled), stringResource(R.string.settings_log_enabled_hint), s.logEnabled, vm::setLogEnabled)
        Text(stringResource(R.string.settings_log_retention), Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyLarge)
        listOf(1, 3, 7, 30).forEach { d ->
            RadioRow(stringResource(R.string.settings_days, d), s.logRetentionDays == d) { vm.setRetention(d) }
        }

        SectionTitle(stringResource(R.string.settings_appearance))
        RadioRow(stringResource(R.string.settings_theme_system), s.theme == ThemeMode.SYSTEM) { vm.setTheme(ThemeMode.SYSTEM) }
        RadioRow(stringResource(R.string.settings_theme_light), s.theme == ThemeMode.LIGHT) { vm.setTheme(ThemeMode.LIGHT) }
        RadioRow(stringResource(R.string.settings_theme_dark), s.theme == ThemeMode.DARK) { vm.setTheme(ThemeMode.DARK) }
        if (Build.VERSION.SDK_INT >= 33) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_language)) },
                supportingContent = { Text(stringResource(R.string.settings_language_hint)) },
                modifier = Modifier.clickable {
                    DeviceChecks.startSafely(
                        ctx,
                        Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
        }

        SectionTitle(stringResource(R.string.settings_about))
        ListItem(
            headlineContent = { Text(stringResource(R.string.app_name) + " " + BuildConfig.VERSION_NAME) },
            supportingContent = { Text(stringResource(R.string.settings_about_privacy)) },
        )
    }
}

@Composable
private fun RadioRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text)
    }
}
