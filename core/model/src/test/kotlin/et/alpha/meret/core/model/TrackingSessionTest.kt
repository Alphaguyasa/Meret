package et.alpha.meret.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class TrackingSessionTest {
    @Test
    fun `a new session starts in segment zero, recording`() {
        val session = session()

        assertThat(session.segmentIndex).isEqualTo(0)
        assertThat(session.isPaused).isFalse()
    }

    @Test
    fun `a blank track id is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { session(trackId = " ") }
    }

    @Test
    fun `a negative segment is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { session(segmentIndex = -1) }
    }

    @Test
    fun `a negative elapsed-realtime start is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            session(startedAtElapsedRealtimeNanos = -1L)
        }
    }

    private fun session(
        trackId: String = "0192f0a4-7c1e-7000-8000-000000000001",
        segmentIndex: Int = 0,
        startedAtElapsedRealtimeNanos: Long = 5_000_000_000L,
    ) = TrackingSession(
        trackId = trackId,
        segmentIndex = segmentIndex,
        startedAtMillis = 1_760_500_000_000L,
        startedAtElapsedRealtimeNanos = startedAtElapsedRealtimeNanos,
    )
}
