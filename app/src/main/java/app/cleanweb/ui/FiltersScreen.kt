package app.cleanweb.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cleanweb.R
import app.cleanweb.data.FilterList
import java.text.DateFormat
import java.util.Date

private enum class Editor { RULES, WHITELIST }

@Composable
fun FiltersScreen(vm: MainViewModel, modifier: Modifier) {
    val lists by vm.lists.collectAsStateWithLifecycle()
    val updating by vm.updating.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<Editor?>(null) }

    LazyColumn(modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = { vm.updateFilters() }, enabled = !updating) {
                    if (updating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Refresh, null)
                    Text("  " + stringResource(if (updating) R.string.filters_updating else R.string.filters_update_now))
                }
                OutlinedButton(onClick = { adding = true }) {
                    Icon(Icons.Filled.Add, null)
                    Text(" " + stringResource(R.string.filters_add_url))
                }
            }
        }
        item { SectionTitle(stringResource(R.string.filters_lists)) }
        items(lists, key = { it.id }) { list ->
            FilterListRow(list, onToggle = { vm.setListEnabled(list.id, it) }, onDelete = { vm.removeList(list.id) })
            HorizontalDivider()
        }
        item { SectionTitle(stringResource(R.string.filters_own)) }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.filters_my_rules)) },
                supportingContent = { Text(stringResource(R.string.filters_my_rules_hint)) },
                modifier = Modifier.padding(0.dp),
                trailingContent = { TextButton(onClick = { editor = Editor.RULES }) { Text(stringResource(R.string.action_edit)) } },
            )
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.filters_whitelist)) },
                supportingContent = { Text(stringResource(R.string.filters_whitelist_hint)) },
                trailingContent = { TextButton(onClick = { editor = Editor.WHITELIST }) { Text(stringResource(R.string.action_edit)) } },
            )
        }
    }

    if (adding) AddListDialog(onDismiss = { adding = false }) { url, name -> vm.addList(url, name); adding = false }
    editor?.let { e ->
        TextEditorDialog(
            title = stringResource(if (e == Editor.RULES) R.string.filters_my_rules else R.string.filters_whitelist),
            hint = stringResource(if (e == Editor.RULES) R.string.filters_my_rules_example else R.string.filters_whitelist_example),
            load = { if (e == Editor.RULES) vm.userRules() else vm.whitelist() },
            onSave = { if (e == Editor.RULES) vm.saveUserRules(it) else vm.saveWhitelist(it); editor = null },
            onDismiss = { editor = null },
        )
    }
}

@Composable
private fun FilterListRow(list: FilterList, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val updated = if (list.lastUpdated > 0) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(list.lastUpdated))
    } else null
    ListItem(
        headlineContent = { Text(list.name) },
        supportingContent = {
            Column {
                Text(
                    when {
                        updated != null -> stringResource(R.string.filters_list_info, list.ruleCount, updated)
                        else -> stringResource(R.string.filters_not_downloaded)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                list.lastError?.let {
                    Text(stringResource(R.string.filters_error, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!list.builtin) IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
                Switch(checked = list.enabled, onCheckedChange = onToggle)
            }
        },
    )
}

@Composable
private fun AddListDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var url by remember { mutableStateOf("https://") }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filters_add_url)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(url, { url = it }, label = { Text("URL") }, singleLine = true)
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.filters_list_name)) }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(url, name) }, enabled = url.length > 10) { Text(stringResource(R.string.action_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun TextEditorDialog(
    title: String,
    hint: String,
    load: suspend () -> String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { text = load() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text ?: "",
                onValueChange = { text = it },
                placeholder = { Text(hint, fontFamily = FontFamily.Monospace) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                enabled = text != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text ?: "") }, enabled = text != null) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
