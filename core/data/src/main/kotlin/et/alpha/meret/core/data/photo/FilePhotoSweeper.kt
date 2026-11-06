package et.alpha.meret.core.data.photo

import android.content.Context
import android.database.SQLException
import dagger.hilt.android.qualifiers.ApplicationContext
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.time.TimeProvider
import et.alpha.meret.core.database.dao.WaypointDao
import et.alpha.meret.core.database.dao.WaypointDraftDao
import et.alpha.meret.core.database.dao.WaypointPhotoDao
import et.alpha.meret.domain.waypoint.PhotoSweeper
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * The [PhotoSweeper] for [JpegPhotoStore]'s `files/photos/` and the waypoint sheet's camera
 * captures in `cacheDir/photo_captures/`.
 *
 * In `photos/`, only files this store could have made are considered (`<uuid>.jpg` and the
 * `<uuid>.jpg.part` of a write that never finished); anything else is left alone. A `.jpg` is
 * kept when a `waypoint_photo` row has its id or its path, or when any draft's or saved
 * waypoint's answers contain its id. The text match is deliberately loose: a false "named"
 * keeps a file, a false "not named" would lose a record's photo.
 *
 * "Old" is a day by the wall clock, which can be wrong. A clock set back makes files look
 * newer and keeps them; only a clock jumping forward a day could make a just-taken photo look
 * old, and that photo is in the draft a moment after it is stored. If the database cannot be
 * read, nothing in `photos/` is deleted.
 */
public class FilePhotoSweeper
    @Inject
    internal constructor(
        @ApplicationContext context: Context,
        private val photoDao: WaypointPhotoDao,
        private val waypointDao: WaypointDao,
        private val draftDao: WaypointDraftDao,
        private val time: TimeProvider,
        private val dispatchers: DispatcherProvider,
    ) : PhotoSweeper {
        private val photos = File(context.filesDir, JpegPhotoStore.DIRECTORY)
        private val captures = File(context.cacheDir, CAPTURE_DIRECTORY)

        override suspend fun sweep(): Int = withContext(dispatchers.io) {
            val cutoff = time.nowMillis() - MIN_AGE_MILLIS
            sweepPhotos(cutoff) + sweepCaptures(cutoff)
        }

        private suspend fun sweepPhotos(cutoff: Long): Int {
            val old = photos.listFiles().orEmpty().filter { it.isOlderThan(cutoff) }
            val partial = old.count { it.name.isPartialWrite() && it.delete() }
            val stored = old.filter { it.name.isStoredPhoto() }
            val orphans = if (stored.isEmpty()) emptyList() else orphansAmong(stored)
            return partial + orphans.count { it.delete() }
        }

        /** The [stored] photos nothing names; none when the database cannot be read. */
        private suspend fun orphansAmong(stored: List<File>): List<File> = try {
            val rowIds = photoDao.getPhotoIds().toHashSet()
            val rowNames = photoDao.getPhotoLocalPaths().mapTo(HashSet()) { File(it).name }
            stored.filterNot { file ->
                val id = file.name.removeSuffix(JpegPhotoStore.SUFFIX)
                id in rowIds || file.name in rowNames || isInAnswers(id)
            }
        } catch (_: SQLException) {
            // Not knowing what the rows name, keep every photo: the next sheet tries again.
            emptyList()
        }

        private suspend fun isInAnswers(id: String): Boolean =
            draftDao.anyDraftAnswersContain(id) || waypointDao.anyWaypointAnswersContain(id)

        private fun sweepCaptures(cutoff: Long): Int =
            captures.listFiles().orEmpty().count { it.isOlderThan(cutoff) && it.delete() }

        private fun File.isOlderThan(cutoff: Long): Boolean = isFile && lastModified() <= cutoff

        private fun String.isStoredPhoto(): Boolean =
            endsWith(JpegPhotoStore.SUFFIX) &&
                JpegPhotoStore.ID_PATTERN.matches(removeSuffix(JpegPhotoStore.SUFFIX))

        private fun String.isPartialWrite(): Boolean =
            endsWith(JpegPhotoStore.TEMPORARY_SUFFIX) &&
                JpegPhotoStore.ID_PATTERN.matches(removeSuffix(JpegPhotoStore.TEMPORARY_SUFFIX))

        internal companion object {
            /** As in `feature/waypoint`'s `photo_capture_paths.xml` and `WaypointCaptureRoute`. */
            const val CAPTURE_DIRECTORY = "photo_captures"

            const val MIN_AGE_MILLIS: Long = 24L * 60 * 60 * 1000
        }
    }
