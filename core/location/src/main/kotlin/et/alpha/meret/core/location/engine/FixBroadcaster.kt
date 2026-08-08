package et.alpha.meret.core.location.engine

import et.alpha.meret.core.location.RawFix
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The hot fan-out every engine publishes through.
 *
 * Exists because all three engines face the same problem: fixes arrive on a platform
 * callback thread that must not be blocked, and the consumer is a coroutine that batches
 * them into Room.
 *
 * ## Why the buffer is deep and the overflow is counted
 *
 * [offer] is non-suspending, so a slow consumer cannot stall the platform's callback
 * thread — blocking it risks the OS deciding the app is unresponsive, and on the OEMs this
 * app targets that is a reason to kill the service.
 *
 * The consequence is that a sufficiently slow consumer loses fixes. [FIX_BUFFER_CAPACITY]
 * is about four minutes of slack at the fastest profile's 1 Hz, so reaching it means
 * something is badly wrong rather than merely busy. When it happens, [droppedFixCount]
 * counts it. That number exists so the loss is never silent: a track with a hole in it and
 * a drop count of 12 is a diagnosable bug, and the same track with no counter is a
 * mystery. `TrackingHealthMonitor` surfaces it on the diagnostics screen.
 *
 * With no subscribers at all, offers complete and go nowhere. That is not a drop — nobody
 * asked for them — and it is what makes collecting [fixes] before [LocationEngine.start]
 * legal.
 */
internal class FixBroadcaster {
    private val mutableFixes =
        MutableSharedFlow<RawFix>(
            replay = 0,
            extraBufferCapacity = FIX_BUFFER_CAPACITY,
            // SUSPEND rather than DROP_OLDEST specifically so `tryEmit` reports failure.
            // DROP_OLDEST always returns true, which would make the loss undetectable.
            onBufferOverflow = BufferOverflow.SUSPEND,
        )

    private val mutableDroppedFixCount = MutableStateFlow(0)

    val fixes: SharedFlow<RawFix> = mutableFixes.asSharedFlow()

    val droppedFixCount: StateFlow<Int> = mutableDroppedFixCount.asStateFlow()

    /** Publishes a fix without suspending. Safe to call from a platform callback thread. */
    fun offer(fix: RawFix) {
        if (!mutableFixes.tryEmit(fix)) {
            mutableDroppedFixCount.update { it + 1 }
        }
    }

    private companion object {
        const val FIX_BUFFER_CAPACITY = 256
    }
}
