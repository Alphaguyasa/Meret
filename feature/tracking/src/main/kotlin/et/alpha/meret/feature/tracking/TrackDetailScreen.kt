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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import et.alpha.meret.core.model.TrackNames
import et.alpha.meret.core.model.geo.BoundingBox
import kotlin.math.roundToInt
import kotlin.math.sin
import et.alpha.meret.core.designsystem.R as DesignR

/**
 * One track: the header the list row shows, the four totals, the elevation profile, and
 * one line per segment (a segment ends at each pause). Numbers, not bars: field workers
 * check figures against what they walked. «Show on map» opens the map fitted to the track; a
 * track with no accepted fix yet has nothing to show and no button. «Rename» names the
 * track, for example after the kebele or farm walked; an empty name shows the date again.
 */
@Composable
fun TrackDetailScreen(
    state: TrackDetailUiState,
    onBack: () -> Unit,
    onShowOnMap: (trackId: String, bounds: BoundingBox) -> Unit,
    onRename: (name: String) -> Unit,
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
        is TrackDetailUiState.Ready -> Detail(state, onBack, onShowOnMap, onRename, padded)
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
    onShowOnMap: (trackId: String, bounds: BoundingBox) -> Unit,
    onRename: (name: String) -> Unit,
    modifier: Modifier,
) {
    // The text being typed while the rename dialog is open; null while it is closed. Saved,
    // so a rotation or a background kill does not lose a half-typed name.
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    val typed = renaming
    if (typed != null) {
        RenameDialog(
            text = typed,
            onTextChange = { renaming = it },
            onSave = {
                onRename(typed)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
    LazyColumn(modifier) {
        item { BackButton(onBack) }
        item { Header(state, onRename = { renaming = state.track.name.orEmpty() }) }
        val bounds = state.bounds
        if (bounds != null) {
            item { ShowOnMapButton(onClick = { onShowOnMap(state.track.id, bounds) }) }
        }
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
private fun ShowOnMapButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    ) {
        Text(stringResource(R.string.track_detail_show_on_map))
    }
}

@Composable
private fun RenameDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.track_rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { typed ->
                    // A paste longer than the limit is refused whole rather than cut.
                    if (typed.codePointCount(0, typed.length) <= TrackNames.MAX_LENGTH) {
                        onTextChange(typed)
                    }
                },
                label = { Text(stringResource(R.string.track_rename_label)) },
                supportingText = { Text(stringResource(R.string.track_rename_hint)) },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text(stringResource(R.string.track_rename_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.track_rename_cancel)) }
        },
    )
}

@Composable
private fun Header(
    state: TrackDetailUiState.Ready,
    onRename: () -> Unit,
) {
    val track = state.track
    Column(
        modifier = Modifier.padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = track.title(),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRename) { Text(stringResource(R.string.track_detail_rename)) }
        }
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
                    bounds = BoundingBox(9.0, 38.74, 9.02, 38.76),
                ),
            onBack = {},
            onShowOnMap = { _, _ -> },
            onRename = {},
        )
    }
}
