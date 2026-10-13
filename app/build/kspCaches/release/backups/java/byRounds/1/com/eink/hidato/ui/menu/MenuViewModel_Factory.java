package com.eink.hidato.ui.menu;

import com.eink.hidato.data.GameRepository;
import com.eink.hidato.data.SettingsRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class MenuViewModel_Factory implements Factory<MenuViewModel> {
  private final Provider<SettingsRepository> settingsRepositoryProvider;

  private final Provider<GameRepository> gameRepositoryProvider;

  public MenuViewModel_Factory(Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<GameRepository> gameRepositoryProvider) {
    this.settingsRepositoryProvider = settingsRepositoryProvider;
    this.gameRepositoryProvider = gameRepositoryProvider;
  }

  @Override
  public MenuViewModel get() {
    return newInstance(settingsRepositoryProvider.get(), gameRepositoryProvider.get());
  }

  public static MenuViewModel_Factory create(
      Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<GameRepository> gameRepositoryProvider) {
    return new MenuViewModel_Factory(settingsRepositoryProvider, gameRepositoryProvider);
  }

  public static MenuViewModel newInstance(SettingsRepository settingsRepository,
      GameRepository gameRepository) {
    return new MenuViewModel(settingsRepository, gameRepository);
  }
}
