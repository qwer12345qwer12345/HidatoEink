package com.eink.hidato.data;

import com.eink.hidato.data.db.GameSaveDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import kotlinx.coroutines.CoroutineScope;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("com.eink.hidato.di.ApplicationScope")
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class GameRepository_Factory implements Factory<GameRepository> {
  private final Provider<GameSaveDao> daoProvider;

  private final Provider<CoroutineScope> appScopeProvider;

  public GameRepository_Factory(Provider<GameSaveDao> daoProvider,
      Provider<CoroutineScope> appScopeProvider) {
    this.daoProvider = daoProvider;
    this.appScopeProvider = appScopeProvider;
  }

  @Override
  public GameRepository get() {
    return newInstance(daoProvider.get(), appScopeProvider.get());
  }

  public static GameRepository_Factory create(Provider<GameSaveDao> daoProvider,
      Provider<CoroutineScope> appScopeProvider) {
    return new GameRepository_Factory(daoProvider, appScopeProvider);
  }

  public static GameRepository newInstance(GameSaveDao dao, CoroutineScope appScope) {
    return new GameRepository(dao, appScope);
  }
}
