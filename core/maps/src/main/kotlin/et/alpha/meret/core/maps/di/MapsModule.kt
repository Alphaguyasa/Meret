package et.alpha.meret.core.maps.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.maps.OfflineMapSession
import et.alpha.meret.core.maps.server.LocalTileServer
import et.alpha.meret.core.maps.server.MbtilesPackInstaller
import et.alpha.meret.core.maps.server.PackDirectoryResolver
import et.alpha.meret.core.maps.server.TilePackInstaller
import et.alpha.meret.core.maps.style.StyleProvider
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
public object MapsModule {
    /**
     * One session per process: it reference-counts the single loopback server, and a second
     * session would start a second server on another port.
     */
    @Provides
    @Singleton
    public fun provideOfflineMapSession(
        @ApplicationContext context: Context,
        dispatchers: DispatcherProvider,
    ): OfflineMapSession {
        val packs = File(context.filesDir, PackDirectoryResolver.APP_DIRECTORY)
        val styles =
            StyleProvider(
                openTemplate = { context.assets.open(StyleProvider.TEMPLATE_ASSET) },
                cacheDir = context.cacheDir,
                io = dispatchers.io,
            )
        val server = LocalTileServer(PackDirectoryResolver(packs))
        return OfflineMapSession(server, styles, dispatchers.io)
    }

    /** Singleton: installs run one at a time, and the lock is the instance's. */
    @Provides
    @Singleton
    public fun provideTilePackInstaller(
        @ApplicationContext context: Context,
    ): TilePackInstaller =
        MbtilesPackInstaller(File(context.filesDir, PackDirectoryResolver.APP_DIRECTORY))
}
