package et.alpha.meret.core.maps.view

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import et.alpha.meret.core.maps.OfflineMapSession
import kotlinx.coroutines.awaitCancellation
import java.io.IOException

/**
 * Holds [session] for [packId] each time [lifecycle] is started and gives it up on stop, so
 * the loopback port is open only while the map can be seen. Every start brings a new port,
 * so [applyStyle] receives a freshly written style URI each time. Returns once the lifecycle
 * is destroyed.
 *
 * A pack id the server refuses or a style that cannot be written leaves the map blank for
 * that start, holding nothing, rather than crashing the screen; the next start tries again.
 */
internal suspend fun holdStyleWhileStarted(
    lifecycle: Lifecycle,
    session: OfflineMapSession,
    packId: String,
    applyStyle: suspend (styleUri: String) -> Unit,
) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        val styleUri = acquireOrNull(session, packId)
        if (styleUri != null) {
            try {
                applyStyle(styleUri)
                awaitCancellation()
            } finally {
                session.release()
            }
        }
    }
}

private suspend fun acquireOrNull(
    session: OfflineMapSession,
    packId: String,
): String? = try {
    session.acquire(packId)
} catch (_: IOException) {
    null
} catch (_: IllegalArgumentException) {
    null
}
