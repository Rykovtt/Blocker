package app.cleanweb.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
    val c = Cw.colors

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GradientButton(
                stringResource(if (updating) R.string.filters_updating else R.string.filters_update_now),
                { vm.updateFilters() },
                Modifier.weight(1f),
                enabled = !updating,
                leading = {
                    if (updating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = c.textMuted)
                    else Icon(Icons.Filled.Refresh, null, tint = Color.White)
                },
            )
            NeuButton(stringResource(R.string.action_add), { adding = true }, icon = Icons.Filled.Add, accentIcon = true)
        }

        SectionLabel(stringResource(R.string.filters_lists))
        NeuGroup {
            lists.forEachIndexed { i, list ->
                if (i > 0) RowDivider()
                FilterListRow(list, onToggle = { vm.setListEnabled(list.id, it) }, onDelete = { vm.removeList(list.id) })
            }
        }

        SectionLabel(stringResource(R.string.filters_own))
        NeuGroup {
            NeuRow(
                stringResource(R.string.filters_my_rules), stringResource(R.string.filters_my_rules_hint),
                onClick = { editor = Editor.RULES },
            ) { Icon(Icons.Filled.Edit, stringResource(R.string.action_edit), tint = c.accentEnd) }
            RowDivider()
            NeuRow(
                stringResource(R.string.filters_whitelist), stringResource(R.string.filters_whitelist_hint),
                onClick = { editor = Editor.WHITELIST },
            ) { Icon(Icons.Filled.Edit, stringResource(R.string.action_edit), tint = c.accentEnd) }
        }
        Spacer(Modifier.height(24.dp))
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
    val c = Cw.colors
    val updated = if (list.lastUpdated > 0) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(list.lastUpdated))
    } else null
    val subtitle = when {
        list.lastError != null && updated == null -> stringResource(R.string.filters_error, list.lastError)
        updated != null -> stringResource(R.string.filters_list_info, list.ruleCount, updated)
        else -> stringResource(R.string.filters_not_downloaded)
    }
    NeuRow(
        list.name,
        subtitle,
        subtitleColor = if (list.lastError != null && updated == null) c.blocked else null,
        onClick = { onToggle(!list.enabled) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!list.builtin) {
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete), tint = c.textMuted) }
                Spacer(Modifier.width(4.dp))
            }
            NeuSwitch(checked = list.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun AddListDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var url by remember { mutableStateOf("https://") }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp),
        title = { Text(stringResource(R.string.filters_add_url)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(url, { url = it }, label = { Text("URL") }, singleLine = true, shape = FieldShape, colors = cwTextFieldColors())
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.filters_list_name)) }, singleLine = true, shape = FieldShape, colors = cwTextFieldColors())
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
        shape = RoundedCornerShape(32.dp),
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text ?: "",
                onValueChange = { text = it },
                placeholder = { Text(hint, fontFamily = FontFamily.Monospace) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                enabled = text != null,
                shape = FieldShape,
                colors = cwTextFieldColors(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text ?: "") }, enabled = text != null) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
