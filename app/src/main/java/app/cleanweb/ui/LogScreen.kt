package app.cleanweb.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cleanweb.R
import app.cleanweb.data.LogEntry
import app.cleanweb.data.QueryStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogScreen(vm: MainViewModel, modifier: Modifier) {
    val entries by vm.logEntries.collectAsStateWithLifecycle()
    val filter by vm.logFilter.collectAsStateWithLifecycle()
    val search by vm.logSearch.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<LogEntry?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = search,
                onValueChange = { vm.logSearch.value = it },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text(stringResource(R.string.log_search)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.log_clear)) }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(filter == null, { vm.logFilter.value = null }, { Text(stringResource(R.string.log_all)) })
            FilterChip(filter == QueryStatus.BLOCKED, { vm.logFilter.value = QueryStatus.BLOCKED }, { Text(stringResource(R.string.log_blocked)) })
            FilterChip(filter == QueryStatus.ALLOWED, { vm.logFilter.value = QueryStatus.ALLOWED }, { Text(stringResource(R.string.log_allowed)) })
        }
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.log_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(32.dp))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries) { e ->
                    LogRow(e, fmt.format(Date(e.time))) { selected = e }
                    HorizontalDivider()
                }
            }
        }
    }

    selected?.let { e -> DomainDialog(e, vm) { selected = null } }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.log_clear)) },
            text = { Text(stringResource(R.string.log_clear_confirm)) },
            confirmButton = { TextButton(onClick = { vm.clearLog(); confirmClear = false }) { Text(stringResource(R.string.log_clear)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogRow(e: LogEntry, time: String, onSelect: () -> Unit) {
    val (color, label) = when (e.status) {
        QueryStatus.BLOCKED -> BlockedColor to R.string.log_status_blocked
        QueryStatus.ALLOWED -> AllowedColor to R.string.log_status_allowed
        QueryStatus.WHITELISTED -> WhitelistColor to R.string.log_status_whitelisted
        QueryStatus.FAILED -> MaterialTheme.colorScheme.error to R.string.log_status_failed
    }
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onSelect, onLongClick = onSelect).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.domain, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val type = when (e.qtype) { 1 -> "A"; 28 -> "AAAA"; 65 -> "HTTPS"; 5 -> "CNAME"; else -> "type ${e.qtype}" }
            Text(
                listOfNotNull(time, e.app, type).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun DomainDialog(e: LogEntry, vm: MainViewModel, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(e.domain, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = { Text(stringResource(R.string.log_domain_hint)) },
        confirmButton = {
            if (e.status == QueryStatus.BLOCKED) {
                TextButton(onClick = { vm.allowDomain(e.domain); onDismiss() }) { Text(stringResource(R.string.action_allow_domain)) }
            } else {
                TextButton(onClick = { vm.blockDomain(e.domain); onDismiss() }) { Text(stringResource(R.string.action_block_domain)) }
            }
        },
        dismissButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(e.domain)); onDismiss() }) { Text(stringResource(R.string.action_copy)) }
        },
    )
}
