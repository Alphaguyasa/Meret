package et.alpha.meret.core.common.uuid

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.time.TimeProvider
import org.junit.Test
import java.util.UUID
import kotlin.random.Random

class UuidV7Test {
    @Test
    fun `generated ids carry version seven and the RFC 4122 variant`() {
        val generator = UuidV7Generator(FakeTimeProvider(1_700_000_000_000L), Random(1))

        repeat(1_000) {
            val parsed = UUID.fromString(generator.generate())

            assertThat(parsed.version()).isEqualTo(7)
            assertThat(parsed.variant()).isEqualTo(2) // RFC 4122, the 0b10 variant
        }
    }

    @Test
    fun `the embedded timestamp is the generation time`() {
        val now = 1_754_400_000_000L
        val generator = UuidV7Generator(FakeTimeProvider(now), Random(2))

        val id = generator.generate()

        assertThat(uuidV7TimestampMillis(id)).isEqualTo(now)
    }

    @Test
    fun `ids generated within a single millisecond still sort in generation order`() {
        // The whole point of the counter. A batch of twenty track points is written
        // inside one millisecond, and their keys must not scatter across the index.
        val generator = UuidV7Generator(FakeTimeProvider(1_700_000_000_000L), Random(3))

        val ids = List(10_000) { generator.generate() }

        assertThat(ids).isInStrictOrder()
    }

    @Test
    fun `ids remain ordered as the clock advances`() {
        val clock = FakeTimeProvider(1_700_000_000_000L)
        val generator = UuidV7Generator(clock, Random(4))

        val ids =
            List(5_000) {
                if (it % 7 == 0) clock.advanceMillis(1)
                generator.generate()
            }

        assertThat(ids).isInStrictOrder()
    }

    @Test
    fun `a clock jumping backwards does not produce ids that sort backwards`() {
        // Field devices genuinely do this: no network for weeks, then NTP corrects the
        // date by hours. An id that sorts before rows already written would break both
        // the outbox drain order and every keyset pagination cursor.
        val clock = FakeTimeProvider(1_700_000_000_000L)
        val generator = UuidV7Generator(clock, Random(5))

        val before = List(100) { generator.generate() }
        clock.setMillis(1_600_000_000_000L)
        val after = List(100) { generator.generate() }

        assertThat(before + after).isInStrictOrder()
    }

    @Test
    fun `the counter rolls over into the next millisecond without repeating`() {
        // 4096 ids in one millisecond exceeds the 12-bit counter no matter how it is
        // seeded, which forces the rollover path.
        val generator = UuidV7Generator(FakeTimeProvider(1_700_000_000_000L), Random(6))

        val ids = List(20_000) { generator.generate() }

        assertThat(ids.toSet()).hasSize(20_000)
        assertThat(ids).isInStrictOrder()
    }

    @Test
    fun `ids are unique across a large batch with a real random source`() {
        val generator = UuidV7Generator(SteadyTimeProvider())

        val ids = List(100_000) { generator.generate() }

        assertThat(ids.toSet()).hasSize(100_000)
    }

    @Test
    fun `concurrent generation stays unique and well formed`() {
        val generator = UuidV7Generator(SteadyTimeProvider())
        val threadCount = 8
        val perThread = 5_000
        val results = List(threadCount) { mutableListOf<String>() }

        val threads =
            List(threadCount) { index ->
                Thread { repeat(perThread) { results[index].add(generator.generate()) } }
            }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        val all = results.flatten()
        assertThat(all).hasSize(threadCount * perThread)
        assertThat(all.toSet()).hasSize(threadCount * perThread)
    }

    @Test
    fun `timestamp extraction rejects anything that is not a version seven uuid`() {
        assertThat(uuidV7TimestampMillis("not-a-uuid")).isNull()
        assertThat(uuidV7TimestampMillis("")).isNull()
        assertThat(uuidV7TimestampMillis(UUID.randomUUID().toString())).isNull() // v4
    }

    private class FakeTimeProvider(
        private var millis: Long,
    ) : TimeProvider {
        override fun nowMillis(): Long = millis

        override fun elapsedRealtimeNanos(): Long = millis * 1_000_000L

        fun advanceMillis(delta: Long) {
            millis += delta
        }

        fun setMillis(value: Long) {
            millis = value
        }
    }

    /** A clock that advances by one millisecond per read, so the counter never rolls. */
    private class SteadyTimeProvider : TimeProvider {
        private var millis = 1_700_000_000_000L

        override fun nowMillis(): Long = millis++

        override fun elapsedRealtimeNanos(): Long = millis * 1_000_000L
    }
}
