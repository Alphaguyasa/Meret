package et.alpha.meret.core.common.time

/**
 * The only source of time in the app.
 *
 * Two clocks, deliberately, because they answer different questions:
 *
 * - [nowMillis] is wall clock. It is what a human reads and what the server compares. On
 *   a field device it is frequently wrong — hours or years wrong, on a phone that has
 *   never seen a network. Never order anything by it.
 * - [elapsedRealtimeNanos] is monotonic since boot. It cannot be set by the user, does not
 *   jump when NTP corrects the clock, and is therefore the ordering key for fixes within
 *   a track. It is meaningless across a reboot and across devices.
 */
public interface TimeProvider {
    public fun nowMillis(): Long

    public fun elapsedRealtimeNanos(): Long
}

/**
 * JVM implementation.
 *
 * [elapsedRealtimeNanos] uses [System.nanoTime], which excludes time spent in deep sleep.
 * On Android that is wrong — a device that dozes for ten minutes must still see ten
 * minutes elapse — so `:core:location` binds an implementation backed by
 * `SystemClock.elapsedRealtimeNanos()` instead. This one exists for JVM modules and tests.
 */
public class SystemTimeProvider : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun elapsedRealtimeNanos(): Long = System.nanoTime()
}
