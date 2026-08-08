package et.alpha.meret.core.location.filter

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.location.RawFix
import et.alpha.meret.core.model.LocationProvider
import et.alpha.meret.core.model.TrackingConfig
import et.alpha.meret.core.model.geo.LatLng
import org.junit.Test

class ValidityGateTest {
    private val config = TrackingConfig()

    @Test
    fun `accepts a fix inside every limit`() {
        val gate = ValidityGate(config)

        assertThat(gate.evaluate(fix())).isEqualTo(FixVerdict.Accepted)
    }

    @Test
    fun `rejects a fix worse than the accuracy limit`() {
        val gate = ValidityGate(config)

        val verdict = gate.evaluate(fix(accuracy = config.maxAccuracyMetres + 0.1))

        assertThat(verdict).isEqualTo(FixVerdict.Rejected(RejectionReason.ACCURACY))
    }

    @Test
    fun `accepts a fix exactly at the accuracy limit`() {
        val gate = ValidityGate(config)

        assertThat(gate.evaluate(fix(accuracy = config.maxAccuracyMetres)))
            .isEqualTo(FixVerdict.Accepted)
    }

    /**
     * Some devices report `0.0` accuracy on a fix that is hundreds of metres out. Treating that
     * as a perfect measurement would pin the Kalman estimate to it and there would be no
     * recovering the line afterwards.
     */
    @Test
    fun `rejects a fix claiming zero accuracy`() {
        val gate = ValidityGate(config)

        assertThat(gate.evaluate(fix(accuracy = 0.0)))
            .isEqualTo(FixVerdict.Rejected(RejectionReason.ACCURACY))
    }

    @Test
    fun `rejects a repeated monotonic timestamp`() {
        val gate = ValidityGate(config)
        gate.evaluate(fix(nanos = seconds(10)))

        val verdict = gate.evaluate(fix(nanos = seconds(10)))

        assertThat(verdict).isEqualTo(FixVerdict.Rejected(RejectionReason.NON_MONOTONIC_TIME))
    }

    @Test
    fun `rejects a backwards timestamp`() {
        val gate = ValidityGate(config)
        gate.evaluate(fix(nanos = seconds(10)))

        val verdict = gate.evaluate(fix(nanos = seconds(9)))

        assertThat(verdict).isEqualTo(FixVerdict.Rejected(RejectionReason.NON_MONOTONIC_TIME))
    }

    @Test
    fun `rejects an impossible implied speed`() {
        val gate = ValidityGate(config)
        gate.evaluate(fix(nanos = seconds(1)))

        // Roughly 1.1 km north in one second.
        val verdict = gate.evaluate(fix(latitude = START.latitude + 0.01, nanos = seconds(2)))

        assertThat(verdict).isEqualTo(FixVerdict.Rejected(RejectionReason.IMPLIED_SPEED))
    }

    @Test
    fun `rejects a mock fix when the project forbids it`() {
        val gate = ValidityGate(TrackingConfig(allowMockLocations = false))

        assertThat(gate.evaluate(fix(isMock = true)))
            .isEqualTo(FixVerdict.Rejected(RejectionReason.MOCK_NOT_ALLOWED))
    }

    @Test
    fun `accepts a mock fix when the project allows it`() {
        val gate = ValidityGate(TrackingConfig(allowMockLocations = true))

        assertThat(gate.evaluate(fix(isMock = true))).isEqualTo(FixVerdict.Accepted)
    }

    /**
     * `LOCATION_ENGINE.md` lists duplicate coordinates among the things to handle, and the
     * handling is not rejection: a device sitting still is a true observation and belongs in
     * the track. Marking a stationary enumerator's whole lunch break as outliers would destroy
     * exactly the evidence that `is_outlier` exists to preserve.
     */
    @Test
    fun `accepts an identical coordinate at a later time as stationary`() {
        val gate = ValidityGate(config)
        gate.evaluate(fix(nanos = seconds(1)))

        val verdict = gate.evaluate(fix(nanos = seconds(2)))

        assertThat(verdict).isEqualTo(FixVerdict.Accepted)
    }

    /**
     * The reference for the speed check has to be the last *accepted* fix. If a rejected
     * outlier became the reference, the next genuine fix would be judged as a 200 m jump away
     * from it and be rejected too — one bad fix would take the rest of the track with it.
     */
    @Test
    fun `an outlier does not become the reference for the fixes behind it`() {
        val gate = ValidityGate(config)
        gate.evaluate(fix(nanos = seconds(1)))

        val outlier = gate.evaluate(fix(latitude = START.latitude + 0.01, nanos = seconds(2)))
        val recovered = gate.evaluate(fix(latitude = START.latitude + 0.00002, nanos = seconds(3)))

        assertThat(outlier).isInstanceOf(FixVerdict.Rejected::class.java)
        assertThat(recovered).isEqualTo(FixVerdict.Accepted)
        assertThat(gate.lastAcceptedFix?.elapsedRealtimeNanos).isEqualTo(seconds(3))
    }

    @Test
    fun `reset forgets the reference so the next fix is taken at face value`() {
        val gate = ValidityGate(config)
        gate.evaluate(fix(nanos = seconds(10)))

        gate.reset()

        // Both a backwards timestamp and a huge jump would fail against the old reference.
        assertThat(gate.evaluate(fix(latitude = START.latitude + 0.01, nanos = seconds(1))))
            .isEqualTo(FixVerdict.Accepted)
    }

    private fun fix(
        latitude: Double = START.latitude,
        longitude: Double = START.longitude,
        accuracy: Double = 4.0,
        nanos: Long = seconds(1),
        isMock: Boolean = false,
    ) = RawFix(
        position = LatLng(latitude, longitude),
        accuracyMetres = accuracy,
        elapsedRealtimeNanos = nanos,
        recordedAtMillis = 1_773_000_000_000L,
        provider = LocationProvider.GPS,
        isMock = isMock,
    )

    private fun seconds(value: Long) = value * 1_000_000_000L

    private companion object {
        val START = LatLng(8.9806, 38.7578)
    }
}
