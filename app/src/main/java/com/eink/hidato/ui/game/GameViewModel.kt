package com.eink.hidato.ui.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eink.hidato.data.GameRepository
import com.eink.hidato.data.toEntity
import com.eink.hidato.data.toGameState
import com.eink.hidato.domain.Difficulty
import com.eink.hidato.domain.GameEngine
import com.eink.hidato.domain.GameState
import com.eink.hidato.domain.PuzzleGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 一次性 UI 事件 */
sealed interface GameEvent {
    data class ShowToast(val message: String) : GameEvent
}

/** 游戏界面 UI 状态 */
sealed interface GameUiState {
    /** 正在生成谜题 / 读取存档 */
    data object Loading : GameUiState

    data class Playing(val state: GameState, val saving: Boolean = false) : GameUiState

    /** 存档缺失或损坏 */
    data class Failed(val message: String) : GameUiState
}

@HiltViewModel
class GameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gameRepository: GameRepository,
    private val puzzleGenerator: PuzzleGenerator,
) : ViewModel() {

    private val mode: String = savedStateHandle["mode"] ?: MODE_NEW
    private val rowsArg: Int = savedStateHandle["rows"] ?: 8
    private val colsArg: Int = savedStateHandle["cols"] ?: 8
    private var difficulty: Difficulty = Difficulty.from(savedStateHandle["difficulty"])

    private val _events = Channel<GameEvent>(Channel.BUFFERED)
    val events: Flow<GameEvent> = _events.receiveAsFlow()

    private val _uiState = MutableStateFlow<GameUiState>(GameUiState.Loading)
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                if (mode == MODE_CONTINUE) {
                    val entity = gameRepository.getCurrent()
                    val restored = entity?.toGameState()
                    if (restored == null) {
                        _uiState.value = GameUiState.Failed("未找到有效的游戏存档")
                    } else {
                        difficulty = Difficulty.from(entity.difficulty)
                        _uiState.value = GameUiState.Playing(restored)
                    }
                } else {
                    val puzzle = withContext(Dispatchers.Default) {
                        puzzleGenerator.generate(rowsArg, colsArg, difficulty)
                    }
                    val fresh = GameState(
                        puzzle = puzzle,
                        revealedHints = emptySet(),
                        playerPath = emptyList(),
                        errorCount = 0,
                        hintCount = 0,
                        completed = false,
                    )
                    gameRepository.save(fresh.toEntity(difficulty)).await()
                    // 新关卡已落库；进程恢复时读取它，而不是重新生成。
                    savedStateHandle["mode"] = MODE_CONTINUE
                    _uiState.value = GameUiState.Playing(fresh)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = GameUiState.Failed("无法读取或保存游戏，请返回后重试")
            }
        }
    }

    /** 点击格子：连接成功或错误计数 +1 */
    fun onCellTap(cell: Int) = update { GameEngine.tap(it, cell) }

    /** 撤销上一步 */
    fun onUndo() = update { GameEngine.undo(it) }

    /** 提示：随机显示一个尚未连接的隐藏数字 */
    fun onHint() = update { GameEngine.hint(it) }

    /**
     * 重置本关：仅重置内存状态，按需求“重置本关不保存”，
     * 之后玩家的下一步操作会以重置后的状态触发保存。
     */
    fun onReset() {
        val current = _uiState.value as? GameUiState.Playing ?: return
        if (current.state.completed) return
        val reset = GameEngine.reset(current.state)
        _uiState.value = GameUiState.Playing(reset)
    }

    private fun update(transform: (GameState) -> GameState) {
        val current = _uiState.value as? GameUiState.Playing ?: return
        if (current.state.completed) return
        val next = transform(current.state)
        if (next === current.state) return

        if (next.errorCount > current.state.errorCount) {
            _events.trySend(GameEvent.ShowToast("走步错误，请重新选择"))
        }

        _uiState.value = GameUiState.Playing(next, saving = next.completed)

        val saved = gameRepository.save(next.toEntity(difficulty))
        viewModelScope.launch {
            try {
                saved.await()
                if (next.completed) _uiState.value = GameUiState.Playing(next)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = GameUiState.Failed("游戏进度保存失败，请返回后重试")
            }
        }
    }

    companion object {
        const val MODE_NEW = "new"
        const val MODE_CONTINUE = "continue"
    }
}
