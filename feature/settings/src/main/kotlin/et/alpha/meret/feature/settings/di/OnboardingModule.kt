package et.alpha.meret.feature.settings.di

import android.content.Context
import android.os.Build
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import et.alpha.meret.core.location.compat.OemGuidance
import et.alpha.meret.core.location.compat.oemGuidanceFor
import et.alpha.meret.feature.settings.diagnostics.AndroidDeviceInfoSource
import et.alpha.meret.feature.settings.diagnostics.DeviceInfoSource
import et.alpha.meret.feature.settings.onboarding.AndroidPermissionSnapshotSource
import et.alpha.meret.feature.settings.onboarding.PermissionSnapshotSource

@Module
@InstallIn(SingletonComponent::class)
object OnboardingModule {
    /** The battery-manager guidance for the phone this runs on. Fixed for the process. */
    @Provides
    fun provideOemGuidance(): OemGuidance = oemGuidanceFor(Build.MANUFACTURER, Build.BRAND)

    @Provides
    fun providePermissionSnapshotSource(
        @ApplicationContext context: Context,
        oemGuidance: OemGuidance,
    ): PermissionSnapshotSource = AndroidPermissionSnapshotSource(context, oemGuidance)

    @Provides
    fun provideDeviceInfoSource(
        @ApplicationContext context: Context,
    ): DeviceInfoSource = AndroidDeviceInfoSource(context)
}
