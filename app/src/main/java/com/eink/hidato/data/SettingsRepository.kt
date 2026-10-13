package com.eink.hidato.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eink.hidato.domain.Difficulty
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 用户自定义设置：网格行列尺寸与难度 */
data class GameSettings(
    val rows: Int,
    val cols: Int,
    val difficulty: Difficulty,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hidato_settings")

/** Jetpack DataStore Preferences：持久化用户自定义的行列尺寸与难度 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ROWS = intPreferencesKey("rows")
        val COLS = intPreferencesKey("cols")
        val DIFFICULTY = stringPreferencesKey("difficulty")
        val TUTORIAL_COMPLETED = booleanPreferencesKey("tutorial_completed")
    }

    val tutorialCompleted: Flow<Boolean> = context.dataStore.data.map { p ->
        p[Keys.TUTORIAL_COMPLETED] ?: false
    }

    suspend fun completeTutorial() {
        context.dataStore.edit { it[Keys.TUTORIAL_COMPLETED] = true }
    }

    val settings: Flow<GameSettings> = context.dataStore.data.map { p ->
        GameSettings(
            rows = (p[Keys.ROWS] ?: DEFAULT_ROWS).coerceIn(4, 15),
            cols = (p[Keys.COLS] ?: DEFAULT_COLS).coerceIn(4, 15),
            difficulty = Difficulty.from(p[Keys.DIFFICULTY]),
        )
    }

    suspend fun save(rows: Int, cols: Int, difficulty: Difficulty) {
        context.dataStore.edit { p ->
            p[Keys.ROWS] = rows
            p[Keys.COLS] = cols
            p[Keys.DIFFICULTY] = difficulty.name
        }
    }

    companion object {
        const val DEFAULT_ROWS = 8
        const val DEFAULT_COLS = 8
    }
}
