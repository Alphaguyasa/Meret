package et.alpha.meret.domain.diagnostics

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.domain.onboarding.PermissionSnapshot
import org.junit.Test

class DiagnosticsReportBuilderTest {
    private val builder = DiagnosticsReportBuilder()

    @Test
    fun `a recording phone with one open gap produces the expected report`() {
        val report = builder.build(snapshot())

        assertThat(report).isEqualTo(
            """
            Meret diagnostics
            Generated (UTC): 2026-01-01 00:00:00
            App: 0.1.0 (3)
            Device: TECNO KI5q, API 34
            Engine: PLATFORM

            Permissions
            Precise location: granted
            Approximate location: granted
            Location blocked: no
            Notifications: granted
            Background location: denied
            Battery optimisation ignored: no
            OEM autostart step: needed

            Tracking
            State: recording
            Sampling interval: 5 s
            Fix rate (10 min): 90 of 120 expected (75%)
            Fixes this session: 412
            Last fix: 3 s ago
            Accuracy: 7.5 m
            Satellites used: 11
            Gaps logged: 2
              - 1 min 30 s long, began 2 min 0 s ago, still open
              - 45 s long, began 1 h 5 min ago

            Sync
            Outbox depth: n/a
            Oldest pending: n/a
            Last sync attempt: n/a
            Last sync error: none

            Storage
            Tile packs: n/a
            Free storage: 1536 MB

            """.trimIndent(),
        )
    }

    @Test
    fun `an idle phone with no recording yet reads n-a, not zero`() {
        val idle =
            snapshot().copy(
                trackingStatus = TrackingStatus.IDLE,
                health = TrackingHealth(),
                outboxDepth = 4,
                lastSyncError = "Timeout",
            )

        val report = builder.build(idle)

        assertThat(report).contains("State: idle\n")
        assertThat(report).contains("Sampling interval: n/a\n")
        assertThat(report).contains("Fix rate (10 min): n/a\n")
        assertThat(report).contains("Last fix: n/a\n")
        assertThat(report).contains("Gaps logged: 0\n")
        assertThat(report).contains("Outbox depth: 4\n")
        assertThat(report).contains("Last sync error: Timeout\n")
    }

    @Test
    fun `permissions that do not exist on old Android are left out`() {
        val oreo = snapshot().copy(permissions = permissions().copy(sdkInt = 26))

        val report = builder.build(oreo)

        assertThat(report).doesNotContain("Notifications")
        assertThat(report).doesNotContain("Background location")
        assertThat(report).contains("API 26")
    }

    /**
     * The report is pasted into WhatsApp. No coordinate may appear in any form, even though
     * the phone has a fix at a known position: the snapshot type has nowhere to put one, and
     * this pins that nothing that looks like a latitude or longitude is printed either.
     */
    @Test
    fun `the report holds no coordinates`() {
        val report = builder.build(snapshot())

        assertThat(report).doesNotContain(FIX_LATITUDE.toString())
        assertThat(report).doesNotContain(FIX_LONGITUDE.toString())
        assertThat(report).doesNotContainMatch("""\d{1,3}\.\d{4,}""")
    }

    private fun snapshot() = DiagnosticsSnapshot(
        generatedAtMillis = 1_767_225_600_000L,
        nowElapsedNanos = NOW_NANOS,
        appVersionName = "0.1.0",
        appVersionCode = 3,
        deviceManufacturer = "TECNO",
        deviceModel = "KI5q",
        engine = "PLATFORM",
        permissions = permissions(),
        trackingStatus = TrackingStatus.RECORDING,
        health =
            TrackingHealth(
                isRecording = true,
                samplingIntervalMillis = 5_000,
                windowExpected = 120,
                windowActual = 90,
                fixCount = 412,
                lastFixElapsedNanos = NOW_NANOS - 3 * NANOS_PER_SECOND,
                lastAccuracyMetres = 7.5,
                lastSatellitesUsed = 11,
                gaps =
                    listOf(
                        GapRecord(NOW_NANOS - 3_900 * NANOS_PER_SECOND, 45_000, isOpen = false),
                        GapRecord(NOW_NANOS - 120 * NANOS_PER_SECOND, 90_000, isOpen = true),
                    ),
            ),
        freeStorageBytes = 1_536L * 1_048_576L,
    )

    private fun permissions() = PermissionSnapshot(
        sdkInt = 34,
        fineLocation = true,
        coarseLocation = true,
        notifications = true,
        backgroundLocation = false,
        ignoringBatteryOptimisations = false,
        oemStepRequired = true,
    )

    private companion object {
        /** Where the fixture phone "is". Must never reach the report. */
        const val FIX_LATITUDE = 9.0300512
        const val FIX_LONGITUDE = 38.7400731

        const val NOW_NANOS = 90_000_000_000_000L
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
