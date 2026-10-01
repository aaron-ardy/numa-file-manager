package com.numa.filemanager.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppTheme(
    val id: String,
    val name: String,
    val isDark: Boolean,
    val primaryColor: Color,
    val secondaryColor: Color,
    val surfaceColor: Color,
    val backgroundColor: Color,
    val containerColor: Color
)

object AppThemeRegistry {
    val presets = listOf(
        AppTheme(
            id = "numa",
            name = "Amber",
            isDark = false,
            primaryColor = Color(0xFFB85C00),
            secondaryColor = Color(0xFF6F7662),
            surfaceColor = Color(0xFFFFFBF4),
            backgroundColor = Color(0xFFFFF4DF),
            containerColor = Color(0xFFFFDDB0)
        ),
        AppTheme(
            id = "sage",
            name = "Sage Mist",
            isDark = false,
            primaryColor = Color(0xFF537A67),
            secondaryColor = Color(0xFF71858A),
            surfaceColor = Color(0xFFF8FBF8),
            backgroundColor = Color(0xFFEEF5F0),
            containerColor = Color(0xFFD7E9DD)
        ),
        AppTheme(
            id = "violet",
            name = "Violet Dusk",
            isDark = true,
            primaryColor = Color(0xFFB18AC7),
            secondaryColor = Color(0xFF9DB4C2),
            surfaceColor = Color(0xFF24232C),
            backgroundColor = Color(0xFF17171F),
            containerColor = Color(0xFF3A3045)
        ),
        AppTheme(
            id = "dark",
            name = "Midnight",
            isDark = true,
            primaryColor = Color(0xFFF0B35A),
            secondaryColor = Color(0xFFAAB6B1),
            surfaceColor = Color(0xFF202322),
            backgroundColor = Color(0xFF121413),
            containerColor = Color(0xFF3A3023)
        )
    )

    val default: AppTheme = presets.first()
    fun byId(id: String): AppTheme = when (id) {
        "ocean" -> presets.first { it.id == "sage" }
        "starry" -> presets.first { it.id == "violet" }
        else -> presets.firstOrNull { it.id == id } ?: default
    }
}

val LocalAppTheme = staticCompositionLocalOf { AppThemeRegistry.default }

@Composable
fun NumaTheme(
    appTheme: AppTheme = AppThemeRegistry.default,
    content: @Composable () -> Unit
) {
    val colors = if (appTheme.isDark || isSystemInDarkTheme() && appTheme.id == "system") {
        darkColorScheme(
            primary = appTheme.primaryColor,
            onPrimary = if (appTheme.id == "dark") Color(0xFF2A1B08) else Color.White,
            secondary = appTheme.secondaryColor,
            onSecondary = Color(0xFF171A18),
            surface = appTheme.surfaceColor,
            background = appTheme.backgroundColor,
            primaryContainer = appTheme.containerColor,
            onPrimaryContainer = Color(0xFFFFF8EF),
            secondaryContainer = appTheme.containerColor,
            onSecondaryContainer = appTheme.onSurfaceColor(),
            surfaceVariant = appTheme.surfaceColor,
            onSurfaceVariant = appTheme.secondaryColor,
            outline = appTheme.secondaryColor
        )
    } else {
        lightColorScheme(
            primary = appTheme.primaryColor,
            onPrimary = Color.White,
            secondary = appTheme.secondaryColor,
            onSecondary = Color.White,
            surface = appTheme.surfaceColor,
            background = appTheme.backgroundColor,
            primaryContainer = appTheme.containerColor,
            onPrimaryContainer = Color(0xFF2B1A08),
            secondaryContainer = appTheme.containerColor,
            onSecondaryContainer = Color(0xFF1A211D),
            surfaceVariant = appTheme.surfaceColor,
            onSurfaceVariant = appTheme.secondaryColor,
            outline = appTheme.secondaryColor
        )
    }

    CompositionLocalProvider(LocalAppTheme provides appTheme) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

private fun AppTheme.onSurfaceColor(): Color = Color(0xFFF3F5F1)