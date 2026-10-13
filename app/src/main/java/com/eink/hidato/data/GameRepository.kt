package com.eink.hidato.data

import com.eink.hidato.data.db.GameSaveDao
import com.eink.hidato.data.db.GameSaveEntity
import com.eink.hidato.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** 单槽位存档：所有读写按顺序执行，写入不随界面销毁而取消。 */
@Singleton
class GameRepository @Inject constructor(
    private val dao: GameSaveDao,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val writes = Mutex()

    fun observeCurrent(): Flow<GameSaveEntity?> = dao.observeCurrent()

    suspend fun getCurrent(): GameSaveEntity? = writes.withLock { dao.getCurrent() }

    fun save(entity: GameSaveEntity): Deferred<Unit> =
        // 立即加入 Mutex 的等待队列，避免后台调度改变调用顺序。
        appScope.async(start = CoroutineStart.UNDISPATCHED) {
            writes.withLock { dao.upsert(entity) }
        }
}
