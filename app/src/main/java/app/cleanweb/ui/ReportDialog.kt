package app.cleanweb.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.cleanweb.BuildConfig
import app.cleanweb.R

/**
 * "Report a missed ad": the user fills in the page address and, if they want, picks a screenshot.
 * Nothing is sent by the app itself: the report goes out through the app the user chooses (mail, messenger).
 */
@Composable
fun ReportDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var url by remember { mutableStateOf("https://") }
    var note by remember { mutableStateOf("") }
    var screenshot by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { screenshot = it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.report_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.report_url)) }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.report_note)) })
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text(stringResource(if (screenshot == null) R.string.report_add_screenshot else R.string.report_screenshot_added))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val body = "URL: $url\n\n$note\n\nCleanWeb ${BuildConfig.VERSION_NAME}, Android ${android.os.Build.VERSION.RELEASE}, ${android.os.Build.MODEL}"
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = if (screenshot != null) "image/*" else "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "CleanWeb: missed ad")
                    putExtra(Intent.EXTRA_TEXT, body)
                    if (BuildConfig.REPORT_EMAIL.isNotEmpty()) putExtra(Intent.EXTRA_EMAIL, arrayOf(BuildConfig.REPORT_EMAIL))
                    screenshot?.let { putExtra(Intent.EXTRA_STREAM, it); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                }
                runCatching { ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.report_title))) }
                onDismiss()
            }) { Text(stringResource(R.string.report_send)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
