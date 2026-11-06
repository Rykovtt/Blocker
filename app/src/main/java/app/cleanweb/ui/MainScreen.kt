package app.cleanweb.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.cleanweb.R

private enum class Tab(@StringRes val title: Int, @DrawableRes val iconRes: Int?, val vector: ImageVector?) {
    HOME(R.string.tab_home, R.drawable.ic_shield, null),
    LOG(R.string.tab_log, null, Icons.AutoMirrored.Filled.List),
    FILTERS(R.string.tab_filters, R.drawable.ic_filter, null),
    APPS(R.string.tab_apps, R.drawable.ic_apps, null),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel, startProtection: ((Boolean) -> Unit) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.ensureFiltersDownloaded()
        vm.messages.collect { snackbar.showSnackbar(context.getString(it)) }
    }
    BackHandler(enabled = showSettings) { showSettings = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (showSettings) R.string.settings_title else tab.title)) },
                navigationIcon = {
                    if (showSettings) {
                        IconButton(onClick = { showSettings = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (!showSettings) {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Settings, stringResource(R.string.settings_title))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!showSettings) {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = {
                                if (t.vector != null) Icon(t.vector, null) else Icon(painterResource(t.iconRes!!), null)
                            },
                            label = { Text(stringResource(t.title)) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val m = Modifier.padding(padding)
        if (showSettings) {
            SettingsScreen(vm, m)
        } else {
            when (tab) {
                Tab.HOME -> HomeScreen(vm, startProtection, m)
                Tab.LOG -> LogScreen(vm, m)
                Tab.FILTERS -> FiltersScreen(vm, m)
                Tab.APPS -> AppsScreen(vm, m)
            }
        }
    }
}
