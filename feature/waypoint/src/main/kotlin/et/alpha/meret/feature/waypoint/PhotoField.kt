package et.alpha.meret.feature.waypoint

import android.graphics.BitmapFactory
import android.media.ExifInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import et.alpha.meret.core.designsystem.theme.Sizes
import et.alpha.meret.core.designsystem.theme.Spacing
import et.alpha.meret.core.model.form.FormField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * A photo field: the photos taken so far as small thumbnails, each removable after a
 * confirmation, and «Take photo» until the field's [FormField.Photo.maxCount] is reached.
 *
 * A thumbnail is decoded off the main thread at an eighth of the stored size (200 px from
 * 1600), so a row of them costs a low-RAM phone almost nothing, and is turned the way the
 * photo's EXIF says.
 */
@Composable
internal fun PhotoControl(
    field: FormField.Photo,
    photos: List<FormPhoto>,
    onTakePhoto: () -> Unit,
    onRemovePhoto: (String) -> Unit,
) {
    var confirmRemove by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (photos.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                photos.forEachIndexed { index, photo ->
                    PhotoThumbnail(
                        photo = photo,
                        number = index + 1,
                        onRemove = { confirmRemove = photo.id },
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.form_photo_count, photos.size, field.maxCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onTakePhoto,
            enabled = photos.size < field.maxCount,
            modifier = Modifier.heightIn(min = Sizes.touchTargetField),
        ) { Text(stringResource(R.string.form_photo_take)) }
    }
    confirmRemove?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(stringResource(R.string.form_photo_remove_title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = null
                        onRemovePhoto(id)
                    },
                ) { Text(stringResource(R.string.form_photo_remove_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) {
                    Text(stringResource(R.string.waypoint_cancel))
                }
            },
        )
    }
}

@Composable
private fun PhotoThumbnail(
    photo: FormPhoto,
    number: Int,
    onRemove: () -> Unit,
) {
    val thumbnail by produceState<Thumbnail?>(initialValue = null, photo.path) {
        value = photo.path?.let { loadThumbnail(it) }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier
                    .size(THUMBNAIL_SIZE)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val loaded = thumbnail
            if (loaded != null) {
                Image(
                    bitmap = loaded.bitmap,
                    contentDescription = stringResource(R.string.form_photo_description, number),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(THUMBNAIL_SIZE).rotate(loaded.degrees),
                )
            } else {
                Text(
                    text = stringResource(R.string.form_photo_description, number),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        val removeLabel = stringResource(R.string.form_photo_remove_numbered, number)
        TextButton(
            onClick = onRemove,
            modifier =
                Modifier
                    .heightIn(min = Sizes.touchTargetField)
                    .semantics { contentDescription = removeLabel },
        ) { Text(stringResource(R.string.form_photo_remove)) }
    }
}

private class Thumbnail(
    val bitmap: ImageBitmap,
    val degrees: Float,
)

private suspend fun loadThumbnail(path: String): Thumbnail? = withContext(Dispatchers.IO) {
    val options = BitmapFactory.Options().apply { inSampleSize = THUMBNAIL_SAMPLE_SIZE }
    val bitmap = BitmapFactory.decodeFile(path, options) ?: return@withContext null
    Thumbnail(bitmap.asImageBitmap(), rotationOf(path))
}

private fun rotationOf(path: String): Float = try {
    when (
        ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    ) {
        ExifInterface.ORIENTATION_ROTATE_90 -> ROTATE_90
        ExifInterface.ORIENTATION_ROTATE_180 -> ROTATE_180
        ExifInterface.ORIENTATION_ROTATE_270 -> ROTATE_270
        else -> 0f
    }
} catch (ignored: IOException) {
    // The pixels still show; only their turn is unknown.
    0f
}

private val THUMBNAIL_SIZE = 72.dp
private const val THUMBNAIL_SAMPLE_SIZE = 8
private const val ROTATE_90 = 90f
private const val ROTATE_180 = 180f
private const val ROTATE_270 = 270f
