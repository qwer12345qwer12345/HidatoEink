package com.eink.hidato.data

import com.eink.hidato.domain.Difficulty
import com.eink.hidato.domain.GameState
import com.eink.hidato.domain.HamiltonianPaths
import com.eink.hidato.domain.Puzzle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameRepositoryTest {
    private val state = GameState(
        Puzzle(4, 4, HamiltonianPaths.randomTour(4, 4, kotlin.random.Random(1)), BooleanArray(17) { true }),
        emptySet(), emptyList(), 0, 0, false,
    )

    @Test
    fun `saves stay ordered and reads wait even when UI waiter is cancelled`() = runTest {
        val dao = FakeGameSaveDao()
        val repository = GameRepository(dao, applicationScope())
        val gate = CompletableDeferred<Unit>()
        dao.blockNextWrite = gate
        val first = state.toEntity(Difficulty.HARD)
        val second = state.copy(playerPath = state.puzzle.path.take(15)).toEntity(Difficulty.HARD)
        val last = state.copy(playerPath = state.puzzle.path.toList(), completed = true).toEntity(Difficulty.HARD)
        val saving = repository.save(first)
        val uiWaiter = launch { saving.await() }
        repository.save(second)
        val completed = repository.save(last)
        val reading = async { repository.getCurrent() }
        runCurrent()
        assertTrue(dao.writes.isEmpty())
        assertFalse(reading.isCompleted)
        uiWaiter.cancel()
        gate.complete(Unit)
        completed.await()
        assertEquals(listOf(first, second, last), dao.writes)
        assertEquals(last, reading.await())
    }

    @Test
    fun `failed write is reported and does not block following writes`() = runTest {
        val dao = FakeGameSaveDao()
        val repository = GameRepository(dao, applicationScope())
        dao.failNextWrite = true
        val entity = state.toEntity(Difficulty.EASY)
        assertTrue(runCatching { repository.save(entity).await() }.isFailure)
        repository.save(entity).await()
        assertEquals(entity, repository.getCurrent())
    }
}
