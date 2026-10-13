package com.eink.hidato.di;

import com.eink.hidato.data.db.GameSaveDao;
import com.eink.hidato.data.db.HidatoDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
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
public final class AppModule_ProvideGameSaveDaoFactory implements Factory<GameSaveDao> {
  private final Provider<HidatoDatabase> dbProvider;

  public AppModule_ProvideGameSaveDaoFactory(Provider<HidatoDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public GameSaveDao get() {
    return provideGameSaveDao(dbProvider.get());
  }

  public static AppModule_ProvideGameSaveDaoFactory create(Provider<HidatoDatabase> dbProvider) {
    return new AppModule_ProvideGameSaveDaoFactory(dbProvider);
  }

  public static GameSaveDao provideGameSaveDao(HidatoDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideGameSaveDao(db));
  }
}
