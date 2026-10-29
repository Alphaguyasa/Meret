package et.alpha.meret.feature.tiles.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import et.alpha.meret.feature.tiles.ContentPickedFileSource
import et.alpha.meret.feature.tiles.PickedFileSource

@Module
@InstallIn(SingletonComponent::class)
object TilesModule {
    @Provides
    fun providePickedFileSource(
        @ApplicationContext context: Context,
    ): PickedFileSource = ContentPickedFileSource(context.contentResolver)
}
