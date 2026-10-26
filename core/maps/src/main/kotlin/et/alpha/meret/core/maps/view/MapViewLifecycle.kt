package et.alpha.meret.core.maps.view

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/** The lifecycle calls `MapView` needs. A seam so the forwarding is testable on the JVM. */
internal interface MapLifecycleTarget {
    fun onCreate()

    fun onStart()

    fun onResume()

    fun onPause()

    fun onStop()

    fun onDestroy()
}

/**
 * Forwards host lifecycle events to a `MapView`, which leaks its native renderer and GL
 * context unless every call arrives, in order.
 *
 * The map can also leave the composition while the screen stays resumed (navigation to
 * another tab). [dispose] then winds the view down the rest of the way, so `onDestroy` runs
 * exactly once whichever happens first.
 */
internal class MapLifecycleForwarder(
    private val target: MapLifecycleTarget,
) : LifecycleEventObserver {
    private var state = Lifecycle.State.INITIALIZED

    override fun onStateChanged(
        source: LifecycleOwner,
        event: Lifecycle.Event,
    ) {
        moveTo(event.targetState)
    }

    /** Stops and destroys the view if the host has not already. */
    fun dispose() {
        moveTo(Lifecycle.State.DESTROYED)
    }

    private fun moveTo(next: Lifecycle.State) {
        if (state == Lifecycle.State.DESTROYED) return
        while (state != next) {
            state =
                when {
                    next == Lifecycle.State.DESTROYED -> down()
                    next > state -> up()
                    else -> down()
                }
        }
    }

    private fun up(): Lifecycle.State = when (state) {
        Lifecycle.State.INITIALIZED -> Lifecycle.State.CREATED.also { target.onCreate() }
        Lifecycle.State.CREATED -> Lifecycle.State.STARTED.also { target.onStart() }
        else -> Lifecycle.State.RESUMED.also { target.onResume() }
    }

    private fun down(): Lifecycle.State = when (state) {
        Lifecycle.State.RESUMED -> Lifecycle.State.STARTED.also { target.onPause() }

        Lifecycle.State.STARTED -> Lifecycle.State.CREATED.also { target.onStop() }

        Lifecycle.State.CREATED -> Lifecycle.State.DESTROYED.also { target.onDestroy() }

        // Never created: nothing native to free.
        else -> Lifecycle.State.DESTROYED
    }
}
