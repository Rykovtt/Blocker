package app.cleanweb.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cleanweb.R

@Composable
fun AppsScreen(vm: MainViewModel, modifier: Modifier) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val c = Cw.colors
    LaunchedEffect(Unit) { vm.loadApps() }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text(stringResource(R.string.apps_search)) },
            singleLine = true,
            shape = FieldShape,
            colors = cwTextFieldColors(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Text(
            stringResource(R.string.apps_hint),
            style = MaterialTheme.typography.bodySmall,
            color = c.textMuted,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accentEnd) }
            return@Column
        }
        val q = query.trim().lowercase()
        val shown = if (q.isEmpty()) list else list.filter { it.label.lowercase().contains(q) || it.pkg.contains(q) }
        LazyColumn(Modifier.fillMaxSize()) {
            item { Spacer(Modifier.height(6.dp)) }
            itemsIndexed(shown, key = { _, a -> a.pkg }) { i, app ->
                val first = i == 0
                val last = i == shown.lastIndex
                val shape = RoundedCornerShape(
                    topStart = if (first) 28.dp else 0.dp, topEnd = if (first) 28.dp else 0.dp,
                    bottomStart = if (last) 28.dp else 0.dp, bottomEnd = if (last) 28.dp else 0.dp,
                )
                val filtered = app.pkg !in settings.excludedApps
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(shape).background(c.surface)) {
                    if (!first) RowDivider()
                    NeuRow(
                        app.label,
                        stringResource(if (filtered) R.string.apps_filtered else R.string.apps_excluded),
                        subtitleColor = if (filtered) c.allowed else c.textMuted,
                        leading = {
                            app.icon?.let {
                                Image(it.asImageBitmap(), null, Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)))
                            } ?: Box(Modifier.size(42.dp))
                        },
                        onClick = { vm.setAppFiltered(app.pkg, !filtered) },
                    ) { NeuSwitch(checked = filtered, onCheckedChange = { vm.setAppFiltered(app.pkg, it) }) }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
