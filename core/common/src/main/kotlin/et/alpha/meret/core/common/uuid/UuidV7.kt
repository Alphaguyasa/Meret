package et.alpha.meret.core.common.uuid

import et.alpha.meret.core.common.time.TimeProvider
import java.util.UUID
import kotlin.random.Random

/**
 * Generates RFC 9562 version 7 UUIDs.
 *
 * Every primary key in this app is client-generated, which is what makes every server
 * write idempotent and every sync operation safely retryable. Version 7 additionally
 * gives us time-ordered keys: B-tree inserts stay at the right-hand edge instead of
 * scattering, and ids sort chronologically without a join.
 *
 * Layout, 128 bits:
 * ```
 * | 48 bits unix_ts_ms | 4 bits version=7 | 12 bits counter | 2 bits variant=0b10 | 62 bits random |
 * ```
 *
 * ## Monotonicity
 *
 * The 12-bit `rand_a` field is used as a monotonic counter (RFC 9562 §6.2, method 1)
 * rather than as random bits, so ids generated inside the same millisecond still sort in
 * generation order. A track writing twenty points per batch would otherwise produce
 * unordered keys for the whole batch.
 *
 * The counter is seeded randomly in the low 11 bits, leaving at least 2048 increments of
 * headroom within a millisecond before rollover.
 *
 * ## Wrong clocks
 *
 * If [TimeProvider.nowMillis] goes *backwards* — an NTP correction, or a user setting the
 * date — the generator holds its previous timestamp and keeps counting rather than
 * emitting ids that sort before ones already written. Field devices have wrong clocks
 * routinely; this is not a hypothetical.
 *
 * Thread-safe.
 */
public class UuidV7Generator(
    private val timeProvider: TimeProvider,
    private val random: Random = Random.Default,
) {
    private val lock = Any()
    private var lastMillis: Long = Long.MIN_VALUE
    private var counter: Int = 0

    /** A canonical lowercase UUIDv7 string. */
    public fun generate(): String {
        val now = timeProvider.nowMillis()
        val timestamp: Long
        val sequence: Int

        synchronized(lock) {
            if (now > lastMillis) {
                lastMillis = now
                counter = random.nextInt(COUNTER_SEED_BOUND)
            } else {
                counter++
                if (counter > MAX_COUNTER) {
                    // Borrow from the next millisecond. Ids stay ordered and stay unique;
                    // the timestamp is at most a few milliseconds ahead of the clock.
                    lastMillis++
                    counter = random.nextInt(COUNTER_SEED_BOUND)
                }
            }
            timestamp = lastMillis
            sequence = counter
        }

        val mostSignificant =
            ((timestamp and TIMESTAMP_MASK) shl TIMESTAMP_SHIFT) or
                (VERSION_7 shl VERSION_SHIFT) or
                sequence.toLong()

        val leastSignificant =
            (random.nextLong() and RAND_B_MASK) or VARIANT_RFC_4122

        return UUID(mostSignificant, leastSignificant).toString()
    }

    private companion object {
        const val MAX_COUNTER = 0xFFF
        const val COUNTER_SEED_BOUND = 0x800

        const val TIMESTAMP_MASK = 0xFFFF_FFFF_FFFFL
        const val TIMESTAMP_SHIFT = 16
        const val VERSION_7 = 0x7L
        const val VERSION_SHIFT = 12

        /** Clears the top two bits so the variant can be written into them. */
        const val RAND_B_MASK = 0x3FFF_FFFF_FFFF_FFFFL

        /** Bit 63 set, bit 62 clear: the variant field's `0b10`. `0x8000_0000_0000_0000`. */
        const val VARIANT_RFC_4122 = Long.MIN_VALUE
    }
}

/**
 * Reads the embedded epoch-millisecond timestamp out of a version 7 UUID string.
 *
 * Returns `null` if the string is not a valid UUID, or is not version 7. Used by
 * diagnostics and by the outbox to age rows without a separate column.
 */
public fun uuidV7TimestampMillis(uuid: String): Long? = runCatching { UUID.fromString(uuid) }
    .getOrNull()
    ?.takeIf { it.version() == UUID_VERSION_7 }
    ?.let { it.mostSignificantBits ushr UUID_TIMESTAMP_SHIFT }

private const val UUID_VERSION_7 = 7
private const val UUID_TIMESTAMP_SHIFT = 16
