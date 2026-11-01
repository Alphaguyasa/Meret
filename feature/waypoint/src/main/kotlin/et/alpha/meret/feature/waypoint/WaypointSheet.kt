package et.alpha.meret.feature.waypoint

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import et.alpha.meret.core.designsystem.theme.MeretTheme
import et.alpha.meret.core.designsystem.theme.Sizes
import et.alpha.meret.core.designsystem.theme.Spacing
import et.alpha.meret.domain.waypoint.WaypointCategory
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The waypoint sheet's content: where it will be saved, a title, a category, notes, and Save.
 *
 * Save stays disabled until there is a live fix, and the sheet says why, so a waypoint is
 * never saved at a cached or made-up position.
 */
@Composable
fun WaypointSheet(
    state: WaypointCaptureUiState,
    onTitleChange: (String) -> Unit,
    onCategoryTap: (WaypointCategory) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.waypoint_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        FixLine(state.fix)
        if (state.onTrack) {
            Text(
                text = stringResource(R.string.waypoint_on_track),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = state.title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.waypoint_title_label)) },
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = stringResource(R.string.waypoint_category_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            WaypointCategory.entries.forEach { category ->
                FilterChip(
                    selected = category == state.category,
                    onClick = { onCategoryTap(category) },
                    label = { Text(stringResource(category.label)) },
                    modifier = Modifier.heightIn(min = Sizes.touchTargetMin),
                )
            }
        }

        OutlinedTextField(
            value = state.notes,
            onValueChange = onNotesChange,
            label = { Text(stringResource(R.string.waypoint_notes_label)) },
            minLines = NOTES_MIN_LINES,
            keyboardOptions =
                KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.heightIn(min = Sizes.touchTargetMin),
            ) { Text(stringResource(R.string.waypoint_cancel)) }
            Button(
                onClick = onSave,
                enabled = state.canSave,
                modifier = Modifier.heightIn(min = Sizes.touchTargetMin),
            ) { Text(stringResource(R.string.waypoint_save)) }
        }
    }
}

/** The position the waypoint would get, or why there is none yet. Read out when it changes. */
@Composable
private fun FixLine(fix: CaptureFix) {
    val text =
        when (fix) {
            CaptureFix.Waiting -> {
                stringResource(R.string.waypoint_waiting_for_fix)
            }

            is CaptureFix.Located -> {
                stringResource(
                    R.string.waypoint_position,
                    coordinate(fix.latitude),
                    coordinate(fix.longitude),
                    fix.accuracyMetres.roundToInt(),
                )
            }
        }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color =
            if (fix is CaptureFix.Located) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Five decimals, about a metre: as fine as a phone's fix means anything. Digits in any locale. */
private fun coordinate(degrees: Double): String = String.format(Locale.ROOT, "%.5f", degrees)

@get:StringRes
private val WaypointCategory.label: Int
    get() =
        when (this) {
            WaypointCategory.FARM_PLOT -> R.string.waypoint_category_farm_plot
            WaypointCategory.HOUSEHOLD -> R.string.waypoint_category_household
            WaypointCategory.WATER_POINT -> R.string.waypoint_category_water_point
            WaypointCategory.LANDMARK -> R.string.waypoint_category_landmark
            WaypointCategory.OTHER -> R.string.waypoint_category_other
        }

private const val NOTES_MIN_LINES = 3

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun LocatedPreview() {
    MeretTheme {
        Surface {
            WaypointSheet(
                state =
                    WaypointCaptureUiState(
                        fix = CaptureFix.Located(9.03012, 38.74021, 4.6),
                        onTrack = true,
                        title = "Borehole",
                        category = WaypointCategory.WATER_POINT,
                    ),
                onTitleChange = {},
                onCategoryTap = {},
                onNotesChange = {},
                onSave = {},
                onCancel = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun WaitingPreview() {
    MeretTheme {
        Surface {
            WaypointSheet(
                state = WaypointCaptureUiState(),
                onTitleChange = {},
                onCategoryTap = {},
                onNotesChange = {},
                onSave = {},
                onCancel = {},
            )
        }
    }
}
