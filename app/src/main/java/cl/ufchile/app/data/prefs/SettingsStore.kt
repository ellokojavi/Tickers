package cl.ufchile.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cl.ufchile.app.domain.model.DataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class SyncInfo(val source: DataSource?, val at: Long)

class SettingsStore(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val LAST_SYNC_SOURCE = stringPreferencesKey("last_sync_source")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
    }

    val theme: Flow<ThemeMode> = context.dataStore.data.map { p: Preferences ->
        runCatching { ThemeMode.valueOf(p[Keys.THEME] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
    }

    val syncInfo: Flow<SyncInfo> = context.dataStore.data.map { p ->
        SyncInfo(
            source = p[Keys.LAST_SYNC_SOURCE]?.let { s ->
                runCatching { DataSource.valueOf(s) }.getOrNull()
            },
            at = p[Keys.LAST_SYNC_AT] ?: 0L,
        )
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun recordSync(source: DataSource, at: Long) {
        context.dataStore.edit {
            it[Keys.LAST_SYNC_SOURCE] = source.name
            it[Keys.LAST_SYNC_AT] = at
        }
    }
}
