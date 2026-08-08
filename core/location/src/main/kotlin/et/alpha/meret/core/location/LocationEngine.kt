package et.alpha.meret.core.location

import et.alpha.meret.core.model.LocationProvider
import et.alpha.meret.core.model.TrackingConfig
import et.alpha.meret.core.model.geo.LatLng
import kotlinx.coroutines.flow.Flow

/**
 * One fix, exactly as the platform reported it.
 *
 * Deliberately not `android.location.Location`: that class is final, has no public
 * constructor worth using in a test, carries a mutable bundle of extras, and would drag
 * the Android framework into every filter-stage unit test. Engines convert at their own
 * boundary and everything downstream of them — the filter chain, the accumulator, the
 * pipeline — is pure Kotlin and runs on the JVM.
 *
 * [elapsedRealtimeNanos] is the ordering key. [recordedAtMillis] is wall clock, kept
 * because the server and the user both want it, and trusted for nothing else. See
 * `TimeProvider` for why.
 *
 * Accuracies are `Double` where the platform gives `Float`. The widening is free, and it
 * keeps every arithmetic site downstream in one precision rather than scattering
 * `toDouble()` through the geometry.
 */
public data class RawFix(
    public val position: LatLng,
    public val accuracyMetres: Double,
    public val elapsedRealtimeNanos: Long,
    public val recordedAtMillis: Long,
    public val provider: LocationProvider,
    public val altitudeMetres: Double? = null,
    public val verticalAccuracyMetres: Double? = null,
    public val bearingDegrees: Double? = null,
    public val speedMetresPerSecond: Double? = null,
    public val speedAccuracyMetresPerSecond: Double? = null,
    public val satellitesUsed: Int? = null,
    public val isMock: Boolean = false,
)

/**
 * Which engine is running and what it can actually do.
 *
 * Surfaced on the diagnostics screen. A supervisor looking at suspicious data needs to
 * know whether it came from fused Play Services, from bare `GPS_PROVIDER`, or from a
 * replayed fixture — the three produce visibly different tracks, and only one of them is
 * real field data.
 */
public data class EngineCapabilities(
    public val kind: EngineKind,
    /** False for [EngineKind.PLATFORM]: `LocationManager` has no batching equivalent. */
    public val supportsBatching: Boolean,
    /** False for [EngineKind.REPLAY], which has no sky to look at. */
    public val supportsGnssStatus: Boolean,
    /** True when the platform reports per-fix speed rather than leaving us to derive it. */
    public val reportsSpeed: Boolean,
)

public enum class EngineKind {
    /** `FusedLocationProviderClient`. Play Services present and current. */
    FUSED,

    /** `LocationManager` + `GPS_PROVIDER`. No usable Play Services. */
    PLATFORM,

    /** A GPX fixture. Test and demo only — never field data. */
    REPLAY,
}

/**
 * A source of location fixes.
 *
 * [fixes] is hot and independent of [start]/[stop]: collecting it before starting is
 * legal and yields nothing, and a collector that arrives mid-track sees fixes from that
 * moment on rather than a replay of the track so far. Persistence is the pipeline's job,
 * not the flow's.
 *
 * Implementations emit **raw** fixes only. No filtering, no rejection, no smoothing —
 * invariant 2 in `CLAUDE.md` requires that raw fixes reach storage untouched, and an
 * engine that quietly dropped a bad fix would make that impossible to honour.
 */
public interface LocationEngine {
    public val fixes: Flow<RawFix>

    public val capabilities: EngineCapabilities

    /**
     * Begins delivering fixes. Idempotent in effect: calling it while already started
     * re-applies [config], which is how a profile switch takes effect.
     *
     * Throws [SecurityException] if location permission has been revoked — the caller is
     * the permission gate's owner, and swallowing it here would turn a fixable permission
     * problem into a silently empty track.
     */
    public suspend fun start(config: TrackingConfig)

    public suspend fun stop()

    /**
     * The platform's cached fix, if any. Used to centre the map before the first live fix
     * arrives. May be minutes or hours old; never write it into a track.
     */
    public suspend fun lastKnown(): RawFix?
}
