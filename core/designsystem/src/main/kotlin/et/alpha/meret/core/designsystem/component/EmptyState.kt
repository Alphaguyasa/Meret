package et.alpha.meret.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import et.alpha.meret.core.designsystem.theme.MeretTheme
import et.alpha.meret.core.designsystem.theme.Spacing

/**
 * What a list looks like before there is anything in it.
 *
 * Deliberately quiet: an empty track list is the normal state of a fresh install, not a
 * problem, so there is no warning colour and no exclamation. The body text says what to
 * do next rather than restating that the list is empty.
 */
@Composable
public fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: @Composable (Modifier) -> Unit = {},
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon(Modifier.size(48.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .padding(top = Spacing.xs)
                    .widthIn(max = MAX_BODY_WIDTH),
        )
    }
}

private val MAX_BODY_WIDTH = 320.dp

@Preview(showBackground = true, widthDp = 380, heightDp = 400)
@Composable
private fun EmptyStatePreview() {
    MeretTheme {
        EmptyState(
            title = "No tracks yet",
            body = "Press record on the map to start logging where you walk.",
            icon = { TrackGlyph(it, tint = MaterialTheme.colorScheme.outline) },
        )
    }
}
