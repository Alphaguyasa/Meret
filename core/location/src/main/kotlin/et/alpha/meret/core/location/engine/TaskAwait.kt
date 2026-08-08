package et.alpha.meret.core.location.engine

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Awaits a Play Services [Task], mapping failure to `null`.
 *
 * Hand-rolled rather than pulling in `kotlinx-coroutines-play-services` for one call site.
 * The library's `await()` is the right thing at three or four call sites; at one it is a
 * transitive dependency to keep in step with the coroutines version forever.
 *
 * A failed `getLastLocation()` is ordinary: no cached fix, location disabled, Play Services
 * mid-update. `null` is the honest answer and the caller — which only wants somewhere to
 * centre the map — treats it as "no opinion".
 *
 * [SecurityException] is the exception, and is rethrown. A revoked location permission is not
 * "no cached fix"; it is a thing the user must be asked about, and swallowing it here would
 * turn a fixable prompt into a map that mysteriously never moves.
 *
 * The Task API has no cancellation hook to call on coroutine cancellation, so the underlying
 * work is left to finish and its result discarded. `getLastLocation()` is a cache read; there
 * is nothing worth cancelling.
 */
internal suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        val failure = task.exception
        when {
            task.isSuccessful -> continuation.resume(task.result)
            failure is SecurityException -> continuation.resumeWithException(failure)
            else -> continuation.resume(null)
        }
    }
}
