package com.ajsharm.imagen.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.ajsharm.imagen.ui.ThemeChoice

data class ImagenColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val onBackground: Color,
    val onSurface: Color,
    val muted: Color,
    val border: Color,
    val accent: Color,
    val accentOn: Color,
    val userBubble: Color,
    val userBubbleOn: Color,
    val assistantBubble: Color,
    val error: Color,
    val errorBg: Color,
    val success: Color,
    val isDark: Boolean,
)

private val Midnight = ImagenColors(
    background = Color(0xFF0D1117),
    surface = Color(0xFF161B22),
    surfaceElevated = Color(0xFF21262D),
    onBackground = Color(0xFFE6EDF3),
    onSurface = Color(0xFFE6EDF3),
    muted = Color(0xFF8B949E),
    border = Color(0xFF30363D),
    accent = Color(0xFF58A6FF),
    accentOn = Color(0xFF0D1117),
    userBubble = Color(0xFF1F6FEB),
    userBubbleOn = Color(0xFFFFFFFF),
    assistantBubble = Color(0xFF21262D),
    error = Color(0xFFF85149),
    errorBg = Color(0x33F85149),
    success = Color(0xFF3FB950),
    isDark = true,
)

private val Paper = ImagenColors(
    background = Color(0xFFFAF7F2),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF1ECE3),
    onBackground = Color(0xFF1F2328),
    onSurface = Color(0xFF1F2328),
    muted = Color(0xFF656D76),
    border = Color(0xFFD8D2C7),
    accent = Color(0xFF0969DA),
    accentOn = Color(0xFFFFFFFF),
    userBubble = Color(0xFF0969DA),
    userBubbleOn = Color(0xFFFFFFFF),
    assistantBubble = Color(0xFFF1ECE3),
    error = Color(0xFFCF222E),
    errorBg = Color(0x22CF222E),
    success = Color(0xFF1A7F37),
    isDark = false,
)

val LocalImagenColors = staticCompositionLocalOf { Midnight }

@Composable
fun ImagenTheme(
    choice: ThemeChoice,
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val colors = when (choice) {
        ThemeChoice.MIDNIGHT -> Midnight
        ThemeChoice.PAPER -> Paper
        ThemeChoice.DYNAMIC -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val dark = isSystemInDarkTheme()
            val dyn = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            ImagenColors(
                background = dyn.background,
                surface = dyn.surface,
                surfaceElevated = dyn.surfaceVariant,
                onBackground = dyn.onBackground,
                onSurface = dyn.onSurface,
                muted = dyn.onSurfaceVariant,
                border = dyn.outlineVariant,
                accent = dyn.primary,
                accentOn = dyn.onPrimary,
                userBubble = dyn.primary,
                userBubbleOn = dyn.onPrimary,
                assistantBubble = dyn.surfaceVariant,
                error = dyn.error,
                errorBg = dyn.errorContainer,
                success = Color(0xFF3FB950),
                isDark = dark,
            )
        } else Midnight
    }

    val materialScheme = if (colors.isDark) darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.accentOn,
        background = colors.background,
        onBackground = colors.onBackground,
        surface = colors.surface,
        onSurface = colors.onSurface,
        surfaceVariant = colors.surfaceElevated,
        error = colors.error,
        outline = colors.border,
    ) else lightColorScheme(
        primary = colors.accent,
        onPrimary = colors.accentOn,
        background = colors.background,
        onBackground = colors.onBackground,
        surface = colors.surface,
        onSurface = colors.onSurface,
        surfaceVariant = colors.surfaceElevated,
        error = colors.error,
        outline = colors.border,
    )

    CompositionLocalProvider(LocalImagenColors provides colors) {
        MaterialTheme(colorScheme = materialScheme, content = content)
    }
}
