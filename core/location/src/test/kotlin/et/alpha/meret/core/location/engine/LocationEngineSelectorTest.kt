package et.alpha.meret.core.location.engine

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.location.EngineCapabilities
import et.alpha.meret.core.location.EngineKind
import et.alpha.meret.core.location.LocationEngine
import et.alpha.meret.core.location.RawFix
import et.alpha.meret.core.model.TrackingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Test

class LocationEngineSelectorTest {
    @Test
    fun `prefers the fused engine when Play Services is usable`() {
        val selector = selector(playServicesUsable = true)

        assertThat(selector.selected).isEqualTo(EngineKind.FUSED)
    }

    @Test
    fun `falls back to the platform engine when Play Services is not usable`() {
        val selector = selector(playServicesUsable = false)

        assertThat(selector.selected).isEqualTo(EngineKind.PLATFORM)
    }

    /**
     * The fallback is silent — no dialog, no error. A field worker has no network to update Play
     * Services over and no permission to try, so telling them would be an obstacle rather than
     * information. It is [LocationEngineSelector.selected] that makes it visible, to the
     * supervisor who can act on it.
     */
    @Test
    fun `the unused engine is never constructed`() {
        var fusedBuilt = 0
        var platformBuilt = 0

        val selector =
            LocationEngineSelector(
                availability = { false },
                fusedEngine = {
                    fusedBuilt++
                    FakeEngine(EngineKind.FUSED)
                },
                platformEngine = {
                    platformBuilt++
                    FakeEngine(EngineKind.PLATFORM)
                },
            )
        selector.engine

        assertThat(fusedBuilt).isEqualTo(0)
        assertThat(platformBuilt).isEqualTo(1)
    }

    /**
     * Resolved once and cached. Switching engines mid-track would change the meaning of the data
     * halfway through — the two stamp different `provider` values on every point, so the track
     * could not say which one produced it.
     */
    @Test
    fun `the engine is resolved once and reused`() {
        var built = 0
        val selector =
            LocationEngineSelector(
                availability = { true },
                fusedEngine = {
                    built++
                    FakeEngine(EngineKind.FUSED)
                },
                platformEngine = { FakeEngine(EngineKind.PLATFORM) },
            )

        val first = selector.engine
        val second = selector.engine

        assertThat(built).isEqualTo(1)
        assertThat(first).isSameInstanceAs(second)
    }

    @Test
    fun `availability is only consulted once`() {
        var checks = 0
        val selector =
            LocationEngineSelector(
                availability = {
                    checks++
                    true
                },
                fusedEngine = { FakeEngine(EngineKind.FUSED) },
                platformEngine = { FakeEngine(EngineKind.PLATFORM) },
            )

        selector.engine
        selector.selected
        selector.engine

        assertThat(checks).isEqualTo(1)
    }

    private fun selector(playServicesUsable: Boolean) = LocationEngineSelector(
        availability = { playServicesUsable },
        fusedEngine = { FakeEngine(EngineKind.FUSED) },
        platformEngine = { FakeEngine(EngineKind.PLATFORM) },
    )

    private class FakeEngine(
        kind: EngineKind,
    ) : LocationEngine {
        override val fixes: Flow<RawFix> = emptyFlow()

        override val capabilities =
            EngineCapabilities(
                kind = kind,
                supportsBatching = kind == EngineKind.FUSED,
                supportsGnssStatus = true,
                reportsSpeed = true,
            )

        override suspend fun start(config: TrackingConfig) = Unit

        override suspend fun stop() = Unit

        override suspend fun lastKnown(): RawFix? = null
    }
}
