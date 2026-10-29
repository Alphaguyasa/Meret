package et.alpha.meret.core.maps.server

import et.alpha.meret.core.maps.mbtiles.InvalidMbtilesException
import et.alpha.meret.core.maps.mbtiles.MbtilesMetadata
import et.alpha.meret.core.maps.mbtiles.MbtilesReader
import et.alpha.meret.core.maps.mbtiles.TileBounds
import et.alpha.meret.core.maps.mbtiles.TileSource
import java.io.File
import java.io.IOException
import java.io.InputStream

/** What an install wrote: the file, and the facts the `tile_pack` row needs. */
public data class InstalledPack(
    val file: File,
    val bytes: Long,
    val metadata: MbtilesMetadata,
    /** The pack's declared extent, or the whole Web Mercator world when it declares none. */
    val bounds: TileBounds,
)

/** The pack is larger than the free space in the pack directory. Nothing was written. */
public class InsufficientSpaceException(
    public val neededBytes: Long,
    public val availableBytes: Long,
) : IOException("pack needs $neededBytes bytes, $availableBytes free")

/** Copies a user's `.mbtiles` file into the app's pack directory, checked. */
public interface TilePackInstaller {
    /**
     * Copies [source] to `{directory}/{packId}.mbtiles`, the layout [PackDirectoryResolver]
     * reads, and returns what it found. Blocking: call it on the IO dispatcher. [source] is
     * read to its end and closed.
     *
     * Throws [InvalidMbtilesException] when the file is not a vector (`pbf`) MBTiles pack,
     * [InsufficientSpaceException] when [sizeBytes] is known and does not fit, and
     * [IOException] when the copy fails. In every failure nothing is left in the directory.
     */
    public fun install(
        packId: String,
        source: InputStream,
        sizeBytes: Long?,
    ): InstalledPack

    /** Deletes the pack's file. A missing file is not an error. */
    public fun uninstall(packId: String)

    /**
     * Deletes every pack file whose id is not in [registered], and any copy a crash left
     * behind. A crash between the rename and the `tile_pack` row leaves a pack nobody can see
     * or delete. Waits for a running install. Returns the number of packs deleted.
     */
    public fun deleteUnregistered(registered: Set<String>): Int
}

/**
 * The production [TilePackInstaller].
 *
 * ## Copy, check, then rename
 *
 * The copy goes to `{packId}.mbtiles.partial`, which the resolver never opens, and is checked
 * there: metadata readable, `format=pbf`, a `tiles` table. Only then is it renamed into place,
 * so a half-copied or rejected file is never served. A copy cut off by a crash leaves a
 * `.partial` file, which the next install deletes. Installs run one at a time, so that clean-up
 * cannot delete another install's copy.
 */
public class MbtilesPackInstaller(
    private val directory: File,
    private val openPack: (File) -> TileSource = MbtilesReader::open,
) : TilePackInstaller {
    private val lock = Any()

    override fun install(
        packId: String,
        source: InputStream,
        sizeBytes: Long?,
    ): InstalledPack = source.use {
        require(isValidPackId(packId)) { "not a pack id" }
        synchronized(lock) {
            directory.mkdirs()
            deleteLeftoverCopies()
            checkSpace(sizeBytes)
            val target = File(directory, packId + PackDirectoryResolver.EXTENSION)
            require(!target.exists()) { "a pack with this id is installed" }
            val partial = File(directory, packId + PackDirectoryResolver.EXTENSION + PARTIAL)
            try {
                partial.outputStream().use { out -> source.copyTo(out) }
                val (metadata, bounds) = validate(partial)
                if (!partial.renameTo(target)) throw IOException("could not move the pack")
                InstalledPack(target, target.length(), metadata, bounds)
            } finally {
                partial.delete()
            }
        }
    }

    override fun uninstall(packId: String) {
        require(isValidPackId(packId)) { "not a pack id" }
        File(directory, packId + PackDirectoryResolver.EXTENSION).delete()
    }

    override fun deleteUnregistered(registered: Set<String>): Int = synchronized(lock) {
        deleteLeftoverCopies()
        val orphans =
            directory
                .listFiles { file ->
                    val id = file.name.removeSuffix(PackDirectoryResolver.EXTENSION)
                    file.isFile &&
                        file.name.endsWith(PackDirectoryResolver.EXTENSION) &&
                        isValidPackId(id) &&
                        id !in registered
                }.orEmpty()
        orphans.count { it.delete() }
    }

    private fun deleteLeftoverCopies() {
        directory.listFiles { file -> file.name.endsWith(PARTIAL) }?.forEach { it.delete() }
    }

    private fun checkSpace(sizeBytes: Long?) {
        val available = directory.usableSpace
        if (sizeBytes != null && sizeBytes > available) {
            throw InsufficientSpaceException(neededBytes = sizeBytes, availableBytes = available)
        }
    }

    private fun validate(file: File): Pair<MbtilesMetadata, TileBounds> =
        openPack(file).use { pack -> validate(pack) }

    private fun validate(pack: TileSource): Pair<MbtilesMetadata, TileBounds> {
        val metadata = pack.readMetadata()
        if (!metadata.format.equals(VECTOR_FORMAT, ignoreCase = true)) {
            throw InvalidMbtilesException("format is not $VECTOR_FORMAT")
        }
        // Throws when the pack has no readable tiles table; a missing tile is fine.
        pack.readTile(metadata.minZoom, 0, 0)
        return metadata to (metadata.bounds ?: WORLD)
    }

    private companion object {
        const val PARTIAL = ".partial"

        /** The style's sources are vector; a raster pack would draw nothing. */
        const val VECTOR_FORMAT = "pbf"

        /** TileJSON's default extent, for a pack with no `bounds` row. */
        val WORLD = TileBounds(west = -180.0, south = -85.051129, east = 180.0, north = 85.051129)
    }
}
