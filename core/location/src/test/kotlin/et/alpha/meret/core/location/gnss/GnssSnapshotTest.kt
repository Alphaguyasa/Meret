package et.alpha.meret.core.location.gnss

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GnssSnapshotTest {
    @Test
    fun `no satellites used in a fix is no signal`() {
        assertThat(quality(used = 0, cn0 = null)).isEqualTo(GnssSignalQuality.NONE)
    }

    /**
     * A fix needs four satellites for three dimensions plus the receiver clock, so anything below
     * that is not a 3D fix at all however strong the signals are.
     */
    @Test
    fun `fewer than four satellites is poor whatever the signal strength`() {
        assertThat(quality(used = 3, cn0 = 45.0)).isEqualTo(GnssSignalQuality.POOR)
    }

    @Test
    fun `four strong satellites is a bare 3D fix`() {
        assertThat(quality(used = 4, cn0 = 40.0)).isEqualTo(GnssSignalQuality.FAIR)
    }

    @Test
    fun `seven strong satellites is good`() {
        assertThat(quality(used = 7, cn0 = 40.0)).isEqualTo(GnssSignalQuality.GOOD)
    }

    @Test
    fun `ten strong satellites is excellent`() {
        assertThat(quality(used = 12, cn0 = 42.0)).isEqualTo(GnssSignalQuality.EXCELLENT)
    }

    /**
     * The canopy case, and the reason satellite count alone is not enough. Twelve satellites all
     * near the noise floor still produce a wandering fix, and reporting that as EXCELLENT would
     * tell a field worker to trust a track they should not.
     */
    @Test
    fun `weak carrier to noise downgrades an otherwise excellent reading`() {
        assertThat(quality(used = 12, cn0 = 18.0)).isEqualTo(GnssSignalQuality.GOOD)
    }

    @Test
    fun `the downgrade cannot go below none`() {
        assertThat(quality(used = 0, cn0 = 5.0)).isEqualTo(GnssSignalQuality.NONE)
    }

    @Test
    fun `an unknown carrier to noise does not downgrade`() {
        assertThat(quality(used = 12, cn0 = null)).isEqualTo(GnssSignalQuality.EXCELLENT)
    }

    @Test
    fun `carrier to noise exactly at the threshold does not downgrade`() {
        assertThat(quality(used = 7, cn0 = GnssSnapshot.WEAK_CN0_DB_HZ))
            .isEqualTo(GnssSignalQuality.GOOD)
    }

    @Test
    fun `a snapshot carries the raw counts through`() {
        val snapshot =
            GnssSnapshot.of(
                satellitesVisible = 22,
                satellitesUsedInFix = 9,
                meanCarrierToNoiseDbHz = 33.5,
                isReceiving = true,
                timeToFirstFixMillis = 4_200,
            )

        assertThat(snapshot.satellitesVisible).isEqualTo(22)
        assertThat(snapshot.satellitesUsedInFix).isEqualTo(9)
        assertThat(snapshot.meanCarrierToNoiseDbHz).isEqualTo(33.5)
        assertThat(snapshot.isReceiving).isTrue()
        assertThat(snapshot.timeToFirstFixMillis).isEqualTo(4_200)
    }

    /**
     * The default is what the map chip shows before the receiver has said anything, so it must
     * read as "nothing yet" rather than as a real reading of zero satellites.
     */
    @Test
    fun `the default snapshot reports nothing and is not receiving`() {
        val snapshot = GnssSnapshot()

        assertThat(snapshot.quality).isEqualTo(GnssSignalQuality.NONE)
        assertThat(snapshot.isReceiving).isFalse()
        assertThat(snapshot.satellitesVisible).isEqualTo(0)
        assertThat(snapshot.meanCarrierToNoiseDbHz).isNull()
        assertThat(snapshot.timeToFirstFixMillis).isNull()
    }

    private fun quality(
        used: Int,
        cn0: Double?,
    ): GnssSignalQuality = GnssSnapshot
        .of(
            satellitesVisible = maxOf(used, used + 4),
            satellitesUsedInFix = used,
            meanCarrierToNoiseDbHz = cn0,
            isReceiving = true,
        ).quality
}
