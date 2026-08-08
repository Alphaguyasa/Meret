package et.alpha.meret.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrackingConfigTest {
    @Test
    fun `the default config is valid and standard`() {
        val config = TrackingConfig()

        assertThat(config.profile).isEqualTo(LocationProfile.STANDARD)
        assertThat(config.isProfilePinned).isFalse()
        assertThat(config.autoPauseEnabled).isTrue()
        assertThat(config.allowMockLocations).isFalse()
    }

    // ------------------------------------------------------------------------- profiles

    @Test
    fun `profiles match the rates in LOCATION_ENGINE`() {
        assertThat(LocationProfile.PRECISE.intervalMillis).isEqualTo(1_000L)
        assertThat(LocationProfile.PRECISE.minDistanceMetres).isEqualTo(0.0)

        assertThat(LocationProfile.STANDARD.intervalMillis).isEqualTo(3_000L)
        assertThat(LocationProfile.STANDARD.minDistanceMetres).isEqualTo(5.0)

        assertThat(LocationProfile.ECONOMY.intervalMillis).isEqualTo(10_000L)
        assertThat(LocationProfile.ECONOMY.minDistanceMetres).isEqualTo(20.0)

        assertThat(LocationProfile.TRANSIT.intervalMillis).isEqualTo(15_000L)
        assertThat(LocationProfile.TRANSIT.minDistanceMetres).isEqualTo(50.0)
    }

    @Test
    fun `profile wire names are stable and distinct`() {
        val names = LocationProfile.entries.map { it.wireName }

        // These reach DataStore and, later, a synced project policy. Renaming one silently
        // resets a field worker's chosen profile to the default.
        assertThat(names).containsExactly("precise", "standard", "economy", "transit")
        assertThat(names.toSet()).hasSize(LocationProfile.entries.size)
    }

    @Test
    fun `interval rises and distance rises together across profiles`() {
        val ordered =
            listOf(
                LocationProfile.PRECISE,
                LocationProfile.STANDARD,
                LocationProfile.ECONOMY,
                LocationProfile.TRANSIT,
            )

        assertThat(ordered.map { it.intervalMillis }).isInStrictOrder()
        assertThat(ordered.map { it.minDistanceMetres }).isInStrictOrder()
    }

    /**
     * Batching is the single largest battery lever available, so a profile that flushes as often
     * as it samples has thrown the saving away.
     */
    @Test
    fun `every profile batches over several intervals`() {
        LocationProfile.entries.forEach { profile ->
            assertThat(profile.batchWindowMillis).isGreaterThan(profile.intervalMillis)
        }
    }

    // ----------------------------------------------------------------------- step gate

    @Test
    fun `the step gate uses the floor when accuracy is good`() {
        val config = TrackingConfig()

        // 4 m accuracy gives 2 m from the factor, so the 4 m floor wins.
        assertThat(config.stepGateMetres(4.0)).isEqualTo(4.0)
    }

    @Test
    fun `the step gate uses accuracy when it is poor`() {
        val config = TrackingConfig()

        assertThat(config.stepGateMetres(30.0)).isEqualTo(15.0)
    }

    @Test
    fun `the step gate handles a zero accuracy without going negative`() {
        assertThat(TrackingConfig().stepGateMetres(0.0)).isEqualTo(4.0)
    }

    // ---------------------------------------------------------------------- validation

    @Test
    fun `the configurable accuracy band from the doc is accepted at both ends`() {
        assertThat(TrackingConfig(maxAccuracyMetres = 10.0).maxAccuracyMetres).isEqualTo(10.0)
        assertThat(TrackingConfig(maxAccuracyMetres = 100.0).maxAccuracyMetres).isEqualTo(100.0)
    }

    @Test
    fun `an accuracy limit outside the band is rejected`() {
        assertThat(fails { TrackingConfig(maxAccuracyMetres = 9.9) }).isTrue()
        assertThat(fails { TrackingConfig(maxAccuracyMetres = 100.1) }).isTrue()
    }

    /**
     * The cross-field invariant that matters most. Equal thresholds make auto-pause chatter at
     * the boundary, flipping the notification between "Paused" and "Recording" on jitter alone.
     */
    @Test
    fun `a resume threshold not above the pause threshold is rejected`() {
        assertThat(
            fails {
                TrackingConfig(
                    autoPauseDisplacementMetres = 15.0,
                    autoResumeDisplacementMetres = 15.0,
                )
            },
        ).isTrue()

        assertThat(
            fails {
                TrackingConfig(
                    autoPauseDisplacementMetres = 20.0,
                    autoResumeDisplacementMetres = 15.0,
                )
            },
        ).isTrue()
    }

    @Test
    fun `non-positive rates and noises are rejected`() {
        assertThat(fails { TrackingConfig(maxImpliedSpeedMetresPerSecond = 0.0) }).isTrue()
        assertThat(fails { TrackingConfig(kalmanProcessNoiseSquareMetresPerSecond = 0.0) }).isTrue()
        assertThat(fails { TrackingConfig(segmentGapSeconds = 0.0) }).isTrue()
        assertThat(fails { TrackingConfig(simplifyEpsilonMetres = 0.0) }).isTrue()
        assertThat(fails { TrackingConfig(writeBatchSize = 0) }).isTrue()
    }

    @Test
    fun `negative step parameters are rejected`() {
        assertThat(fails { TrackingConfig(minStepMetres = -1.0) }).isTrue()
        assertThat(fails { TrackingConfig(stepAccuracyFactor = -0.1) }).isTrue()
    }

    /**
     * Zero is legitimate for both: `PRECISE` area measurement wants every step counted, and
     * turning the gate off entirely is a supervisor's call to make.
     */
    @Test
    fun `zero step parameters are allowed`() {
        val config = TrackingConfig(minStepMetres = 0.0, stepAccuracyFactor = 0.0)

        assertThat(config.stepGateMetres(25.0)).isEqualTo(0.0)
    }

    @Test
    fun `a single write batch is allowed even though it is a bad idea`() {
        // Never one transaction per fix in production, but the value is not incoherent, and the
        // place to argue about it is the settings screen rather than a crash.
        assertThat(TrackingConfig(writeBatchSize = 1).writeBatchSize).isEqualTo(1)
    }

    private fun fails(block: () -> TrackingConfig): Boolean =
        runCatching(block).exceptionOrNull() is IllegalArgumentException
}
