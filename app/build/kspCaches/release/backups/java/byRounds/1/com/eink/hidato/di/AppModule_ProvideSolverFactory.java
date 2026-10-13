package com.eink.hidato.di;

import com.eink.hidato.domain.HidatoSolver;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class AppModule_ProvideSolverFactory implements Factory<HidatoSolver> {
  @Override
  public HidatoSolver get() {
    return provideSolver();
  }

  public static AppModule_ProvideSolverFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static HidatoSolver provideSolver() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideSolver());
  }

  private static final class InstanceHolder {
    private static final AppModule_ProvideSolverFactory INSTANCE = new AppModule_ProvideSolverFactory();
  }
}
