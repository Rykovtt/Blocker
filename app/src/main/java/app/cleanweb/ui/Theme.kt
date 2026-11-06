package app.cleanweb.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import app.cleanweb.data.ThemeMode

private fun typography(): Typography {
    val base = Typography()
    fun t(s: androidx.compose.ui.text.TextStyle, w: FontWeight? = null) = s.copy(fontFamily = Montserrat, fontWeight = w ?: s.fontWeight)
    return Typography(
        displayLarge = t(base.displayLarge, FontWeight.Light),
        displayMedium = t(base.displayMedium, FontWeight.Light),
        displaySmall = t(base.displaySmall, FontWeight.Normal),
        headlineLarge = t(base.headlineLarge, FontWeight.Normal),
        headlineMedium = t(base.headlineMedium, FontWeight.Normal),
        headlineSmall = t(base.headlineSmall, FontWeight.Medium),
        titleLarge = t(base.titleLarge, FontWeight.SemiBold),
        titleMedium = t(base.titleMedium, FontWeight.Medium),
        titleSmall = t(base.titleSmall, FontWeight.Medium),
        bodyLarge = t(base.bodyLarge),
        bodyMedium = t(base.bodyMedium),
        bodySmall = t(base.bodySmall),
        labelLarge = t(base.labelLarge, FontWeight.Medium),
        labelMedium = t(base.labelMedium, FontWeight.Medium),
        labelSmall = t(base.labelSmall, FontWeight.Medium),
    )
}

@Composable
fun CleanWebTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val c = if (dark) DarkCw else LightCw
    // Material components (dialogs, snackbars, progress, text fields) follow the same palette.
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accentEnd, onPrimary = Color.White, secondary = c.accentStart,
            background = c.background, onBackground = c.text, surface = c.surfaceTop, onSurface = c.text,
            surfaceVariant = c.track, onSurfaceVariant = c.textMuted, surfaceContainerHigh = c.surfaceTop,
            surfaceContainer = c.surfaceTop, surfaceContainerLow = c.surfaceBottom,
            outline = c.outline, error = c.blocked, inverseSurface = c.text, inverseOnSurface = c.background,
            inversePrimary = c.accentStart,
        )
    } else {
        lightColorScheme(
            primary = c.accentEnd, onPrimary = Color.White, secondary = c.accentStart,
            background = c.background, onBackground = c.text, surface = c.surfaceTop, onSurface = c.text,
            surfaceVariant = c.track, onSurfaceVariant = c.textMuted, surfaceContainerHigh = c.surfaceTop,
            surfaceContainer = c.surfaceTop, surfaceContainerLow = c.surfaceBottom,
            outline = c.outline, error = c.blocked, inverseSurface = c.text, inverseOnSurface = c.background,
            inversePrimary = c.accentStart,
        )
    }
    CompositionLocalProvider(LocalCw provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography(), content = content)
    }
}
