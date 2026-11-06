package app.cleanweb.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.cleanweb.data.ThemeMode

private val Light = lightColorScheme(
    primary = Color(0xFF0F7B5F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F2D6),
    onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF4C6358),
    tertiary = Color(0xFF3F6375),
    error = Color(0xFFBA1A1A),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8AD6BA),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF00513E),
    onPrimaryContainer = Color(0xFFA6F2D6),
    secondary = Color(0xFFB3CCBF),
    tertiary = Color(0xFFA7CCE0),
    error = Color(0xFFFFB4AB),
)

@Composable
fun CleanWebTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
