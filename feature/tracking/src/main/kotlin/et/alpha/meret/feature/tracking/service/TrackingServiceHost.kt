package et.alpha.meret.feature.tracking.service

import et.alpha.meret.core.data.tracking.TrackingController

/**
 * Implemented by the `Application`, which hands [TrackingService] its controller.
 *
 * A plain interface rather than Hilt field injection into the service, so the service runs
 * under Robolectric with a test application and a fake controller, with no Hilt test
 * component. The app's implementation reads the Hilt singleton.
 */
interface TrackingServiceHost {
    val trackingController: TrackingController
}
