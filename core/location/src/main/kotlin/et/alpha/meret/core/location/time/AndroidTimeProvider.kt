package et.alpha.meret.core.location.time

import android.os.SystemClock
import et.alpha.meret.core.common.time.TimeProvider

/**
 * The [TimeProvider] the app actually runs on.
 *
 * `:core:common`'s `SystemTimeProvider` uses [System.nanoTime], which on Android **excludes
 * time spent in deep sleep**. A device that dozes for ten minutes in a field worker's pocket
 * would see almost no elapsed time, so a real ten-minute gap would look like a two-second one
 * — the Kalman filter would not reset, the segment would not break, and the app would draw a
 * confident straight line across ten minutes of unobserved travel.
 *
 * [SystemClock.elapsedRealtimeNanos] counts through deep sleep, which is the property the
 * whole ordering-and-gap scheme depends on. Its KDoc in `:core:common` already names this
 * module as the place the correct binding lives; this is it.
 *
 * Still meaningless across a reboot and across devices — it is time since *this* boot. That
 * is why it is an intra-track ordering key and never a sync key.
 */
public class AndroidTimeProvider : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun elapsedRealtimeNanos(): Long = SystemClock.elapsedRealtimeNanos()
}
