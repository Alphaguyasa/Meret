package et.alpha.meret.core.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import et.alpha.meret.core.data.waypoint.BundledWaypointFormSource
import et.alpha.meret.core.data.waypoint.RoomWaypointRepository
import et.alpha.meret.core.data.waypoint.WaypointRepository
import et.alpha.meret.domain.waypoint.SaveWaypointUseCase
import et.alpha.meret.domain.waypoint.WaypointFormSource
import et.alpha.meret.domain.waypoint.WaypointStore

@Module
@InstallIn(SingletonComponent::class)
public abstract class WaypointModule {
    @Binds
    public abstract fun bindWaypointRepository(
        repository: RoomWaypointRepository,
    ): WaypointRepository

    @Binds
    public abstract fun bindWaypointStore(repository: WaypointRepository): WaypointStore

    @Binds
    public abstract fun bindWaypointFormSource(
        source: BundledWaypointFormSource,
    ): WaypointFormSource

    public companion object {
        /** `:domain` has no `@Inject`. */
        @Provides
        public fun provideSaveWaypointUseCase(store: WaypointStore): SaveWaypointUseCase =
            SaveWaypointUseCase(store)
    }
}
