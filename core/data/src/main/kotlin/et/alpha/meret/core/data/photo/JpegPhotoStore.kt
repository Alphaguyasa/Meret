package et.alpha.meret.core.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.image.PhotoSizing
import et.alpha.meret.core.common.image.PixelSize
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.common.time.TimeProvider
import et.alpha.meret.core.common.uuid.UuidV7Generator
import et.alpha.meret.domain.waypoint.PhotoStore
import et.alpha.meret.domain.waypoint.StoredPhoto
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject

/**
 * Waypoint photos as JPEG files in `files/photos/`: app-private, and excluded from every
 * backup and device transfer by the app's backup rules (the decision log A5).
 *
 * The capture is decoded skipping pixels by a power of two, then scaled to the exact size
 * ([PhotoSizing]), so a 12 MP capture never sits in memory whole. The new file is written
 * under a temporary name and renamed only once it is complete, so a crash leaves either no
 * photo or a whole one, never half a JPEG with a row pointing at it.
 *
 * Re-encoding drops the camera's EXIF, so the tags worth keeping are copied across: the GPS
 * block, the capture time and the orientation (the pixels are not turned; viewers turn them).
 * The phone's make, model, serial numbers and thumbnail are deliberately not.
 */
public class JpegPhotoStore
    @Inject
    internal constructor(
        @ApplicationContext context: Context,
        private val ids: UuidV7Generator,
        private val time: TimeProvider,
        private val dispatchers: DispatcherProvider,
    ) : PhotoStore {
        private val directory = File(context.filesDir, DIRECTORY)

        override suspend fun store(capturePath: String): AppResult<StoredPhoto> =
            withContext(dispatchers.default) {
                val capture = File(capturePath)
                if (!capture.isFile) return@withContext failure(AppError.Storage.Kind.NotFound)
                val source =
                    imageSize(capture) ?: return@withContext failure(AppError.Storage.Kind.Corrupt)
                val id = ids.generate()
                val temporary = File(directory, "$id$TEMPORARY_SUFFIX")
                try {
                    val stored = shrink(capture, source, temporary)
                    if (stored == null) {
                        temporary.delete()
                        return@withContext failure(AppError.Storage.Kind.Corrupt)
                    }
                    copyExif(capture, temporary)
                    val file = File(directory, "$id$SUFFIX")
                    if (!temporary.renameTo(file)) {
                        temporary.delete()
                        return@withContext failure(AppError.Storage.Kind.WriteFailed)
                    }
                    val capturedAt = time.nowMillis()
                    // describe() reads the capture time back from here, after a process death.
                    file.setLastModified(capturedAt)
                    capture.delete()
                    AppResult.Success(read(id, file, stored, capturedAt))
                } catch (e: IOException) {
                    temporary.delete()
                    AppResult.Failure(AppError.Storage(AppError.Storage.Kind.WriteFailed, e))
                } catch (e: OutOfMemoryError) {
                    // A low-RAM phone with other apps open. The capture stays, so the worker
                    // can try again once something has been closed.
                    temporary.delete()
                    AppResult.Failure(AppError.Storage(AppError.Storage.Kind.ReadFailed, e))
                }
            }

        override suspend fun describe(id: String): StoredPhoto? = withContext(dispatchers.io) {
            val file = fileOf(id)?.takeIf { it.isFile } ?: return@withContext null
            try {
                val size = imageSize(file) ?: return@withContext null
                read(id, file, size, file.lastModified())
            } catch (ignored: IOException) {
                // Unreadable is as good as gone: Save leaves it out of the record.
                null
            }
        }

        override suspend fun delete(id: String) {
            withContext(dispatchers.io) {
                fileOf(id)?.delete()
            }
        }

        /**
         * The file of photo [id], or null when [id] is not one this store could have made. The
         * id comes from a draft's or a record's answers, so it must never name a path: no
         * `../`, no other directory.
         */
        private fun fileOf(id: String): File? =
            if (ID_PATTERN.matches(id)) File(directory, "$id$SUFFIX") else null

        private fun read(
            id: String,
            file: File,
            size: PixelSize,
            capturedAt: Long,
        ): StoredPhoto {
            val position = FloatArray(2)
            val located = ExifInterface(file.path).getLatLong(position)
            return StoredPhoto(
                id = id,
                path = file.path,
                width = size.width,
                height = size.height,
                bytes = file.length(),
                sha256 = sha256(file),
                capturedAt = capturedAt,
                latitude = position[0].toDouble().takeIf { located },
                longitude = position[1].toDouble().takeIf { located },
            )
        }

        /** Writes [capture], of [source] size, shrunk to [target]; null if it does not decode. */
        private fun shrink(
            capture: File,
            source: PixelSize,
            target: File,
        ): PixelSize? {
            val size = PhotoSizing.targetSize(source)
            val options =
                BitmapFactory.Options().apply {
                    inSampleSize = PhotoSizing.sampleSize(source, size)
                }
            val decoded = BitmapFactory.decodeFile(capture.path, options) ?: return null
            val scaled =
                if (decoded.width == size.width && decoded.height == size.height) {
                    decoded
                } else {
                    Bitmap.createScaledBitmap(decoded, size.width, size.height, true).also {
                        if (it !== decoded) decoded.recycle()
                    }
                }
            directory.mkdirs()
            try {
                val written =
                    target.outputStream().use {
                        scaled.compress(Bitmap.CompressFormat.JPEG, PhotoSizing.JPEG_QUALITY, it)
                    }
                if (!written) throw IOException("JPEG encoder refused the bitmap")
            } finally {
                scaled.recycle()
            }
            return size
        }

        /** The size of the image in [file], read from its header only; null if not an image. */
        private fun imageSize(file: File): PixelSize? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            return if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                PixelSize(bounds.outWidth, bounds.outHeight)
            } else {
                null
            }
        }

        private fun copyExif(
            from: File,
            to: File,
        ) {
            val source = ExifInterface(from.path)
            val target = ExifInterface(to.path)
            KEPT_TAGS.forEach { tag ->
                source.getAttribute(tag)?.let { target.setAttribute(tag, it) }
            }
            target.saveAttributes()
        }

        private fun failure(kind: AppError.Storage.Kind): AppResult<StoredPhoto> =
            AppResult.Failure(AppError.Storage(kind))

        internal companion object {
            const val DIRECTORY = "photos"
            const val SUFFIX = ".jpg"
            const val TEMPORARY_SUFFIX = ".jpg.part"

            /** A UUID as [UuidV7Generator] writes it: lower-case hex in 8-4-4-4-12. */
            val ID_PATTERN: Regex =
                Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

            /** GPS, capture time and orientation. Nothing that identifies the phone. */
            val KEPT_TAGS: List<String> =
                listOf(
                    ExifInterface.TAG_GPS_VERSION_ID,
                    ExifInterface.TAG_GPS_LATITUDE,
                    ExifInterface.TAG_GPS_LATITUDE_REF,
                    ExifInterface.TAG_GPS_LONGITUDE,
                    ExifInterface.TAG_GPS_LONGITUDE_REF,
                    ExifInterface.TAG_GPS_ALTITUDE,
                    ExifInterface.TAG_GPS_ALTITUDE_REF,
                    ExifInterface.TAG_GPS_TIMESTAMP,
                    ExifInterface.TAG_GPS_DATESTAMP,
                    ExifInterface.TAG_GPS_PROCESSING_METHOD,
                    ExifInterface.TAG_GPS_DOP,
                    ExifInterface.TAG_GPS_MAP_DATUM,
                    ExifInterface.TAG_GPS_IMG_DIRECTION,
                    ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
                    ExifInterface.TAG_DATETIME_ORIGINAL,
                    ExifInterface.TAG_DATETIME,
                    ExifInterface.TAG_ORIENTATION,
                )
        }
    }

/** Lower-case hex SHA-256 of [file], read in blocks. */
internal fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private const val BUFFER_BYTES = 64 * 1024
