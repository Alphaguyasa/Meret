package et.alpha.meret.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role

/**
 * The handful of glyphs this app needs, drawn rather than pulled in from an icon
 * library.
 *
 * `material-icons-extended` is ~10 MB of vectors for the six shapes we actually use, and
 * it is frozen upstream. Drawing them keeps the APK small — which matters on the devices
 * this ships to — and lets the stroke weight match the type scale instead of Material's.
 *
 * Every one takes a [contentDescription]; pass `null` only when the icon sits inside an
 * already-labelled control.
 */

/** Downward chevron. Rotate 180° for "up". */
@Composable
public fun Chevron(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Canvas(modifier.iconSemantics(contentDescription)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(
            width = w * STROKE_FRACTION,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.25f, h * 0.4f)
            lineTo(w * 0.5f, h * 0.65f)
            lineTo(w * 0.75f, h * 0.4f)
        }
        drawPath(path, tint, style = stroke)
    }
}

/** Map pin, filled. Used for waypoints. */
@Composable
public fun PinGlyph(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    /** Colour of the hole in the pin head — normally whatever it is drawn on top of. */
    punchColor: Color = MaterialTheme.colorScheme.surface,
) {
    Canvas(modifier.iconSemantics(contentDescription)) {
        val w = size.width
        val h = size.height
        val headRadius = w * 0.28f
        val headCentre = Offset(w / 2, h * 0.38f)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w / 2, h * 0.92f)
            lineTo(w / 2 - headRadius * 0.85f, h * 0.55f)
            lineTo(w / 2 + headRadius * 0.85f, h * 0.55f)
            close()
        }
        drawPath(path, tint)
        drawCircle(tint, radius = headRadius, center = headCentre)
        // Punch-out so the pin reads as a pin and not a lollipop at 16 dp.
        drawCircle(punchColor, radius = headRadius * 0.38f, center = headCentre)
    }
}

/** Concentric rings — the "locate me" / follow-mode control. */
@Composable
public fun CrosshairGlyph(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Canvas(modifier.iconSemantics(contentDescription)) {
        val centre = Offset(size.width / 2, size.height / 2)
        val outer = size.minDimension * 0.34f
        val stroke = Stroke(width = size.width * STROKE_FRACTION, cap = StrokeCap.Round)
        drawCircle(tint, radius = outer, center = centre, style = stroke)
        drawCircle(tint, radius = outer * 0.34f, center = centre)
        listOf(
            Offset(centre.x, 0f) to Offset(centre.x, centre.y - outer * 1.45f),
            Offset(centre.x, size.height) to Offset(centre.x, centre.y + outer * 1.45f),
            Offset(0f, centre.y) to Offset(centre.x - outer * 1.45f, centre.y),
            Offset(size.width, centre.y) to Offset(centre.x + outer * 1.45f, centre.y),
        ).forEach { (from, to) ->
            drawLine(tint, from, to, strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
    }
}

/** Plus. Add a waypoint, add a vertex. */
@Composable
public fun PlusGlyph(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Canvas(modifier.iconSemantics(contentDescription)) {
        val centre = Offset(size.width / 2, size.height / 2)
        val arm = size.minDimension * 0.3f
        val width = size.width * STROKE_FRACTION
        drawLine(
            tint,
            Offset(centre.x - arm, centre.y),
            Offset(centre.x + arm, centre.y),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
        drawLine(
            tint,
            Offset(centre.x, centre.y - arm),
            Offset(centre.x, centre.y + arm),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }
}

/** A polyline with vertex dots — the "tracks" affordance. */
@Composable
public fun TrackGlyph(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Canvas(modifier.iconSemantics(contentDescription)) {
        val w = size.width
        val h = size.height
        val points = listOf(
            Offset(w * 0.18f, h * 0.76f),
            Offset(w * 0.4f, h * 0.34f),
            Offset(w * 0.62f, h * 0.66f),
            Offset(w * 0.84f, h * 0.24f),
        )
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path,
            tint,
            style = Stroke(
                width = w * STROKE_FRACTION,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        points.forEach { drawCircle(tint, radius = w * 0.075f, center = it) }
    }
}

/** Stacked layers — the map/tile-pack affordance. */
@Composable
public fun LayersGlyph(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Canvas(modifier.iconSemantics(contentDescription)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(
            width = w * STROKE_FRACTION,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        fun diamond(centreY: Float) = androidx.compose.ui.graphics.Path().apply {
            moveTo(w / 2, centreY - h * 0.14f)
            lineTo(w * 0.85f, centreY)
            lineTo(w / 2, centreY + h * 0.14f)
            lineTo(w * 0.15f, centreY)
            close()
        }
        drawPath(diamond(h * 0.68f), tint, style = stroke)
        drawPath(diamond(h * 0.34f), tint)
    }
}

private fun Modifier.iconSemantics(contentDescription: String?): Modifier =
    if (contentDescription == null) {
        this
    } else {
        this.clearAndSetSemantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    }

private const val STROKE_FRACTION = 0.11f
