package com.lumina.filemanager.core.designsystem

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "lumina_preferences")

class ThemeRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_id")

    val selectedTheme: Flow<AppTheme> = context.themeDataStore.data.map { preferences ->
        AppThemeRegistry.byId(preferences[themeKey] ?: AppThemeRegistry.default.id)
    }

    suspend fun setTheme(themeId: String) {
        context.themeDataStore.edit { preferences -> preferences[themeKey] = themeId }
    }
}