package com.eink.hidato.ui.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.eink.hidato.data.FakeGameSaveDao
import com.eink.hidato.data.GameRepository
import com.eink.hidato.data.applicationScope
import com.eink.hidato.data.toEntity
import com.eink.hidato.domain.Difficulty
import com.eink.hidato.domain.GameState
import com.eink.hidato.domain.Grids
import com.eink.hidato.domain.HamiltonianPaths
import com.eink.hidato.domain.Puzzle
import com.eink.hidato.domain.PuzzleGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val clues = BooleanArray(17).apply { this[1] = true; this[16] = true }
    private val path = HamiltonianPaths.randomTour(4, 4, kotlin.random.Random(1))
    private val state = GameState(
        Puzzle(4, 4, path, clues),
        emptySet(), emptyList(), 0, 0, false,
    )

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `new game records continue mode and recreated viewmodel restores same puzzle`() = runTest(dispatcher) {
        val dao = FakeGameSaveDao()
        val repository = GameRepository(dao, applicationScope())
        val handle = SavedStateHandle(mapOf("mode" to "new", "rows" to 4, "cols" to 4, "difficulty" to "HARD"))
        val first = GameViewModel(handle, repository, PuzzleGenerator())
        store.put("first", first)
        val playing = first.uiState.first { it is GameUiState.Playing } as GameUiState.Playing
        assertEquals("continue", handle.get<String>("mode"))
        first.onCellTap(playing.state.puzzle.path[0])
        val before = repository.getCurrent()
        val restoredHandle = SavedStateHandle(mapOf("mode" to handle.get<String>("mode"), "difficulty" to "NONE"))
        val restored = GameViewModel(restoredHandle, repository, PuzzleGenerator())
        store.put("restored", restored)
        val recovered = restored.uiState.first { it is GameUiState.Playing } as GameUiState.Playing
        assertArrayEquals(playing.state.puzzle.path, recovered.state.puzzle.path)
        assertEquals(playing.state.placedCount, recovered.state.placedCount)
        assertEquals(before, repository.getCurrent())
        restored.onCellTap(recovered.state.puzzle.path[1])
        assertEquals("HARD", repository.getCurrent()?.difficulty)
    }

    @Test
    fun `corrupt save reaches failure UI`() = runTest(dispatcher) {
        val dao = FakeGameSaveDao(state.toEntity(Difficulty.EASY).copy(solutionPath = "x"))
        val model = GameViewModel(SavedStateHandle(mapOf("mode" to "continue")), GameRepository(dao, applicationScope()), PuzzleGenerator())
        store.put("bad", model)
        assertTrue(model.uiState.first { it !is GameUiState.Loading } is GameUiState.Failed)
    }

    @Test
    fun `settlement waits for completed save to commit`() = runTest(dispatcher) {
        val almost = state.copy(
            playerPath = state.puzzle.path.slice(1..13),
            focusedCell = state.puzzle.path[13],
        )
        val dao = FakeGameSaveDao(almost.toEntity(Difficulty.HARD))
        val model = GameViewModel(SavedStateHandle(mapOf("mode" to "continue")), GameRepository(dao, applicationScope()), PuzzleGenerator())
        store.put("settlement", model)
        model.uiState.first { it is GameUiState.Playing }
        val gate = CompletableDeferred<Unit>()
        dao.blockNextWrite = gate
        model.onCellTap(state.puzzle.path[14])
        assertTrue((model.uiState.value as GameUiState.Playing).saving)
        assertFalse(requireNotNull(dao.current.value).completed)
        model.onUndo()
        assertTrue((model.uiState.value as GameUiState.Playing).state.completed)
        gate.complete(Unit)
        val committed = model.uiState.first { it is GameUiState.Playing && !it.saving } as GameUiState.Playing
        assertTrue(committed.state.completed)
        assertTrue(requireNotNull(dao.current.value).completed)
    }

    @Test
    fun `save error is shown and completed save can restore settlement`() = runTest(dispatcher) {
        val dao = FakeGameSaveDao(state.toEntity(Difficulty.EASY))
        val repository = GameRepository(dao, applicationScope())
        val model = GameViewModel(SavedStateHandle(mapOf("mode" to "continue")), repository, PuzzleGenerator())
        store.put("error", model)
        model.uiState.first { it is GameUiState.Playing }
        dao.failNextWrite = true
        model.onCellTap(state.puzzle.path[0])
        assertTrue(model.uiState.first { it is GameUiState.Failed } is GameUiState.Failed)
        repository.save(
            state.copy(
                playerPath = state.puzzle.path.slice(1..14),
                completed = true,
            ).toEntity(Difficulty.EASY),
        ).await()
        val restored = GameViewModel(SavedStateHandle(mapOf("mode" to "continue")), repository, PuzzleGenerator())
        store.put("complete", restored)
        val playing = restored.uiState.first { it is GameUiState.Playing } as GameUiState.Playing
        assertTrue(playing.state.completed)
    }

    @Test
    fun `wrong step emits toast event`() = runTest(dispatcher) {
        val dao = FakeGameSaveDao()
        val repository = GameRepository(dao, applicationScope())
        val handle = SavedStateHandle(mapOf("mode" to "new", "rows" to 4, "cols" to 4, "difficulty" to "EASY"))
        val model = GameViewModel(handle, repository, PuzzleGenerator())
        store.put("toast", model)
        val playing = model.uiState.first { it is GameUiState.Playing } as GameUiState.Playing
        // 先点击线索 1 获得焦点
        model.onCellTap(playing.state.puzzle.path[0])
        // 找一个不相邻的隐藏格子触发错误走步
        val wrongCell = (0 until playing.state.total).first {
            !playing.state.isVisibleNumber(playing.state.puzzle.numberAt(it)) &&
                !Grids.adjacent(playing.state.puzzle.path[0], it, playing.state.puzzle.cols)
        }
        val events = mutableListOf<GameEvent>()
        val job = launch { model.events.collect { events.add(it) } }
        model.onCellTap(wrongCell)
        testScheduler.advanceUntilIdle()
        assertEquals(1, events.size)
        assertTrue(events[0] is GameEvent.ShowToast)
        assertEquals("走步错误，请重新选择", (events[0] as GameEvent.ShowToast).message)
        job.cancel()
    }
}
