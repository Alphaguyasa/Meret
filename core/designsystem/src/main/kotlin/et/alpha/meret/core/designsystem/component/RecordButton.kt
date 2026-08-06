package et.alpha.meret.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import et.alpha.meret.core.designsystem.R
import et.alpha.meret.core.designsystem.theme.MeretTheme
import et.alpha.meret.core.designsystem.theme.Sizes

/** What the record button is currently showing. */
public enum class RecordingState { Idle, Recording, Paused }

/**
 * The primary control. 72 dp, well above the Material FAB size, because it is pressed
 * one-handed while walking.
 *
 * Tap pauses or resumes; **long-press stops**. That asymmetry is deliberate: stopping a
 * track is the destructive action, and a long-press cannot be done by accident against a
 * trouser pocket. Stopping still opens an undo window at the call site — see
 * DESIGN_SYSTEM.md rule 4.
 *
 * The recording pulse is suppressed when [reduceMotion] is set, which the caller should
 * do on low battery. A 1 Hz animation running for eight hours is not free.
 */
@Composable
public fun RecordButton(
    state: RecordingState,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    val colors = MeretTheme.colors
    val fillColor =
        when (state) {
            RecordingState.Idle -> colors.tracking
            RecordingState.Recording -> colors.tracking
            RecordingState.Paused -> colors.paused
        }

    val pulseScale =
        if (state == RecordingState.Recording && !reduceMotion) {
            val transition = rememberInfiniteTransition(label = "record-pulse")
            transition
                .animateFloat(
                    initialValue = 1f,
                    targetValue = PULSE_MAX_SCALE,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = PULSE_PERIOD_MILLIS),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "record-pulse-scale",
                ).value
        } else {
            1f
        }

    val tapLabel =
        stringResource(
            when (state) {
                RecordingState.Idle -> R.string.record_start
                RecordingState.Recording -> R.string.record_pause
                RecordingState.Paused -> R.string.record_resume
            },
        )
    val longPressLabel = stringResource(R.string.record_stop)
    val onSurface = MaterialTheme.colorScheme.surface

    Box(
        modifier =
            modifier
                .size(Sizes.recordButton)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = Sizes.recordButton / 2),
                    onClick = onTap,
                    onLongClick = onLongPress,
                ).semantics {
                    contentDescription = tapLabel
                    onLongClick(label = longPressLabel) {
                        onLongPress()
                        true
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(Sizes.recordButton)) {
            val centre = Offset(size.width / 2, size.height / 2)
            val bodyRadius = size.minDimension / 2 * BODY_RADIUS_FRACTION

            // Pulsing ring sits outside the body so the body itself never moves — a
            // control that changes size under the thumb is hard to hit.
            if (pulseScale > 1f) {
                drawCircle(
                    color = fillColor.copy(alpha = PULSE_RING_ALPHA / pulseScale),
                    radius = bodyRadius * pulseScale,
                    center = centre,
                )
            }

            drawCircle(color = fillColor, radius = bodyRadius, center = centre)
            // White halo: this button sits on top of the map, over unknown colours.
            drawCircle(
                color = onSurface,
                radius = bodyRadius,
                center = centre,
                style = Stroke(width = HALO_WIDTH_PX),
            )

            drawGlyph(state, centre, bodyRadius, onSurface)
        }
    }
}

private fun DrawScope.drawGlyph(
    state: RecordingState,
    centre: Offset,
    bodyRadius: Float,
    glyphColor: Color,
) {
    when (state) {
        // A filled dot: the universal "record".
        RecordingState.Idle -> {
            drawCircle(
                color = glyphColor,
                radius = bodyRadius * GLYPH_DOT_FRACTION,
                center = centre,
            )
        }

        // Two bars: pressing this pauses.
        RecordingState.Recording -> {
            val barWidth = bodyRadius * PAUSE_BAR_WIDTH_FRACTION
            val barHeight = bodyRadius * PAUSE_BAR_HEIGHT_FRACTION
            val gap = barWidth
            listOf(-1, 1).forEach { side ->
                drawRect(
                    color = glyphColor,
                    topLeft =
                        Offset(
                            centre.x + side * (gap / 2) - if (side < 0) barWidth else 0f,
                            centre.y - barHeight / 2,
                        ),
                    size = Size(barWidth, barHeight),
                )
            }
        }

        // A triangle: pressing this resumes.
        RecordingState.Paused -> {
            val half = bodyRadius * PLAY_SIZE_FRACTION
            val path =
                Path().apply {
                    moveTo(centre.x - half * PLAY_X_OFFSET, centre.y - half)
                    lineTo(centre.x + half, centre.y)
                    lineTo(centre.x - half * PLAY_X_OFFSET, centre.y + half)
                    close()
                }
            drawPath(path, glyphColor)
        }
    }
}

private const val PULSE_PERIOD_MILLIS = 1000
private const val PULSE_MAX_SCALE = 1.18f
private const val PULSE_RING_ALPHA = 0.35f
private const val BODY_RADIUS_FRACTION = 0.86f
private const val HALO_WIDTH_PX = 4f
private const val GLYPH_DOT_FRACTION = 0.42f
private const val PAUSE_BAR_WIDTH_FRACTION = 0.16f
private const val PAUSE_BAR_HEIGHT_FRACTION = 0.62f
private const val PLAY_SIZE_FRACTION = 0.42f
private const val PLAY_X_OFFSET = 0.72f

@Preview(name = "Record button", showBackground = true)
@Composable
private fun RecordButtonPreview() {
    MeretTheme {
        Row(
            modifier = Modifier.size(width = 280.dp, height = 120.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecordButton(RecordingState.Idle, {}, {})
            RecordButton(RecordingState.Recording, {}, {}, reduceMotion = true)
            RecordButton(RecordingState.Paused, {}, {})
        }
    }
}
