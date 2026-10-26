package et.alpha.meret.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import et.alpha.meret.core.data.tilepack.DefaultTilePackRepository
import et.alpha.meret.core.data.tilepack.TilePackRepository

@Module
@InstallIn(SingletonComponent::class)
public abstract class TilePackModule {
    @Binds
    public abstract fun bindTilePackRepository(
        repository: DefaultTilePackRepository,
    ): TilePackRepository
}
