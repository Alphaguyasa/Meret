package et.alpha.meret.core.maps.server

import et.alpha.meret.core.maps.mbtiles.InvalidMbtilesException
import et.alpha.meret.core.maps.mbtiles.MbtilesReader
import et.alpha.meret.core.maps.mbtiles.TileSource
import java.io.File

/**
 * Resolves a pack id to `{directory}/{packId}.mbtiles`, the layout the tile pack import
 * (6g) writes.
 *
 * The id reaches here from a request path, so it is checked again even though the server
 * already filters it: nothing outside [directory] is ever opened. A missing or corrupt file
 * resolves to null, which the server answers with 404, rather than an exception on the
 * server's worker thread.
 */
public class PackDirectoryResolver(
    private val directory: File,
    private val openPack: (File) -> TileSource = MbtilesReader::open,
) : TilePackResolver {
    override fun open(packId: String): TileSource? {
        val file = packId.takeIf(::isValidPackId)?.let { File(directory, it + EXTENSION) }
        val inside = file?.canonicalFile?.parentFile == directory.canonicalFile
        return if (file != null && inside && file.isFile) {
            try {
                openPack(file)
            } catch (_: InvalidMbtilesException) {
                null
            }
        } else {
            null
        }
    }

    public companion object {
        public const val EXTENSION: String = ".mbtiles"

        /** The app's pack directory, under `filesDir`. */
        public const val APP_DIRECTORY: String = "packs"
    }
}
