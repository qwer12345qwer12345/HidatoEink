package com.eink.hidato.ui.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eink.hidato.data.GameRepository
import com.eink.hidato.data.GameSettings
import com.eink.hidato.data.SettingsRepository
import com.eink.hidato.data.db.GameSaveEntity
import com.eink.hidato.domain.Difficulty
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.io.IOException

@HiltViewModel
class MenuViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    gameRepository: GameRepository,
) : ViewModel() {

    val tutorialCompleted: StateFlow<Boolean?> = settingsRepository.tutorialCompleted
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _tutorialError = MutableStateFlow<String?>(null)
    val tutorialError = _tutorialError.asStateFlow()

    fun completeTutorial() {
        viewModelScope.launch {
            try {
                settingsRepository.completeTutorial()
                _tutorialError.value = null
            } catch (_: IOException) {
                _tutorialError.value = "无法保存教程设置，请重试"
            }
        }
    }

    /** 用户上次自定义的行列尺寸与难度 */
    val settings: StateFlow<GameSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, GameSettings(8, 8, Difficulty.MEDIUM))

    /** 是否存在未完成的游戏（决定“继续游戏”按钮是否高亮可用） */
    val hasUnfinished: StateFlow<Boolean> = gameRepository.observeCurrent()
        .map { it != null && !it.completed }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 未完成游戏的摘要，用于“继续游戏”按钮副标题；无则为 null */
    val unfinishedSummary: StateFlow<String?> = gameRepository.observeCurrent()
        .map { entity: GameSaveEntity? ->
            entity?.takeIf { !it.completed }
                ?.let { "${it.rows} x ${it.cols} · 已连 ${it.currentNumber}/${it.rows * it.cols}" }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onNewGameConfirmed(rows: Int, cols: Int, difficulty: Difficulty) {
        viewModelScope.launch {
            settingsRepository.save(rows, cols, difficulty)
        }
    }
}
