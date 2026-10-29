package et.alpha.meret.core.common.flow

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform

/**
 * At most one value per [periodMillis]: the first at once, then the newest one after each
 * period. Values in between are dropped, never the last one.
 *
 * Unlike `sample`, nothing waits a period for the first value, and the final value arrives
 * even if it came just after the previous one.
 */
public fun <T> Flow<T>.throttleLatest(periodMillis: Long): Flow<T> = conflate().transform {
    emit(it)
    delay(periodMillis)
}
