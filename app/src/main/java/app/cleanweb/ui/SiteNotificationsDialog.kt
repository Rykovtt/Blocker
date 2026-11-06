package app.cleanweb.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.cleanweb.R

private val BROWSERS = listOf(
    "com.android.chrome", "com.sec.android.app.sbrowser", "org.mozilla.firefox", "com.opera.browser",
    "com.opera.mini.native", "com.yandex.browser", "com.brave.browser", "com.microsoft.emmx", "com.UCMobile.intl",
    "com.mi.globalbrowser", "com.kiwibrowser.browser", "com.duckduckgo.mobile.android",
)

private fun installedBrowsers(ctx: Context): List<Pair<String, String>> = BROWSERS.mapNotNull { pkg ->
    try {
        val info = ctx.packageManager.getApplicationInfo(pkg, 0)
        pkg to ctx.packageManager.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}

/**
 * Ad "pop-ups" that appear over other apps are usually site notifications a page tricked
 * the user into allowing. DNS filtering cannot stop them; turning them off in the browser does.
 */
@Composable
fun SiteNotificationsDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val browsers = remember { installedBrowsers(ctx) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
        title = { Text(stringResource(R.string.push_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.push_body), style = MaterialTheme.typography.bodyMedium)
                browsers.forEach { (pkg, label) ->
                    NeuButton(label,
                        {
                            val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { ctx.startActivity(i) }
                        },
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        icon = androidx.compose.material.icons.Icons.Filled.Notifications,
                        accentIcon = true,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.onb_finish)) } },
    )
}
