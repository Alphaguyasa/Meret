package et.alpha.meret.core.common.telemetry

/**
 * Crash and non-fatal reporting, behind an interface with a no-op default.
 *
 * The app must build and ship with no reporting SDK at all — NGO deployments frequently
 * cannot accept one, and a hard dependency on a vendor would make that a fork rather than
 * a configuration. Clients that want reporting bind their own implementation.
 *
 * Implementations must never throw and must never block the caller.
 *
 * ## No personal data, ever
 *
 * Records identify people and describe their health and farms. Callers must never put
 * names, phone numbers, coordinates, form answers, photo paths, or free text a user typed
 * into an event name, attribute, or exception message passed here. Counts, durations,
 * states, enum names, and versions are fine. When a throwable may carry user data in its
 * message, record its class name in an attribute instead of the throwable.
 *
 * This is also why the app has no logger: `android.util.Log` and friends are forbidden by
 * detekt, and `println`/`printStackTrace` by `NoLoggingInMainSourcesTest`.
 */
public interface Telemetry {
    /** A handled failure worth knowing about. Not shown to the user. */
    public fun recordNonFatal(
        throwable: Throwable,
        attributes: Map<String, String> = emptyMap(),
    )

    /** A named occurrence with no throwable — `tracking_gap`, `sync_conflict`, and such. */
    public fun recordEvent(
        name: String,
        attributes: Map<String, String> = emptyMap(),
    )

    /** A value attached to every subsequent report: active engine, device model, and so on. */
    public fun setAttribute(
        key: String,
        value: String,
    )
}

/** The default binding. Discards everything. */
public object NoOpTelemetry : Telemetry {
    override fun recordNonFatal(
        throwable: Throwable,
        attributes: Map<String, String>,
    ): Unit = Unit

    override fun recordEvent(
        name: String,
        attributes: Map<String, String>,
    ): Unit = Unit

    override fun setAttribute(
        key: String,
        value: String,
    ): Unit = Unit
}
