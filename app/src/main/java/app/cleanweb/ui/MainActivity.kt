package app.cleanweb.ui

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cleanweb.R
import app.cleanweb.vpn.BlockerState
import app.cleanweb.vpn.BlockerVpnService

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            CleanWebTheme(settings.theme) {
                val startProtection = rememberProtectionStarter()
                if (!settings.onboardingDone) {
                    OnboardingScreen(vm, startProtection)
                } else {
                    MainScreen(vm, startProtection)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshChecks()
    }
}

/**
 * Returns a function that asks for VPN permission when needed and then starts protection.
 * The callback receives true once protection is starting.
 */
@Composable
fun rememberProtectionStarter(): (onResult: (Boolean) -> Unit) -> Unit {
    val context = LocalContext.current
    // Holder survives recomposition while the system permission dialog is open.
    val pending = remember { arrayOfNulls<(Boolean) -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val ok = result.resultCode == Activity.RESULT_OK
        if (ok) BlockerVpnService.start(context) else BlockerState.lastError.value = R.string.error_vpn_denied
        pending[0]?.invoke(ok)
        pending[0] = null
    }
    return { onResult ->
        val intent = VpnService.prepare(context)
        if (intent == null) {
            BlockerVpnService.start(context)
            onResult(true)
        } else {
            pending[0] = onResult
            try {
                launcher.launch(intent)
            } catch (_: Exception) {
                BlockerState.lastError.value = R.string.error_vpn_not_permitted
                onResult(false)
            }
        }
    }
}
