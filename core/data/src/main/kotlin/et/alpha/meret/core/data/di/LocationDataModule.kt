package et.alpha.meret.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import et.alpha.meret.core.data.location.DefaultDevicePositionRepository
import et.alpha.meret.core.data.location.DevicePositionRepository

@Module
@InstallIn(SingletonComponent::class)
public abstract class LocationDataModule {
    @Binds
    public abstract fun bindDevicePositionRepository(
        repository: DefaultDevicePositionRepository,
    ): DevicePositionRepository
}
