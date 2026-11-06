package app.cleanweb.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cleanweb.AppGraph
import app.cleanweb.R
import app.cleanweb.filter.Domains
import app.cleanweb.vpn.BlockerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Block this ad": opened from the browser's Share menu (with the ad page's address),
 * from the notification, or from the home screen. Lists what was contacted in the last
 * few minutes, grouped by site, so the user can block the one that showed the ad.
 */
class QuickBlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val shared = if (intent?.action == Intent.ACTION_SEND) {
            Domains.hostsInText(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty() + " " + intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty())
        } else emptyList()
        val g = AppGraph.get(this)
        setContent {
            val settings by g.settings.state.collectAsStateWithLifecycle()
            CleanWebTheme(settings.theme) { QuickBlockScreen(shared, onClose = { finish() }) }
        }
    }

    companion object {
        fun intent(context: Context) = Intent(context, QuickBlockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

private class SiteRow(val site: String, val hosts: List<String>, val app: String?, val lastSeen: Long, val blocked: Boolean)

@Composable
private fun QuickBlockScreen(shared: List<String>, onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val g = remember { AppGraph.get(ctx) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var rows by remember { mutableStateOf<List<SiteRow>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val changes by g.log.changes.collectAsStateWithLifecycle()
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    LaunchedEffect(changes, refresh) {
        rows = withContext(Dispatchers.IO) {
            val recent = g.log.recentDomains(System.currentTimeMillis() - WINDOW_MS, 300)
            recent.groupBy { Domains.site(it.domain) }.map { (site, list) ->
                SiteRow(
                    site = site,
                    hosts = list.map { it.domain },
                    app = list.firstNotNullOfOrNull { it.app },
                    lastSeen = list.maxOf { it.lastSeen },
                    blocked = g.filters.isBlocked(site, BlockerState.engine) || list.all { it.blocked },
                )
            }.sortedByDescending { it.lastSeen }
        }
    }

    fun block(site: String) = scope.launch {
        withContext(Dispatchers.IO) { g.filters.blockDomain(site) }
        refresh++
        val r = snackbar.showSnackbar(ctx.getString(R.string.qb_blocked, site), ctx.getString(R.string.qb_undo), duration = androidx.compose.material3.SnackbarDuration.Long)
        if (r == SnackbarResult.ActionPerformed) {
            withContext(Dispatchers.IO) { g.filters.unblockDomain(site) }
            refresh++
        }
    }
    fun ask(site: String) { if (Domains.isEssential(site)) confirm = site else block(site) }

    val c = Cw.colors
    Scaffold(
        containerColor = c.background,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NeuIconButton(onClose, stringResource(R.string.back), Icons.Filled.Close)
                Spacer(Modifier.width(16.dp))
                ScreenTitle(stringResource(R.string.qb_title), Modifier.weight(1f))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (shared.isNotEmpty()) {
                item {
                    NeuCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), radius = 32.dp) {
                        Text(stringResource(R.string.qb_shared_title).uppercase(), style = MaterialTheme.typography.labelLarge, color = c.textMuted)
                        shared.forEach { host ->
                            val site = Domains.site(host)
                            val already = g.filters.isBlocked(host, BlockerState.engine)
                            Spacer(Modifier.height(10.dp))
                            Text(host, color = c.text, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (already) {
                                Spacer(Modifier.height(6.dp))
                                Text(stringResource(R.string.qb_already), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                            } else {
                                GradientButton(stringResource(R.string.qb_block_site, site), { ask(site) }, Modifier.fillMaxWidth().padding(top = 14.dp))
                                if (host != site) {
                                    NeuButton(
                                        stringResource(R.string.qb_block_host),
                                        { scope.launch { withContext(Dispatchers.IO) { g.filters.blockDomain(host) }; refresh++; snackbar.showSnackbar(ctx.getString(R.string.qb_blocked, host)) } },
                                        Modifier.fillMaxWidth().padding(top = 12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.qb_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textMuted,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
            if (rows.isEmpty()) {
                item {
                    NeuCard(Modifier.fillMaxWidth().padding(20.dp)) { Text(stringResource(R.string.qb_empty), color = c.textMuted) }
                }
            }
            itemsIndexed(rows, key = { _, r -> r.site }) { i, row ->
                val first = i == 0
                val last = i == rows.lastIndex
                val shape = RoundedCornerShape(
                    topStart = if (first) 28.dp else 0.dp, topEnd = if (first) 28.dp else 0.dp,
                    bottomStart = if (last) 28.dp else 0.dp, bottomEnd = if (last) 28.dp else 0.dp,
                )
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(shape).background(c.surface)) {
                    if (!first) RowDivider()
                    NeuRow(
                        row.site,
                        listOfNotNull(fmt.format(Date(row.lastSeen)), row.app, row.hosts.take(3).joinToString(", ")).joinToString(" · "),
                    ) {
                        if (row.blocked) {
                            Text(stringResource(R.string.log_status_blocked), color = c.blocked, style = MaterialTheme.typography.labelMedium)
                        } else {
                            NeuChip(true, stringResource(R.string.qb_block)) { ask(row.site) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    confirm?.let { site ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            shape = RoundedCornerShape(32.dp),
            title = { Text(stringResource(R.string.qb_essential_title, site)) },
            text = { Text(stringResource(R.string.qb_essential_body)) },
            confirmButton = { TextButton(onClick = { block(site); confirm = null }) { Text(stringResource(R.string.qb_block)) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private const val WINDOW_MS = 5 * 60_000L
