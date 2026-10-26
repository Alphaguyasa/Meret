package et.alpha.meret.core.maps.view

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import et.alpha.meret.core.maps.OfflineMapSession
import kotlinx.coroutines.awaitCancellation

/**
 * Holds [session] for [packId] each time [lifecycle] is started and gives it up on stop, so
 * the loopback port is open only while the map can be seen. Every start brings a new port,
 * so [applyStyle] receives a freshly written style URI each time. Returns once the lifecycle
 * is destroyed.
 */
internal suspend fun holdStyleWhileStarted(
    lifecycle: Lifecycle,
    session: OfflineMapSession,
    packId: String,
    applyStyle: suspend (styleUri: String) -> Unit,
) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        val styleUri = session.acquire(packId)
        try {
            applyStyle(styleUri)
            awaitCancellation()
        } finally {
            session.release()
        }
    }
}
