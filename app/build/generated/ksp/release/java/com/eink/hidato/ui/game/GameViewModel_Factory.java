package com.eink.hidato.ui.game;

import androidx.lifecycle.SavedStateHandle;
import com.eink.hidato.data.GameRepository;
import com.eink.hidato.domain.PuzzleGenerator;
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
public final class GameViewModel_Factory implements Factory<GameViewModel> {
  private final Provider<SavedStateHandle> savedStateHandleProvider;

  private final Provider<GameRepository> gameRepositoryProvider;

  private final Provider<PuzzleGenerator> puzzleGeneratorProvider;

  public GameViewModel_Factory(Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<GameRepository> gameRepositoryProvider,
      Provider<PuzzleGenerator> puzzleGeneratorProvider) {
    this.savedStateHandleProvider = savedStateHandleProvider;
    this.gameRepositoryProvider = gameRepositoryProvider;
    this.puzzleGeneratorProvider = puzzleGeneratorProvider;
  }

  @Override
  public GameViewModel get() {
    return newInstance(savedStateHandleProvider.get(), gameRepositoryProvider.get(), puzzleGeneratorProvider.get());
  }

  public static GameViewModel_Factory create(Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<GameRepository> gameRepositoryProvider,
      Provider<PuzzleGenerator> puzzleGeneratorProvider) {
    return new GameViewModel_Factory(savedStateHandleProvider, gameRepositoryProvider, puzzleGeneratorProvider);
  }

  public static GameViewModel newInstance(SavedStateHandle savedStateHandle,
      GameRepository gameRepository, PuzzleGenerator puzzleGenerator) {
    return new GameViewModel(savedStateHandle, gameRepository, puzzleGenerator);
  }
}
