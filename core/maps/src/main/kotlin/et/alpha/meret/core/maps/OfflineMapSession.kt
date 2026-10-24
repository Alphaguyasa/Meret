package et.alpha.meret.core.maps

import et.alpha.meret.core.maps.server.LocalTileServer
import et.alpha.meret.core.maps.style.StyleProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Keeps the loopback tile server running while at least one map is in the foreground, and
 * gives each map a style that points at it.
 *
 * A map calls [acquire] when it starts and [release] when it stops. The first acquire starts
 * the server and the last release stops it, so no port stays open while nothing draws a map.
 * The port changes on every start, which is why [acquire] writes the style every time
 * instead of caching a URI.
 */
public class OfflineMapSession(
    private val server: LocalTileServer,
    private val styles: StyleProvider,
    private val io: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private var holders = 0

    /**
     * Starts the server if this is the first holder and returns the `file://` style URI for
     * [packId], ready for `Style.Builder().fromUri()`. Pair every successful call with one
     * [release]. A call that throws holds nothing.
     */
    public suspend fun acquire(packId: String): String = mutex.withLock {
        holders++
        var held = false
        try {
            withContext(io) { server.start() }
            styles.write(server.baseUrl(packId) + TILE_JSON).also { held = true }
        } finally {
            if (!held) withContext(NonCancellable) { releaseLocked() }
        }
    }

    /** Gives up one hold. The last one stops the server and closes every open pack. */
    public suspend fun release() {
        withContext(NonCancellable) { mutex.withLock { releaseLocked() } }
    }

    private suspend fun releaseLocked() {
        check(holders > 0) { "release without acquire" }
        holders--
        if (holders == 0) withContext(io) { server.stop() }
    }

    private companion object {
        const val TILE_JSON = "/tiles.json"
    }
}
