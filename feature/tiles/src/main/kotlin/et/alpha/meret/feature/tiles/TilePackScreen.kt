package et.alpha.meret.feature.tiles

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import et.alpha.meret.core.designsystem.component.EmptyState
import et.alpha.meret.core.designsystem.component.LayersGlyph
import et.alpha.meret.core.designsystem.theme.MeretTheme

/**
 * Placeholder for the packs screen.
 *
 * The list itself lands in a later phase; what is real here is the empty state, which
 * is what a fresh install actually shows and therefore the first thing a field worker
 * sees. It reads as "nothing yet", not as "something went wrong".
 */
@Composable
public fun TilePackScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    EmptyState(
        title = stringResource(R.string.packs_empty_title),
        body = stringResource(R.string.packs_empty_body),
        icon = { LayersGlyph(it, tint = MaterialTheme.colorScheme.outline) },
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding),
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun Preview() {
    MeretTheme { TilePackScreen() }
}
