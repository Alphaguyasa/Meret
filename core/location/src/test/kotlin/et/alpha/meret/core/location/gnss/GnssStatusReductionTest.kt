package et.alpha.meret.core.location.gnss

import android.location.GnssStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [toSnapshot] against a real [GnssStatus].
 *
 * Needs Robolectric because `GnssStatus` is final with no public constructor —
 * `GnssStatus.Builder` arrived in API 30, and the SDK level is pinned to 34 in
 * `src/test/resources/robolectric.properties` for the whole module.
 *
 * The reduction is small, but it is the only place that decides which satellites count, and
 * getting `usedInFix` backwards would make every quality reading wrong in a way no other test
 * would catch.
 */
@RunWith(RobolectricTestRunner::class)
class GnssStatusReductionTest {
    @Test
    fun `counts only satellites used in the fix`() {
        val status =
            gnssStatus(
                satellite(cn0 = 40f, usedInFix = true),
                satellite(cn0 = 38f, usedInFix = true),
                satellite(cn0 = 12f, usedInFix = false),
                satellite(cn0 = 15f, usedInFix = false),
            )

        val snapshot = status.toSnapshot(timeToFirstFixMillis = null)

        assertThat(snapshot.satellitesVisible).isEqualTo(4)
        assertThat(snapshot.satellitesUsedInFix).isEqualTo(2)
    }

    /**
     * Averaging every visible satellite would fold in the ones the receiver already decided to
     * ignore, and the number would then say more about how many satellites happen to be over the
     * horizon than about the quality of the fix.
     */
    @Test
    fun `the carrier to noise mean ignores unused satellites`() {
        val status =
            gnssStatus(
                satellite(cn0 = 40f, usedInFix = true),
                satellite(cn0 = 30f, usedInFix = true),
                satellite(cn0 = 5f, usedInFix = false),
            )

        val snapshot = status.toSnapshot(timeToFirstFixMillis = null)

        assertThat(snapshot.meanCarrierToNoiseDbHz).isWithin(0.001).of(35.0)
    }

    /**
     * A satellite reporting 0.0 dB-Hz while being *used* in the fix is a firmware quirk, not a
     * satellite at the noise floor. Low-end hardware does this, and letting it drag the mean to
     * zero would downgrade a perfectly good fix.
     */
    @Test
    fun `a zero carrier to noise reading is excluded from the mean but still counted`() {
        val status =
            gnssStatus(
                satellite(cn0 = 40f, usedInFix = true),
                satellite(cn0 = 0f, usedInFix = true),
                satellite(cn0 = 36f, usedInFix = true),
                satellite(cn0 = 44f, usedInFix = true),
            )

        val snapshot = status.toSnapshot(timeToFirstFixMillis = null)

        assertThat(snapshot.satellitesUsedInFix).isEqualTo(4)
        assertThat(snapshot.meanCarrierToNoiseDbHz).isWithin(0.001).of(40.0)
        assertThat(snapshot.quality).isEqualTo(GnssSignalQuality.FAIR)
    }

    @Test
    fun `no usable readings leaves the mean unknown rather than zero`() {
        val status =
            gnssStatus(
                satellite(cn0 = 0f, usedInFix = true),
                satellite(cn0 = 0f, usedInFix = true),
            )

        val snapshot = status.toSnapshot(timeToFirstFixMillis = null)

        // Unknown, so it must not trigger the weak-signal downgrade.
        assertThat(snapshot.meanCarrierToNoiseDbHz).isNull()
        assertThat(snapshot.quality).isEqualTo(GnssSignalQuality.POOR)
    }

    @Test
    fun `an empty status is no signal but still receiving`() {
        val snapshot = gnssStatus().toSnapshot(timeToFirstFixMillis = null)

        assertThat(snapshot.satellitesVisible).isEqualTo(0)
        assertThat(snapshot.quality).isEqualTo(GnssSignalQuality.NONE)
        // A receiver reporting zero satellites is still switched on and looking.
        assertThat(snapshot.isReceiving).isTrue()
    }

    @Test
    fun `time to first fix is carried through`() {
        val snapshot =
            gnssStatus(satellite(cn0 = 40f, usedInFix = true))
                .toSnapshot(timeToFirstFixMillis = 8_300)

        assertThat(snapshot.timeToFirstFixMillis).isEqualTo(8_300)
    }

    private fun gnssStatus(vararg satellites: Satellite): GnssStatus {
        val builder = GnssStatus.Builder()
        satellites.forEachIndexed { index, satellite ->
            builder.addSatellite(
                GnssStatus.CONSTELLATION_GPS,
                FIRST_SVID + index,
                satellite.cn0,
                ELEVATION_DEGREES,
                AZIMUTH_DEGREES,
                true,
                true,
                satellite.usedInFix,
                false,
                0f,
                false,
                0f,
            )
        }
        return builder.build()
    }

    private class Satellite(
        val cn0: Float,
        val usedInFix: Boolean,
    )

    private fun satellite(
        cn0: Float,
        usedInFix: Boolean,
    ) = Satellite(cn0, usedInFix)

    private companion object {
        const val FIRST_SVID = 1
        const val ELEVATION_DEGREES = 45f
        const val AZIMUTH_DEGREES = 180f
    }
}
