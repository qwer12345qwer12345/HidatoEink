package com.eink.hidato.di

import android.content.Context
import androidx.room.Room
import com.eink.hidato.data.db.GameSaveDao
import com.eink.hidato.data.db.HidatoDatabase
import com.eink.hidato.domain.PuzzleGenerator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HidatoDatabase =
        Room.databaseBuilder(context, HidatoDatabase::class.java, HidatoDatabase.NAME).build()

    @Provides
    @Singleton
    fun provideGameSaveDao(db: HidatoDatabase): GameSaveDao = db.gameSaveDao()

    /** 应用级协程作用域：承载防抖保存等与界面生命周期解耦的工作 */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun providePuzzleGenerator(): PuzzleGenerator = PuzzleGenerator()
}
