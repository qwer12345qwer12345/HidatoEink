package com.eink.hidato.data

import com.eink.hidato.data.db.GameSaveDao
import com.eink.hidato.data.db.GameSaveEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import java.io.IOException

class FakeGameSaveDao(initial: GameSaveEntity? = null) : GameSaveDao {
    val current = MutableStateFlow(initial)
    val writes = mutableListOf<GameSaveEntity>()
    var blockNextWrite: CompletableDeferred<Unit>? = null
    var failNextWrite = false

    override fun observeCurrent() = current
    override suspend fun getCurrent() = current.value

    override suspend fun upsert(entity: GameSaveEntity) {
        val gate = blockNextWrite
        blockNextWrite = null
        gate?.await()
        if (failNextWrite) {
            failNextWrite = false
            throw IOException("disk full")
        }
        writes += entity
        current.value = entity
    }
}

fun TestScope.applicationScope() = CoroutineScope(
    backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job]),
)
