package com.eink.hidato.di;

import com.eink.hidato.domain.HidatoSolver;
import com.eink.hidato.domain.PuzzleGenerator;
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
public final class AppModule_ProvidePuzzleGeneratorFactory implements Factory<PuzzleGenerator> {
  private final Provider<HidatoSolver> solverProvider;

  public AppModule_ProvidePuzzleGeneratorFactory(Provider<HidatoSolver> solverProvider) {
    this.solverProvider = solverProvider;
  }

  @Override
  public PuzzleGenerator get() {
    return providePuzzleGenerator(solverProvider.get());
  }

  public static AppModule_ProvidePuzzleGeneratorFactory create(
      Provider<HidatoSolver> solverProvider) {
    return new AppModule_ProvidePuzzleGeneratorFactory(solverProvider);
  }

  public static PuzzleGenerator providePuzzleGenerator(HidatoSolver solver) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.providePuzzleGenerator(solver));
  }
}
