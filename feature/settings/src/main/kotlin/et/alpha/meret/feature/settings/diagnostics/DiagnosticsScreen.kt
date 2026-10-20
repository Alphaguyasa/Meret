package et.alpha.meret.feature.settings.diagnostics

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import et.alpha.meret.core.designsystem.theme.MeretTheme
import et.alpha.meret.core.designsystem.theme.Sizes
import et.alpha.meret.core.designsystem.theme.Spacing
import et.alpha.meret.domain.diagnostics.DiagnosticsSnapshot
import et.alpha.meret.domain.diagnostics.GapRecord
import et.alpha.meret.domain.diagnostics.TrackingHealth
import et.alpha.meret.domain.diagnostics.TrackingStatus
import et.alpha.meret.domain.onboarding.PermissionSnapshot
import et.alpha.meret.feature.settings.R
import kotlin.math.roundToInt

/**
 * The diagnostics screen: what a supervisor needs to see on a worker's phone, in the
 * phone's language, with one button that copies the English report for WhatsApp or
 * Telegram. Stateless; [DiagnosticsRoute] owns the ViewModel and the clipboard.
 */
@Composable
fun DiagnosticsScreen(
    state: DiagnosticsUiState,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state) {
            // One read of a few system services; a spinner would only flash.
            DiagnosticsUiState.Loading -> {
                Box(Modifier.fillMaxSize())
            }

            is DiagnosticsUiState.Ready -> {
                Column(Modifier.fillMaxSize().padding(Spacing.lg)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Text(
                            text = stringResource(R.string.diagnostics_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = Spacing.md),
                        )
                        Facts(state.snapshot)
                    }
                    // Within thumb reach, below the scrolling facts.
                    Button(
                        onClick = { onCopy(state.report) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = Spacing.md)
                                .heightIn(min = Sizes.touchTargetField),
                    ) {
                        Text(stringResource(R.string.diagnostics_copy))
                    }
                }
            }
        }
    }
}

@Composable
private fun Facts(snapshot: DiagnosticsSnapshot) {
    val permissions = snapshot.permissions
    val notAvailable = stringResource(R.string.diagnostics_not_available)

    Section(R.string.diagnostics_section_phone) {
        Fact(
            R.string.diagnostics_app_version,
            "${snapshot.appVersionName} (${snapshot.appVersionCode})",
        )
        Fact(R.string.diagnostics_phone, "${snapshot.deviceManufacturer} ${snapshot.deviceModel}")
        Fact(R.string.diagnostics_android_level, permissions.sdkInt.toString())
        Fact(R.string.diagnostics_engine, stringResource(engineLabel(snapshot.engine)))
    }
    Section(R.string.diagnostics_section_permissions) {
        Fact(R.string.diagnostics_precise_location, allowed(permissions.fineLocation))
        if (permissions.sdkInt >= BACKGROUND_LOCATION_SDK) {
            Fact(R.string.diagnostics_background_location, allowed(permissions.backgroundLocation))
        }
        if (permissions.sdkInt >= NOTIFICATIONS_SDK) {
            Fact(R.string.diagnostics_notifications, allowed(permissions.notifications))
        }
        Fact(
            R.string.diagnostics_battery,
            stringResource(
                if (permissions.ignoringBatteryOptimisations) {
                    R.string.diagnostics_battery_off
                } else {
                    R.string.diagnostics_battery_on
                },
            ),
        )
    }
    Section(R.string.diagnostics_section_tracking) {
        TrackingFacts(snapshot, notAvailable)
    }
    Section(R.string.diagnostics_section_sync) {
        Fact(R.string.diagnostics_outbox, snapshot.outboxDepth?.toString() ?: notAvailable)
        // Phase 9 fills the sync fields; until then there is nothing to format.
        Fact(R.string.diagnostics_last_sync, notAvailable)
    }
    Section(R.string.diagnostics_section_storage) {
        Fact(R.string.diagnostics_tile_packs, snapshot.tilePackCount?.toString() ?: notAvailable)
        Fact(
            R.string.diagnostics_free_storage,
            stringResource(
                R.string.diagnostics_megabytes,
                snapshot.freeStorageBytes / BYTES_PER_MB,
            ),
        )
    }
}

@Composable
private fun TrackingFacts(
    snapshot: DiagnosticsSnapshot,
    notAvailable: String,
) {
    val health = snapshot.health
    val now = snapshot.nowElapsedNanos
    Fact(R.string.diagnostics_state, stringResource(stateLabel(snapshot.trackingStatus)))
    Fact(
        R.string.diagnostics_fix_rate,
        health.windowRatio?.let { ratio ->
            stringResource(
                R.string.diagnostics_fix_rate_value,
                health.windowActual,
                health.windowExpected,
                (ratio * PERCENT).toInt(),
            )
        } ?: notAvailable,
    )
    Fact(
        R.string.diagnostics_accuracy,
        health.lastAccuracyMetres?.let {
            stringResource(R.string.diagnostics_accuracy_value, it.roundToInt())
        } ?: notAvailable,
    )
    Fact(R.string.diagnostics_satellites, health.lastSatellitesUsed?.toString() ?: notAvailable)
    Fact(
        R.string.diagnostics_last_fix,
        health.lastFixElapsedNanos?.let {
            stringResource(R.string.diagnostics_ago, duration(nanosToMillis(now - it)))
        } ?: notAvailable,
    )
    Fact(
        R.string.diagnostics_gaps,
        if (health.gaps.isEmpty()) {
            stringResource(
                R.string.diagnostics_none,
            )
        } else {
            "${health.gaps.size}"
        },
    )
    health.gaps.asReversed().forEach { gap -> GapLine(gap, now) }
}

@Composable
private fun GapLine(
    gap: GapRecord,
    nowElapsedNanos: Long,
) {
    val length = duration(gap.durationMillis)
    val text =
        if (gap.isOpen) {
            stringResource(R.string.diagnostics_gap_open, length)
        } else {
            val ago = duration(nanosToMillis(nowElapsedNanos - gap.startElapsedNanos))
            stringResource(R.string.diagnostics_gap_closed, length, ago)
        }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.md, top = Spacing.xs),
    )
}

@Composable
private fun Section(
    @StringRes title: Int,
    content: @Composable () -> Unit,
) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xs),
    )
    HorizontalDivider()
    content()
}

/** Label left, value right; long Amharic labels wrap rather than push the value off screen. */
@Composable
private fun Fact(
    @StringRes label: Int,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun allowed(granted: Boolean): String =
    stringResource(if (granted) R.string.diagnostics_allowed else R.string.diagnostics_not_allowed)

/** `45 s`, `12 min 5 s`, `3 h 20 min`, in the phone's language. */
@Composable
private fun duration(millis: Long): String {
    val seconds = millis.coerceAtLeast(0L) / MILLIS_PER_SECOND
    val minutes = seconds / SECONDS_PER_MINUTE
    val hours = minutes / MINUTES_PER_HOUR
    return when {
        hours > 0 -> {
            stringResource(
                R.string.diagnostics_duration_hours,
                hours,
                minutes % MINUTES_PER_HOUR,
            )
        }

        minutes > 0 -> {
            stringResource(
                R.string.diagnostics_duration_minutes,
                minutes,
                seconds % SECONDS_PER_MINUTE,
            )
        }

        else -> {
            stringResource(R.string.diagnostics_duration_seconds, seconds)
        }
    }
}

@StringRes
internal fun engineLabel(engine: String): Int = when (engine) {
    "FUSED" -> R.string.diagnostics_engine_fused
    "REPLAY" -> R.string.diagnostics_engine_replay
    else -> R.string.diagnostics_engine_platform
}

@StringRes
internal fun stateLabel(status: TrackingStatus): Int = when (status) {
    TrackingStatus.IDLE -> R.string.diagnostics_state_idle
    TrackingStatus.RECORDING -> R.string.diagnostics_state_recording
    TrackingStatus.PAUSED -> R.string.diagnostics_state_paused
}

private fun nanosToMillis(nanos: Long): Long = nanos / NANOS_PER_MILLI

private const val NOTIFICATIONS_SDK = 33
private const val BACKGROUND_LOCATION_SDK = 29
private const val PERCENT = 100
private const val BYTES_PER_MB = 1_048_576L
private const val NANOS_PER_MILLI = 1_000_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun DiagnosticsPreview() {
    val now = 90_000_000_000_000L
    val snapshot =
        DiagnosticsSnapshot(
            generatedAtMillis = 0L,
            nowElapsedNanos = now,
            appVersionName = "0.1.0",
            appVersionCode = 1,
            deviceManufacturer = "TECNO",
            deviceModel = "KI5q",
            engine = "PLATFORM",
            permissions =
                PermissionSnapshot(
                    sdkInt = 34,
                    fineLocation = true,
                    coarseLocation = true,
                ),
            trackingStatus = TrackingStatus.RECORDING,
            health =
                TrackingHealth(
                    isRecording = true,
                    samplingIntervalMillis = 5_000,
                    windowExpected = 120,
                    windowActual = 90,
                    fixCount = 412,
                    lastFixElapsedNanos = now - 3_000_000_000L,
                    lastAccuracyMetres = 7.5,
                    lastSatellitesUsed = 11,
                    gaps = listOf(GapRecord(now - 120_000_000_000L, 90_000, isOpen = true)),
                ),
            freeStorageBytes = 1_536L * BYTES_PER_MB,
        )
    MeretTheme { DiagnosticsScreen(DiagnosticsUiState.Ready(snapshot, ""), onCopy = {}) }
}
