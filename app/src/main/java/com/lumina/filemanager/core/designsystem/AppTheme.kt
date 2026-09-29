package com.lumina.filemanager.core.designsystem

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
            id = "lumina",
            name = "Lumina",
            isDark = false,
            primaryColor = Color(0xFFE06D53),
            secondaryColor = Color(0xFF526B5D),
            surfaceColor = Color(0xFFFFFBF7),
            backgroundColor = Color(0xFFF7F3EE),
            containerColor = Color(0xFFF5D9CD)
        ),
        AppTheme(
            id = "ocean",
            name = "Ocean",
            isDark = false,
            primaryColor = Color(0xFF1F7A8C),
            secondaryColor = Color(0xFF506776),
            surfaceColor = Color(0xFFF4FAFC),
            backgroundColor = Color(0xFFEDF4F5),
            containerColor = Color(0xFFD2E9EC)
        ),
        AppTheme(
            id = "starry",
            name = "Starry",
            isDark = true,
            primaryColor = Color(0xFF7B2CBF),
            secondaryColor = Color(0xFF9CB8C7),
            surfaceColor = Color(0xFF171A22),
            backgroundColor = Color(0xFF0F111A),
            containerColor = Color(0xFF292638)
        )
    )

    val default: AppTheme = presets.first()
    fun byId(id: String): AppTheme = presets.firstOrNull { it.id == id } ?: default
}

val LocalAppTheme = staticCompositionLocalOf { AppThemeRegistry.default }

@Composable
fun LuminaTheme(
    appTheme: AppTheme = AppThemeRegistry.default,
    content: @Composable () -> Unit
) {
    val colors = if (appTheme.isDark || isSystemInDarkTheme() && appTheme.id == "system") {
        darkColorScheme(
            primary = appTheme.primaryColor,
            secondary = appTheme.secondaryColor,
            surface = appTheme.surfaceColor,
            background = appTheme.backgroundColor,
            primaryContainer = appTheme.containerColor
        )
    } else {
        lightColorScheme(
            primary = appTheme.primaryColor,
            secondary = appTheme.secondaryColor,
            surface = appTheme.surfaceColor,
            background = appTheme.backgroundColor,
            primaryContainer = appTheme.containerColor
        )
    }

    CompositionLocalProvider(LocalAppTheme provides appTheme) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}