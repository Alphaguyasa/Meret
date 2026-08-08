package et.alpha.meret.core.location

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import java.io.Closeable
import java.util.concurrent.Executor

/**
 * The thread every location callback is delivered on.
 *
 * ## Why not the main thread
 *
 * Nothing done in a location callback is expensive — a fix is converted and handed to a
 * buffer. The problem is *when* it happens. Delivered on the main looper, a fix queues
 * behind whatever the UI is doing, so panning the map or scrolling a form delays delivery
 * and clumps the arrival times of several fixes together. Those arrival times do not matter
 * (`elapsedRealtimeNanos` comes from the fix, not from the clock at delivery), but the
 * bunching does: it makes the batch writer fire in bursts, and it makes a real OEM-induced
 * gap indistinguishable from a busy UI on the diagnostics screen.
 *
 * ## Why a thread and not just an Executor
 *
 * Because `minSdk` is 26 and the platform APIs disagree about how to be told where to
 * deliver. Checked against `api-versions.xml` for android-37:
 *
 * - `LocationManager.requestLocationUpdates(String, long, float, Executor, LocationListener)`
 *   is **API 30**. The `Looper` overload is API 1, so [looper] is what the platform engine
 *   must use.
 * - `LocationManager.registerGnssStatusCallback(Callback, Handler)` is API 24, while the
 *   `Executor` form is API 30 — so the GNSS monitor needs [handler].
 * - Play Services' `requestLocationUpdates(..., Executor, LocationCallback)` has no such
 *   floor, so the fused engine can take [executor].
 *
 * One `HandlerThread` backs all three views of it, so there is a single place where location
 * callbacks are delivered rather than three.
 *
 * [close] stops the thread. A subsystem-lifetime singleton in practice, so it is only closed
 * when the process is going away anyway — but a leaked `HandlerThread` is a real leak, and
 * the tests that construct one need to be able to end it.
 */
public class LocationCallbackThread(
    name: String = DEFAULT_THREAD_NAME,
) : Closeable {
    private val thread = HandlerThread(name).apply { start() }

    /** For `LocationManager.requestLocationUpdates`, which needs a `Looper` below API 30. */
    public val looper: Looper get() = thread.looper

    /** For `LocationManager.registerGnssStatusCallback`, which needs a `Handler`. */
    public val handler: Handler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { Handler(looper) }

    /** For Play Services, which takes an `Executor` at any API level. */
    public val executor: Executor by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Executor { command -> handler.post(command) }
    }

    /**
     * Stops the thread, letting already-queued callbacks run first.
     *
     * `quitSafely`, not `quit`: a fix already on the queue when tracking stops is a real
     * observation, and dropping it would leave the track one point shorter than the file the
     * device actually recorded.
     */
    override fun close() {
        thread.quitSafely()
    }

    private companion object {
        const val DEFAULT_THREAD_NAME = "meret-location"
    }
}
