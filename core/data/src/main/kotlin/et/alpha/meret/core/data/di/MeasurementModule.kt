package et.alpha.meret.core.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import et.alpha.meret.core.data.measure.MeasureRingDraftRepository
import et.alpha.meret.core.data.measure.MeasurementRepository
import et.alpha.meret.domain.measure.MeasureRingDraft
import et.alpha.meret.domain.measure.MeasurementStore
import et.alpha.meret.domain.measure.SaveMeasurementUseCase

@Module
@InstallIn(SingletonComponent::class)
public abstract class MeasurementModule {
    @Binds
    public abstract fun bindMeasurementStore(repository: MeasurementRepository): MeasurementStore

    @Binds
    public abstract fun bindMeasureRingDraft(
        repository: MeasureRingDraftRepository,
    ): MeasureRingDraft

    public companion object {
        /** `:domain` has no `@Inject`. */
        @Provides
        public fun provideSaveMeasurementUseCase(store: MeasurementStore): SaveMeasurementUseCase =
            SaveMeasurementUseCase(store)
    }
}
