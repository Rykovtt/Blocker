package app.cleanweb.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cleanweb.R

private enum class Tab(@StringRes val title: Int, @DrawableRes val iconRes: Int?, val vector: ImageVector?) {
    HOME(R.string.tab_home, R.drawable.ic_shield, null),
    LOG(R.string.tab_log, null, Icons.AutoMirrored.Filled.List),
    FILTERS(R.string.tab_filters, R.drawable.ic_filter, null),
    APPS(R.string.tab_apps, R.drawable.ic_apps, null),
}

enum class Overlay(@StringRes val title: Int) { SETTINGS(R.string.settings_title), STATS(R.string.stats_title) }

@Composable
fun MainScreen(vm: MainViewModel, startProtection: ((Boolean) -> Unit) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val c = Cw.colors

    LaunchedEffect(Unit) {
        vm.ensureFiltersDownloaded()
        vm.checkUpdates(manual = false)
        vm.messages.collect { snackbar.showSnackbar(context.getString(it)) }
    }
    BackHandler(enabled = overlay != null) { overlay = null }

    Scaffold(
        containerColor = c.background,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 24.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (overlay != null) {
                    NeuIconButton({ overlay = null }, stringResource(R.string.back), Icons.AutoMirrored.Filled.ArrowBack)
                    Spacer(Modifier.width(16.dp))
                }
                ScreenTitle(stringResource(overlay?.title ?: tab.title), Modifier.weight(1f))
                if (overlay == null) {
                    NeuIconButton({ overlay = Overlay.SETTINGS }, stringResource(R.string.settings_title), Icons.Filled.Settings)
                }
            }
        },
        bottomBar = {
            if (overlay == null) {
                Row(
                    Modifier.fillMaxWidth().background(c.background).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Tab.entries.forEach { t ->
                        NavTile(
                            label = stringResource(t.title),
                            icon = t.vector,
                            iconRes = t.iconRes,
                            selected = tab == t,
                            onClick = { tab = t },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val m = Modifier.padding(padding)
        when (overlay) {
            Overlay.SETTINGS -> SettingsScreen(vm, m)
            Overlay.STATS -> StatsScreen(vm, m)
            null -> when (tab) {
                Tab.HOME -> HomeScreen(vm, startProtection, m) { overlay = Overlay.STATS }
                Tab.LOG -> LogScreen(vm, m)
                Tab.FILTERS -> FiltersScreen(vm, m)
                Tab.APPS -> AppsScreen(vm, m)
            }
        }
    }
}

/** Rounded-square nav tile: raised with a gradient icon when active, outlined otherwise (as in the kit). */
@Composable
private fun NavTile(label: String, icon: ImageVector?, iconRes: Int?, selected: Boolean, onClick: () -> Unit) {
    val c = Cw.colors
    val shape = RoundedCornerShape(22.dp)
    val painter = if (icon != null) rememberVectorPainter(icon) else painterResource(iconRes!!)
    Column(
        Modifier
            .size(width = 76.dp, height = 72.dp)
            .then(if (selected) Modifier.neu(c, 22.dp, elevation = 7.dp) else Modifier.border(1.dp, c.outline.copy(alpha = 0.7f), shape))
            .clip(shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    if (selected) drawRect(Brush.linearGradient(listOf(c.accentStart, c.accentEnd)), blendMode = BlendMode.SrcIn)
                },
        ) {
            Icon(painter, null, tint = c.textMuted, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 11.sp,
            color = if (selected) c.text else c.textMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
