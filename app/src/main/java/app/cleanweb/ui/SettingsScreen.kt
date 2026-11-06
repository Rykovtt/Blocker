package app.cleanweb.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cleanweb.BuildConfig
import app.cleanweb.R
import app.cleanweb.data.DnsProtocol
import app.cleanweb.data.ThemeMode
import app.cleanweb.data.Upstream
import app.cleanweb.system.DeviceChecks

@Composable
fun SettingsScreen(vm: MainViewModel, modifier: Modifier) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val c = Cw.colors
    var custom by remember(s.customUpstream) { mutableStateOf(s.customUpstream) }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.exportBackup(it) }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importBackup(it) }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // ---- DNS ----
        SectionLabel(stringResource(R.string.settings_dns))
        NeuCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
            ChipRow {
                Upstream.entries.forEach { u ->
                    NeuChip(s.upstream == u, if (u == Upstream.CUSTOM) stringResource(R.string.settings_dns_custom) else u.label) {
                        vm.setUpstream(u, custom)
                    }
                }
            }
            if (s.upstream != Upstream.CUSTOM) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.settings_protocol), color = c.textMuted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                ChipRow {
                    NeuChip(s.protocol == DnsProtocol.DOH, "DNS-over-HTTPS") { vm.setProtocol(DnsProtocol.DOH) }
                    NeuChip(s.protocol == DnsProtocol.DOT, "DNS-over-TLS") { vm.setProtocol(DnsProtocol.DOT) }
                }
            } else {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it },
                    label = { Text(stringResource(R.string.settings_dns_custom_hint)) },
                    singleLine = true,
                    shape = FieldShape,
                    colors = cwTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                GradientButton(stringResource(R.string.action_save), { vm.setUpstream(Upstream.CUSTOM, custom) }, Modifier.fillMaxWidth().padding(top = 14.dp))
            }
        }

        // ---- General ----
        SectionLabel(stringResource(R.string.settings_general))
        NeuGroup {
            SwitchRow(stringResource(R.string.settings_autostart), stringResource(R.string.settings_autostart_hint), s.autostart, vm::setAutostart)
            RowDivider()
            SwitchRow(stringResource(R.string.settings_wifi_only), stringResource(R.string.settings_wifi_only_hint), s.wifiOnlyUpdates, vm::setWifiOnly)
            RowDivider()
            LinkRow(stringResource(R.string.settings_always_on), stringResource(R.string.settings_always_on_hint)) {
                DeviceChecks.startSafely(ctx, DeviceChecks.vpnSettingsIntent())
            }
            RowDivider()
            LinkRow(stringResource(R.string.check_battery_title), stringResource(R.string.settings_battery_hint)) {
                DeviceChecks.startSafely(ctx, DeviceChecks.batteryOptimizationIntent(ctx), DeviceChecks.appDetailsIntent(ctx))
            }
            RowDivider()
            LinkRow(stringResource(R.string.check_private_dns_title), stringResource(R.string.settings_private_dns_hint)) {
                DeviceChecks.startSafely(ctx, DeviceChecks.privateDnsSettingsIntent(), DeviceChecks.fallbackNetworkSettingsIntent())
            }
        }

        // ---- Log ----
        SectionLabel(stringResource(R.string.settings_log))
        NeuGroup {
            SwitchRow(stringResource(R.string.settings_log_enabled), stringResource(R.string.settings_log_enabled_hint), s.logEnabled, vm::setLogEnabled)
            RowDivider()
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text(stringResource(R.string.settings_log_retention), color = c.text, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(10.dp))
                ChipRow {
                    listOf(1, 3, 7, 30).forEach { d ->
                        NeuChip(s.logRetentionDays == d, stringResource(R.string.settings_days, d)) { vm.setRetention(d) }
                    }
                }
            }
        }

        // ---- Appearance ----
        SectionLabel(stringResource(R.string.settings_appearance))
        NeuCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
            ChipRow {
                NeuChip(s.theme == ThemeMode.SYSTEM, stringResource(R.string.settings_theme_system)) { vm.setTheme(ThemeMode.SYSTEM) }
                NeuChip(s.theme == ThemeMode.LIGHT, stringResource(R.string.settings_theme_light)) { vm.setTheme(ThemeMode.LIGHT) }
                NeuChip(s.theme == ThemeMode.DARK, stringResource(R.string.settings_theme_dark)) { vm.setTheme(ThemeMode.DARK) }
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            NeuGroup {
                LinkRow(stringResource(R.string.settings_language), stringResource(R.string.settings_language_hint)) {
                    DeviceChecks.startSafely(
                        ctx,
                        Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }

        // ---- Backup ----
        SectionLabel(stringResource(R.string.settings_backup))
        NeuGroup {
            LinkRow(stringResource(R.string.settings_backup_save), stringResource(R.string.settings_backup_save_hint)) {
                saveBackup.launch("cleanweb-backup.json")
            }
            RowDivider()
            LinkRow(stringResource(R.string.settings_backup_restore), stringResource(R.string.settings_backup_restore_hint)) {
                openBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
        }

        // ---- About ----
        SectionLabel(stringResource(R.string.settings_about))
        NeuGroup {
            LinkRow(stringResource(R.string.settings_check_updates), null) { vm.checkUpdates(manual = true) }
            RowDivider()
            NeuRow(stringResource(R.string.app_name) + " " + BuildConfig.VERSION_NAME, stringResource(R.string.settings_about_privacy))
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    // Extra vertical padding leaves room for the chips' soft shadows.
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun LinkRow(title: String, subtitle: String?, onClick: () -> Unit) {
    NeuRow(title, subtitle, onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Cw.colors.textMuted)
    }
}
