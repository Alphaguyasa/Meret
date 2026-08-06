package et.alpha.meret.feature.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import et.alpha.meret.core.designsystem.component.CrosshairGlyph
import et.alpha.meret.core.designsystem.component.GpsQuality
import et.alpha.meret.core.designsystem.component.GpsQualityChip
import et.alpha.meret.core.designsystem.component.PlusGlyph
import et.alpha.meret.core.designsystem.component.RecordButton
import et.alpha.meret.core.designsystem.component.RecordingState
import et.alpha.meret.core.designsystem.component.SyncBadge
import et.alpha.meret.core.designsystem.component.SyncState
import et.alpha.meret.core.designsystem.component.TrackingBar
import et.alpha.meret.core.designsystem.component.TrackingStats
import et.alpha.meret.core.designsystem.theme.Elevations
import et.alpha.meret.core.designsystem.theme.MeretTheme
import et.alpha.meret.core.designsystem.theme.Sizes
import et.alpha.meret.core.designsystem.theme.Spacing

/**
 * Everything the map screen renders. One sealed shape, not an `isLoading` +
 * `errorMessage` + `data` triple — and note there is no loading state for user data,
 * because the data is local and there is nothing to wait for.
 */
public data class MapUiState(
    val gpsQuality: GpsQuality = GpsQuality.NoFix,
    val accuracyMetres: Float? = null,
    val satelliteCount: Int? = null,
    val recordingState: RecordingState = RecordingState.Idle,
    val syncState: SyncState = SyncState.Idle,
    val pendingCount: Int = 0,
    val isOffline: Boolean = true,
    val trackingStats: TrackingStats? = null,
    val basemapInstalled: Boolean = false,
)

/**
 * The home screen.
 *
 * Layout follows the field rules rather than convention: the readouts sit at the top
 * where they are glanceable, and every control the user actually presses is in the
 * bottom third, inside thumb reach on a large phone held one-handed.
 */
@Composable
public fun MapScreen(
    state: MapUiState,
    onRecordTap: () -> Unit,
    onRecordLongPress: () -> Unit,
    onAddWaypoint: () -> Unit,
    onRecentreMap: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var barExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        BasemapSurface(
            installed = state.basemapInstalled,
            modifier = Modifier.fillMaxSize(),
        )

        // --- Top: readouts. Glanceable, never pressed by accident. -------------------
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GpsQualityChip(
                    quality = state.gpsQuality,
                    accuracyMetres = state.accuracyMetres,
                    satelliteCount = state.satelliteCount,
                    onClick = onOpenDiagnostics,
                )
                SyncBadge(
                    state = state.syncState,
                    pendingCount = state.pendingCount,
                    onClick = onOpenSync,
                )
            }
        }

        // --- Bottom: everything pressable. -------------------------------------------
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contentPadding.calculateBottomPadding()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                MapControl(
                    onClick = onAddWaypoint,
                    contentDescription = stringResource(R.string.map_add_waypoint),
                ) { PlusGlyph(it, tint = MaterialTheme.colorScheme.onSurface) }

                RecordButton(
                    state = state.recordingState,
                    onTap = onRecordTap,
                    onLongPress = onRecordLongPress,
                )

                MapControl(
                    onClick = onRecentreMap,
                    contentDescription = stringResource(R.string.map_recentre),
                ) { CrosshairGlyph(it, tint = MaterialTheme.colorScheme.onSurface) }
            }

            Spacer(Modifier.height(Spacing.sm))

            state.trackingStats?.let { stats ->
                TrackingBar(
                    stats = stats,
                    expanded = barExpanded,
                    onToggleExpanded = { barExpanded = !barExpanded },
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
                Spacer(Modifier.height(Spacing.xs))
            }
        }
    }
}

/** A round control that floats over the map. Sized for a walking thumb, not a desk. */
@Composable
private fun MapControl(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    glyph: @Composable (Modifier) -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(Sizes.touchTargetField),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = Elevations.card,
        shadowElevation = Elevations.card,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.semantics { this.contentDescription = contentDescription },
        ) {
            glyph(Modifier.size(Sizes.icon))
        }
    }
}

/**
 * Stand-in for the MapLibre surface.
 *
 * Real vector tiles arrive in phase 6 with the `.mbtiles` reader and the loopback tile
 * server. Until then this draws a neutral terrain field so the screen composes and the
 * overlay chrome can be judged against something map-shaped — and, when no pack is
 * installed, it says so plainly rather than showing a grey grid the way Google Maps does
 * off-network.
 */
@Composable
private fun BasemapSurface(
    installed: Boolean,
    modifier: Modifier = Modifier,
) {
    val landcover = MaterialTheme.colorScheme.surfaceContainerLow
    val contour = MaterialTheme.colorScheme.outlineVariant
    val water = MeretTheme.colors.assignment.copy(alpha = 0.18f)

    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(landcover, MaterialTheme.colorScheme.surfaceContainer),
            ),
        ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Contour-like bands. Purely decorative; deliberately low contrast so the
            // overlay chrome stays the thing your eye lands on.
            val bandCount = 7
            repeat(bandCount) { index ->
                val y = size.height * (index + 1) / (bandCount + 1f)
                val path = Path().apply {
                    moveTo(0f, y)
                    cubicTo(
                        size.width * 0.3f, y - size.height * 0.06f,
                        size.width * 0.7f, y + size.height * 0.06f,
                        size.width, y - size.height * 0.02f,
                    )
                }
                drawPath(
                    path,
                    contour.copy(alpha = 0.35f),
                    style = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            val river = Path().apply {
                moveTo(size.width * 0.12f, 0f)
                cubicTo(
                    size.width * 0.35f, size.height * 0.35f,
                    size.width * 0.05f, size.height * 0.6f,
                    size.width * 0.4f, size.height,
                )
            }
            drawPath(river, water, style = Stroke(width = 14f, cap = StrokeCap.Round))

            drawCircle(
                color = contour.copy(alpha = 0.25f),
                radius = size.minDimension * 0.16f,
                center = Offset(size.width * 0.72f, size.height * 0.42f),
            )
        }

        if (!installed) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(Spacing.lg),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = Elevations.card,
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.map_no_pack_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.map_no_pack_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = Spacing.xxs),
                    )
                }
            }
        }
    }
}

@Preview(name = "Map — recording", showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun MapScreenPreview() {
    MeretTheme {
        MapScreen(
            state = MapUiState(
                gpsQuality = GpsQuality.Excellent,
                accuracyMetres = 4f,
                satelliteCount = 14,
                recordingState = RecordingState.Recording,
                syncState = SyncState.Pending,
                pendingCount = 214,
                isOffline = true,
                trackingStats = TrackingStats(
                    state = RecordingState.Recording,
                    duration = "01:42:07",
                    distance = "4.82",
                    distanceUnit = "km",
                    pointCount = "1,204",
                    segmentCount = "3",
                    accuracy = "4",
                ),
            ),
            onRecordTap = {},
            onRecordLongPress = {},
            onAddWaypoint = {},
            onRecentreMap = {},
            onOpenSync = {},
            onOpenDiagnostics = {},
        )
    }
}

