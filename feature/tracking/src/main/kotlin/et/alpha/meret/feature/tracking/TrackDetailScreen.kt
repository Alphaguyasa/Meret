package et.alpha.meret.feature.tracking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import et.alpha.meret.core.designsystem.component.EmptyState
import et.alpha.meret.core.designsystem.component.Metric
import et.alpha.meret.core.designsystem.component.TrackGlyph
import et.alpha.meret.core.designsystem.format.formatDuration
import et.alpha.meret.core.designsystem.theme.MeretTheme
import et.alpha.meret.core.designsystem.theme.Spacing
import et.alpha.meret.core.model.ElevationSample
import et.alpha.meret.core.model.SegmentSummary
import et.alpha.meret.core.model.SyncState
import kotlin.math.roundToInt
import kotlin.math.sin
import et.alpha.meret.core.designsystem.R as DesignR

/**
 * One track: the header the list row shows, the four totals, the elevation profile, and
 * one line per segment (a segment ends at each pause). Numbers, not bars: field workers
 * check figures against what they walked.
 */
@Composable
fun TrackDetailScreen(
    state: TrackDetailUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val padded =
        modifier
            .fillMaxSize()
            .padding(contentPadding)
    when (state) {
        TrackDetailUiState.Loading -> Unit
        TrackDetailUiState.NotFound -> NotFound(onBack, padded)
        is TrackDetailUiState.Ready -> Detail(state, onBack, padded)
    }
}

@Composable
private fun NotFound(
    onBack: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        BackButton(onBack)
        EmptyState(
            title = stringResource(R.string.track_detail_not_found),
            body = stringResource(R.string.track_detail_not_found_body),
            icon = { TrackGlyph(it, tint = MaterialTheme.colorScheme.outline) },
        )
    }
}

@Composable
private fun Detail(
    state: TrackDetailUiState.Ready,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    LazyColumn(modifier) {
        item { BackButton(onBack) }
        item { Header(state) }
        item { Section(stringResource(R.string.track_detail_elevation)) }
        item { ElevationProfile(state.elevation) }
        item { Section(stringResource(R.string.track_detail_segments)) }
        items(state.segments, key = { it.index }) { segment ->
            SegmentRow(segment)
            HorizontalDivider()
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    TextButton(onClick = onBack, modifier = Modifier.padding(start = Spacing.xs)) {
        Text(stringResource(R.string.track_detail_back))
    }
}

@Composable
private fun Header(state: TrackDetailUiState.Ready) {
    val track = state.track
    Column(
        modifier = Modifier.padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(text = track.title(), style = MaterialTheme.typography.titleLarge)
        val (label, color) = track.standing()
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = color)
        val (distance, unit) = distanceText(track.distanceMetres)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Metric(
                value = distance,
                unit = unit,
                label = stringResource(DesignR.string.metric_distance),
            )
            Metric(
                value = formatDuration(track.movingDurationMillis),
                unit = "",
                label = stringResource(DesignR.string.metric_duration),
            )
            Metric(
                value = state.pointCount.toString(),
                unit = "",
                label = stringResource(DesignR.string.metric_points),
            )
            Metric(
                value = state.segments.size.toString(),
                unit = "",
                label = stringResource(DesignR.string.metric_segments),
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier =
            Modifier
                .padding(horizontal = Spacing.md)
                .padding(top = Spacing.lg, bottom = Spacing.xs),
    )
}

/** Altitude against distance along the track, lowest at the bottom, with the range in words. */
@Composable
private fun ElevationProfile(samples: List<ElevationSample>) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (samples.size < 2) {
        Text(
            text = stringResource(R.string.track_detail_no_elevation),
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
            modifier = Modifier.padding(horizontal = Spacing.md),
        )
        return
    }
    val lowest = samples.minOf { it.altitudeMetres }
    val highest = samples.maxOf { it.altitudeMetres }
    val range =
        stringResource(
            R.string.track_detail_altitude_range,
            lowest.roundToInt(),
            highest.roundToInt(),
        )
    val line = MaterialTheme.colorScheme.primary
    Column(Modifier.padding(horizontal = Spacing.md)) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(PROFILE_HEIGHT)
                    .semantics { contentDescription = range },
        ) {
            val length = samples.last().distanceMetres.coerceAtLeast(MIN_SPAN)
            val rise = (highest - lowest).coerceAtLeast(MIN_SPAN)
            val path = Path()
            samples.forEachIndexed { i, sample ->
                val point =
                    Offset(
                        x = (sample.distanceMetres / length * size.width).toFloat(),
                        y = ((highest - sample.altitudeMetres) / rise * size.height).toFloat(),
                    )
                if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(
                path = path,
                color = line,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        Text(text = range, style = MaterialTheme.typography.bodyMedium, color = muted)
    }
}

@Composable
private fun SegmentRow(segment: SegmentSummary) {
    val (distance, unit) = distanceText(segment.distanceMetres)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.track_detail_segment, segment.index + 1),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text =
                stringResource(
                    R.string.track_detail_segment_stats,
                    distance,
                    unit,
                    formatDuration(segment.durationMillis),
                    segment.pointCount,
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A flat track would divide by zero; a one-metre floor draws it as a level line. */
private const val MIN_SPAN = 1.0
private val PROFILE_HEIGHT = 120.dp

@Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun DetailPreview() {
    MeretTheme {
        TrackDetailScreen(
            state =
                TrackDetailUiState.Ready(
                    track =
                        TrackItem(
                            id = "a",
                            name = null,
                            startedAtMillis = 1_761_800_000_000L,
                            distanceMetres = 2_460.0,
                            movingDurationMillis = 2_520_000L,
                            progress = TrackProgress.Finished,
                            syncState = SyncState.PENDING,
                        ),
                    pointCount = 812,
                    segments =
                        listOf(
                            SegmentSummary(0, 500, 1_510.0, 1_560_000L),
                            SegmentSummary(1, 312, 950.0, 960_000L),
                        ),
                    elevation = List(40) { ElevationSample(it * 60.0, 2_400 + 20 * sin(it / 5.0)) },
                ),
            onBack = {},
        )
    }
}
