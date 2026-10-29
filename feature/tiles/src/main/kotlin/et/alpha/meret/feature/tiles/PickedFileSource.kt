package et.alpha.meret.feature.tiles

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.io.InputStream

/** What the document picker says about a file, read before the copy starts. */
data class PickedFile(
    /** The name the picker showed, such as `ethiopia-2026.mbtiles`. */
    val displayName: String?,
    /** Null when the provider does not know; the free-space check is then skipped. */
    val sizeBytes: Long?,
)

/** Reads a file the user picked. [uri] is the picker's content URI, as a string. */
interface PickedFileSource {
    /** Blocking: a provider may be slow. Never throws; unknown facts are null. */
    fun describe(uri: String): PickedFile

    /** Blocking. Throws an [java.io.IOException] or [SecurityException] when it cannot. */
    fun open(uri: String): InputStream
}

/** The production [PickedFileSource], through the app's [ContentResolver]. */
class ContentPickedFileSource(
    private val resolver: ContentResolver,
) : PickedFileSource {
    override fun describe(uri: String): PickedFile = try {
        resolver
            .query(Uri.parse(uri), COLUMNS, null, null, null)
            ?.use { cursor -> cursor.takeIf { it.moveToFirst() }?.toPickedFile() }
            ?: UNKNOWN
    } catch (_: SecurityException) {
        // The grant is gone; open() fails the same way and the import reports it.
        UNKNOWN
    } catch (_: IllegalArgumentException) {
        // A provider that does not support these columns.
        UNKNOWN
    }

    override fun open(uri: String): InputStream =
        resolver.openInputStream(Uri.parse(uri)) ?: throw FileNotFoundException("no stream")

    private fun Cursor.toPickedFile(): PickedFile = PickedFile(
        displayName = column(OpenableColumns.DISPLAY_NAME)?.let(::getString),
        sizeBytes = column(OpenableColumns.SIZE)?.let(::getLong),
    )

    private fun Cursor.column(name: String): Int? =
        getColumnIndex(name).takeIf { it >= 0 && !isNull(it) }

    private companion object {
        val COLUMNS = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val UNKNOWN = PickedFile(displayName = null, sizeBytes = null)
    }
}
