package et.alpha.meret.core.common.telemetry

/**
 * Crash and non-fatal reporting, behind an interface with a no-op default.
 *
 * The app must build and ship with no reporting SDK at all — NGO deployments frequently
 * cannot accept one, and a hard dependency on a vendor would make that a fork rather than
 * a configuration. Clients that want reporting bind their own implementation.
 *
 * Implementations must never throw and must never block the caller.
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
