package et.alpha.meret.core.model

/**
 * A request-rate preset.
 *
 * The four profiles from `LOCATION_ENGINE.md`. Interval and minimum distance live on the
 * profile rather than being free-floating numbers in [TrackingConfig], because the pair is
 * only meaningful together: a 15 s interval with a 0 m distance filter is a battery bug,
 * and a 1 s interval with a 50 m filter throws away the precision it just paid for.
 *
 * The engine may switch profiles on speed and battery, but only while
 * [TrackingConfig.isProfilePinned] is false. A silent rate change the user did not ask for
 * and cannot see makes survey data unfalsifiable — if a track is sparse, the field worker
 * has to be able to tell "the app throttled" from "the sky was bad".
 */
public enum class LocationProfile(
    override val wireName: String,
    public val intervalMillis: Long,
    public val minDistanceMetres: Double,
) : WireNamed {
    /** Area measurement and walking survey. Every fix, no distance gate. */
    PRECISE("precise", PRECISE_INTERVAL_MS, 0.0),

    /** Default field work. */
    STANDARD("standard", STANDARD_INTERVAL_MS, STANDARD_MIN_DISTANCE_M),

    /** Battery below [TrackingConfig.economyBatteryPercent], or a long transit leg. */
    ECONOMY("economy", ECONOMY_INTERVAL_MS, ECONOMY_MIN_DISTANCE_M),

    /** Sustained speed above [TrackingConfig.transitSpeedMetresPerSecond]. */
    TRANSIT("transit", TRANSIT_INTERVAL_MS, TRANSIT_MIN_DISTANCE_M),
    ;

    /**
     * The batching window handed to the platform. Batching is the single largest battery
     * lever available, so it is derived from the interval rather than configured
     * separately — a profile that samples every 10 s and flushes every 10 s has thrown the
     * saving away.
     */
    public val batchWindowMillis: Long
        get() = intervalMillis * BATCH_WINDOW_INTERVALS
}

// Profile constants are file-private rather than nested in a companion: an enum entry's
// constructor arguments are evaluated before the enum's own companion is initialised, and
// while `const val` happens to be inlined and therefore safe, relying on that reads as an
// accident waiting to be broken by someone dropping the `const`.
private const val PRECISE_INTERVAL_MS = 1_000L
private const val STANDARD_INTERVAL_MS = 3_000L
private const val ECONOMY_INTERVAL_MS = 10_000L
private const val TRANSIT_INTERVAL_MS = 15_000L

private const val STANDARD_MIN_DISTANCE_M = 5.0
private const val ECONOMY_MIN_DISTANCE_M = 20.0
private const val TRANSIT_MIN_DISTANCE_M = 50.0

/** Flush at most this many sampling intervals' worth of fixes at once. */
private const val BATCH_WINDOW_INTERVALS = 5

/**
 * Everything tunable about how a fix is requested, judged, smoothed and counted.
 *
 * One flat object rather than a nest of per-stage configs. Every value here is something a
 * supervisor may need to change for a specific survey — a forestry plot walked under
 * canopy needs a looser accuracy gate than a road survey — and a flat object is what a
 * settings screen and a `DataStore` both want.
 *
 * Persisted by `:core:datastore`. This module stays pure Kotlin, so the type is safe to
 * use from the engine, the filter stages and their JVM unit tests without an Android
 * dependency anywhere.
 */
public data class TrackingConfig(
    /** The preset in force. Always the rate actually being requested. */
    public val profile: LocationProfile = LocationProfile.STANDARD,
    /** True when the user chose the profile by hand. Blocks automatic switching. */
    public val isProfilePinned: Boolean = false,
    /**
     * Validity gate: a fix reporting worse horizontal accuracy than this is rejected.
     * `LOCATION_ENGINE.md` fixes the adjustable band at 10–100 m; enforced in [init].
     */
    public val maxAccuracyMetres: Double = DEFAULT_MAX_ACCURACY_M,
    /** Validity gate: implied speed above this means the previous or current fix is wrong. */
    public val maxImpliedSpeedMetresPerSecond: Double = DEFAULT_MAX_IMPLIED_SPEED_MPS,
    /** When false, a fix flagged as mock is rejected. Per-project policy. */
    public val allowMockLocations: Boolean = false,
    /** Kalman process noise, m²/s. See `KalmanLocationFilter.DEFAULT_PROCESS_NOISE`. */
    public val kalmanProcessNoiseSquareMetresPerSecond: Double = DEFAULT_PROCESS_NOISE,
    /** A gap longer than this starts a new segment and resets the filter. */
    public val segmentGapSeconds: Double = DEFAULT_SEGMENT_GAP_S,
    /** Distance accumulates only on steps longer than this, whatever the accuracy says. */
    public val minStepMetres: Double = DEFAULT_MIN_STEP_M,
    /**
     * The other half of the step gate: a step must also exceed
     * `accuracy * stepAccuracyFactor`. A 20 m-accuracy fix that moved 5 m has not
     * demonstrably moved at all.
     */
    public val stepAccuracyFactor: Double = DEFAULT_STEP_ACCURACY_FACTOR,
    /**
     * Auto-pause. Off is a legitimate choice, not a degraded one: a stationary
     * observation point is survey activity, and pausing through it loses the fact that
     * the enumerator was there for forty minutes.
     */
    public val autoPauseEnabled: Boolean = true,
    /** Window over which auto-pause measures displacement. */
    public val autoPauseWindowMillis: Long = DEFAULT_AUTO_PAUSE_WINDOW_MS,
    /** Displacement below this over the window, with speed below the threshold, pauses. */
    public val autoPauseDisplacementMetres: Double = DEFAULT_AUTO_PAUSE_DISPLACEMENT_M,
    public val autoPauseSpeedMetresPerSecond: Double = DEFAULT_AUTO_PAUSE_SPEED_MPS,
    /** Displacement from the pause anchor that resumes. Deliberately above the pause
     *  threshold: equal values would chatter around the boundary. */
    public val autoResumeDisplacementMetres: Double = DEFAULT_AUTO_RESUME_DISPLACEMENT_M,
    /** Douglas–Peucker tolerance for `simplified_geojson`. Never applied on the fix path. */
    public val simplifyEpsilonMetres: Double = DEFAULT_SIMPLIFY_EPSILON_M,
    /** Fixes buffered before a Room write. Never one transaction per fix. */
    public val writeBatchSize: Int = DEFAULT_WRITE_BATCH_SIZE,
    /** Flush a partial batch after this long, so a slow walk still persists promptly. */
    public val writeBatchWindowMillis: Long = DEFAULT_WRITE_BATCH_WINDOW_MS,
    /** Below this battery percentage the engine drops to [LocationProfile.ECONOMY]. */
    public val economyBatteryPercent: Int = DEFAULT_ECONOMY_BATTERY_PERCENT,
    /** Sustained speed above this switches to [LocationProfile.TRANSIT]. */
    public val transitSpeedMetresPerSecond: Double = DEFAULT_TRANSIT_SPEED_MPS,
) {
    init {
        require(maxAccuracyMetres in MIN_MAX_ACCURACY_M..MAX_MAX_ACCURACY_M) {
            "maxAccuracyMetres must be in $MIN_MAX_ACCURACY_M..$MAX_MAX_ACCURACY_M, " +
                "was $maxAccuracyMetres"
        }
        require(maxImpliedSpeedMetresPerSecond > 0.0) {
            "maxImpliedSpeedMetresPerSecond must be positive, was $maxImpliedSpeedMetresPerSecond"
        }
        require(kalmanProcessNoiseSquareMetresPerSecond > 0.0) {
            "kalman process noise must be positive, " +
                "was $kalmanProcessNoiseSquareMetresPerSecond"
        }
        require(segmentGapSeconds > 0.0) {
            "segmentGapSeconds must be positive, was $segmentGapSeconds"
        }
        require(minStepMetres >= 0.0) { "minStepMetres must not be negative, was $minStepMetres" }
        require(stepAccuracyFactor >= 0.0) {
            "stepAccuracyFactor must not be negative, was $stepAccuracyFactor"
        }
        require(autoResumeDisplacementMetres > autoPauseDisplacementMetres) {
            "autoResumeDisplacementMetres ($autoResumeDisplacementMetres) must exceed " +
                "autoPauseDisplacementMetres ($autoPauseDisplacementMetres), or auto-pause " +
                "will chatter at the boundary"
        }
        require(writeBatchSize >= 1) { "writeBatchSize must be at least 1, was $writeBatchSize" }
        require(simplifyEpsilonMetres > 0.0) {
            "simplifyEpsilonMetres must be positive, was $simplifyEpsilonMetres"
        }
    }

    /** The minimum step, in metres, that counts as movement for a fix of this accuracy. */
    public fun stepGateMetres(accuracyMetres: Double): Double =
        maxOf(minStepMetres, accuracyMetres * stepAccuracyFactor)

    public companion object {
        public const val DEFAULT_MAX_ACCURACY_M: Double = 25.0
        public const val MIN_MAX_ACCURACY_M: Double = 10.0
        public const val MAX_MAX_ACCURACY_M: Double = 100.0

        /** 60 m/s is 216 km/h — beyond any vehicle a field team travels in. */
        public const val DEFAULT_MAX_IMPLIED_SPEED_MPS: Double = 60.0

        public const val DEFAULT_PROCESS_NOISE: Double = 3.0
        public const val DEFAULT_SEGMENT_GAP_S: Double = 60.0

        public const val DEFAULT_MIN_STEP_M: Double = 4.0
        public const val DEFAULT_STEP_ACCURACY_FACTOR: Double = 0.5

        public const val DEFAULT_AUTO_PAUSE_WINDOW_MS: Long = 60_000L
        public const val DEFAULT_AUTO_PAUSE_DISPLACEMENT_M: Double = 15.0
        public const val DEFAULT_AUTO_PAUSE_SPEED_MPS: Double = 0.5
        public const val DEFAULT_AUTO_RESUME_DISPLACEMENT_M: Double = 20.0

        public const val DEFAULT_SIMPLIFY_EPSILON_M: Double = 3.0

        public const val DEFAULT_WRITE_BATCH_SIZE: Int = 20
        public const val DEFAULT_WRITE_BATCH_WINDOW_MS: Long = 10_000L

        public const val DEFAULT_ECONOMY_BATTERY_PERCENT: Int = 20
        public const val DEFAULT_TRANSIT_SPEED_MPS: Double = 8.0
    }
}
