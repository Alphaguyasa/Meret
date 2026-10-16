package et.alpha.meret

import android.app.Application
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import et.alpha.meret.core.data.tracking.TrackingController
import et.alpha.meret.feature.tracking.service.TrackingService
import et.alpha.meret.feature.tracking.service.TrackingServiceHost
import javax.inject.Inject

@HiltAndroidApp
class MeretApplication :
    Application(),
    TrackingServiceHost {
    /**
     * Lazy, so app start does not open Room, DataStore and the location engine before
     * anything needs them; only [TrackingService] does.
     */
    @Inject
    lateinit var trackingControllerProvider: Lazy<TrackingController>

    override val trackingController: TrackingController
        get() = trackingControllerProvider.get()
}
