package et.alpha.meret.domain.diagnostics

import et.alpha.meret.domain.onboarding.PermissionSnapshot
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The tracking subsystem's state, without the track id or anything else that names a record. */
public enum class TrackingStatus {
    IDLE,
    RECORDING,
    PAUSED,
}

/**
 * Everything the diagnostics screen shows (LOCATION_ENGINE.md §Diagnostics screen), as one
 * value the screen and the "copy diagnostics" report are both built from.
 *
 * **No personal data.** No coordinates, no names, no form answers, no track or user ids:
 * the report is pasted into WhatsApp and Telegram. Counts, states and versions only. A
 * field that cannot be known yet (the outbox before Phase 9, tile packs before Phase 6) is
 * null and reads "n/a".
 */
public data class DiagnosticsSnapshot(
    /** Wall clock when the report was taken. For the reader; the device clock may be wrong. */
    public val generatedAtMillis: Long,
    /** `elapsedRealtimeNanos` at the same moment, to age the last fix and the gaps. */
    public val nowElapsedNanos: Long,
    public val appVersionName: String,
    public val appVersionCode: Long,
    public val deviceManufacturer: String,
    public val deviceModel: String,
    /** `EngineKind` name: FUSED, PLATFORM or REPLAY. */
    public val engine: String,
    public val permissions: PermissionSnapshot,
    public val trackingStatus: TrackingStatus,
    public val health: TrackingHealth,
    public val freeStorageBytes: Long,
    public val outboxDepth: Int? = null,
    public val oldestPendingAgeMillis: Long? = null,
    public val lastSyncAttemptMillis: Long? = null,
    /** An error class or code, never a server message that may echo a record back. */
    public val lastSyncError: String? = null,
    public val tilePackCount: Int? = null,
)

/**
 * Builds the plain-text report behind "copy diagnostics".
 *
 * English and fixed-format on purpose: it is read by whoever supports the app, pasted from
 * any phone language, and should look the same from every device so two reports can be
 * compared line by line. The screen itself is localised.
 */
public class DiagnosticsReportBuilder {
    public fun build(snapshot: DiagnosticsSnapshot): String = buildString {
        with(snapshot) {
            appendLine("Meret diagnostics")
            line("Generated (UTC)", TIMESTAMP.format(Instant.ofEpochMilli(generatedAtMillis)))
            line("App", "$appVersionName ($appVersionCode)")
            line("Device", "$deviceManufacturer $deviceModel, API ${permissions.sdkInt}")
            line("Engine", engine)
            appendLine()
            appendLine("Permissions")
            line("Precise location", permissions.fineLocation.grant())
            line("Approximate location", permissions.coarseLocation.grant())
            line("Location blocked", permissions.locationPermanentlyDenied.yesNo())
            if (permissions.sdkInt >= NOTIFICATIONS_SDK) {
                line("Notifications", permissions.notifications.grant())
            }
            if (permissions.sdkInt >= BACKGROUND_LOCATION_SDK) {
                line("Background location", permissions.backgroundLocation.grant())
            }
            line("Battery optimisation ignored", permissions.ignoringBatteryOptimisations.yesNo())
            line("OEM autostart step", if (permissions.oemStepRequired) "needed" else "not needed")
            appendLine()
            appendTracking(snapshot)
            appendLine()
            appendLine("Sync")
            line("Outbox depth", outboxDepth.orNa())
            line("Oldest pending", oldestPendingAgeMillis?.let(::duration).orNa())
            line(
                "Last sync attempt",
                lastSyncAttemptMillis
                    ?.let { TIMESTAMP.format(Instant.ofEpochMilli(it)) }
                    .orNa(),
            )
            line("Last sync error", lastSyncError ?: "none")
            appendLine()
            appendLine("Storage")
            line("Tile packs", tilePackCount.orNa())
            line("Free storage", megabytes(freeStorageBytes))
        }
    }.trimEnd() + "\n"

    private fun StringBuilder.appendTracking(snapshot: DiagnosticsSnapshot) {
        val health = snapshot.health
        appendLine("Tracking")
        line("State", snapshot.trackingStatus.name.lowercase(Locale.ROOT))
        line("Sampling interval", health.samplingIntervalMillis?.let(::duration).orNa())
        line("Fix rate (10 min)", fixRate(health))
        line("Fixes this session", health.fixCount.toString())
        line(
            "Last fix",
            health.lastFixElapsedNanos
                ?.let { duration((snapshot.nowElapsedNanos - it) / NANOS_PER_MILLI) + " ago" }
                .orNa(),
        )
        line("Accuracy", health.lastAccuracyMetres?.let { "%.1f m".format(Locale.ROOT, it) }.orNa())
        line("Satellites used", health.lastSatellitesUsed.orNa())
        line("Gaps logged", health.gaps.size.toString())
        health.gaps.asReversed().forEach { gap ->
            val ago = duration((snapshot.nowElapsedNanos - gap.startElapsedNanos) / NANOS_PER_MILLI)
            val state = if (gap.isOpen) ", still open" else ""
            appendLine("  - ${duration(gap.durationMillis)} long, began $ago ago$state")
        }
    }

    private fun fixRate(health: TrackingHealth): String {
        val ratio = health.windowRatio ?: return "n/a"
        val percent = (ratio * PERCENT).toInt()
        return "${health.windowActual} of ${health.windowExpected} expected ($percent%)"
    }

    private fun StringBuilder.line(
        label: String,
        value: String,
    ) {
        appendLine("$label: $value")
    }

    private fun Boolean.grant() = if (this) "granted" else "denied"

    private fun Boolean.yesNo() = if (this) "yes" else "no"

    private fun Any?.orNa() = this?.toString() ?: "n/a"

    private fun megabytes(bytes: Long) = "${bytes / BYTES_PER_MEGABYTE} MB"

    /** `45 s`, `12 min 5 s`, `3 h 20 min`. Negative (a clock oddity) reads as zero. */
    private fun duration(millis: Long): String {
        val seconds = millis.coerceAtLeast(0L) / MILLIS_PER_SECOND
        val minutes = seconds / SECONDS_PER_MINUTE
        val hours = minutes / MINUTES_PER_HOUR
        return when {
            hours > 0 -> "$hours h ${minutes % MINUTES_PER_HOUR} min"
            minutes > 0 -> "$minutes min ${seconds % SECONDS_PER_MINUTE} s"
            else -> "$seconds s"
        }
    }

    private companion object {
        val TIMESTAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(ZoneOffset.UTC)

        const val NOTIFICATIONS_SDK = 33
        const val BACKGROUND_LOCATION_SDK = 29
        const val NANOS_PER_MILLI = 1_000_000L
        const val MILLIS_PER_SECOND = 1_000L
        const val SECONDS_PER_MINUTE = 60L
        const val MINUTES_PER_HOUR = 60L
        const val PERCENT = 100
        const val BYTES_PER_MEGABYTE = 1_048_576L
    }
}
