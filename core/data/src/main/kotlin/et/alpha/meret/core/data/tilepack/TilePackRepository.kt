package et.alpha.meret.core.data.tilepack

import android.database.SQLException
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.common.time.TimeProvider
import et.alpha.meret.core.common.uuid.UuidV7Generator
import et.alpha.meret.core.database.dao.TilePackDao
import et.alpha.meret.core.database.entity.TilePackEntity
import et.alpha.meret.core.maps.mbtiles.InvalidMbtilesException
import et.alpha.meret.core.maps.server.InstalledPack
import et.alpha.meret.core.maps.server.InsufficientSpaceException
import et.alpha.meret.core.maps.server.TilePackInstaller
import et.alpha.meret.core.model.TilePack
import et.alpha.meret.core.model.TilePackSource
import et.alpha.meret.core.model.geo.BoundingBox
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

/** The installed offline basemaps. */
public interface TilePackRepository {
    /** Every installed pack, newest first. */
    public fun observeTilePacks(): Flow<List<TilePack>>

    /**
     * Installs a pack the user picked: copies it into the app's pack directory, checks it is
     * a vector MBTiles pack, and writes its `tile_pack` row, which the map then draws.
     *
     * [open] opens the user's file; it is called once, off the main thread. [displayName] is
     * the file's name as the picker shows it, used as the pack's name without `.mbtiles`.
     * [sizeBytes], when the picker knows it, is checked against the free space first.
     *
     * Failures are [AppError.Storage]: `Corrupt` for a file that is not a vector pack,
     * `OutOfSpace`, `ReadFailed` when the file cannot be opened, and `WriteFailed` otherwise.
     * A failed import leaves no file and no row.
     */
    public suspend fun importPack(
        displayName: String?,
        sizeBytes: Long?,
        open: () -> InputStream,
    ): AppResult<TilePack>

    /**
     * Removes an installed pack: its row first, so the map stops drawing it, then its file. A
     * file the delete leaves behind is removed by [deleteUnregisteredPacks].
     */
    public suspend fun deletePack(packId: String): AppResult<Unit>

    /**
     * Deletes pack files that have no row. A crash between an import's copy and its row
     * leaves one: the map never draws it and the pack list never shows it, so nothing else
     * would free its space. Waits for a running import. Returns how many were deleted.
     */
    public suspend fun deleteUnregisteredPacks(): Int
}

public class DefaultTilePackRepository
    @Inject
    constructor(
        private val dao: TilePackDao,
        private val installer: TilePackInstaller,
        private val ids: UuidV7Generator,
        private val time: TimeProvider,
        private val dispatchers: DispatcherProvider,
    ) : TilePackRepository {
        /**
         * Held from an import's copy to its row, and by the sweep: between the two, the
         * imported file has no row yet and the sweep would take it for a crash leftover.
         */
        private val importLock = Mutex()

        override fun observeTilePacks(): Flow<List<TilePack>> =
            dao.observeTilePacks().map { packs -> packs.map { it.toModel() } }

        override suspend fun importPack(
            displayName: String?,
            sizeBytes: Long?,
            open: () -> InputStream,
        ): AppResult<TilePack> = withContext(dispatchers.io) {
            importLock.withLock {
                val id = ids.generate()
                when (val copied = copy(id, sizeBytes, open)) {
                    is AppResult.Failure -> {
                        copied
                    }

                    // A copied pack gets its row even if the caller has gone: leaving the
                    // screen mid-import must not leave a file nobody can see.
                    is AppResult.Success -> {
                        withContext(NonCancellable) { register(id, displayName, copied.value) }
                    }
                }
            }
        }

        override suspend fun deletePack(packId: String): AppResult<Unit> =
            withContext(dispatchers.io) {
                val rowDeleted =
                    try {
                        dao.deleteTilePack(packId)
                        true
                    } catch (_: SQLException) {
                        false
                    }
                if (rowDeleted) {
                    installer.uninstall(packId)
                    AppResult.Success(Unit)
                } else {
                    AppResult.Failure(AppError.Storage(AppError.Storage.Kind.WriteFailed))
                }
            }

        override suspend fun deleteUnregisteredPacks(): Int = withContext(dispatchers.io) {
            importLock.withLock {
                val registered = dao.observeTilePacks().first().mapTo(HashSet()) { it.id }
                installer.deleteUnregistered(registered)
            }
        }

        private fun copy(
            id: String,
            sizeBytes: Long?,
            open: () -> InputStream,
        ): AppResult<InstalledPack> = when (val source = openSource(open)) {
            is AppResult.Failure -> source
            is AppResult.Success -> install(id, source.value, sizeBytes)
        }

        private fun openSource(open: () -> InputStream): AppResult<InputStream> = try {
            AppResult.Success(open())
        } catch (e: IOException) {
            storageFailure(AppError.Storage.Kind.ReadFailed, e)
        } catch (e: SecurityException) {
            // The picker's grant was revoked, or the provider refuses.
            storageFailure(AppError.Storage.Kind.ReadFailed, e)
        }

        private fun install(
            id: String,
            source: InputStream,
            sizeBytes: Long?,
        ): AppResult<InstalledPack> = try {
            AppResult.Success(installer.install(id, source, sizeBytes))
        } catch (e: InvalidMbtilesException) {
            storageFailure(AppError.Storage.Kind.Corrupt, e)
        } catch (e: InsufficientSpaceException) {
            storageFailure(AppError.Storage.Kind.OutOfSpace, e)
        } catch (e: IOException) {
            storageFailure(AppError.Storage.Kind.WriteFailed, e)
        }

        /** The row, or no file at all: a pack on disk with no row would never be drawn. */
        private suspend fun register(
            id: String,
            displayName: String?,
            installed: InstalledPack,
        ): AppResult<TilePack> {
            val entity = installed.toEntity(id, packName(displayName, installed), time.nowMillis())
            return try {
                dao.upsertTilePack(entity)
                AppResult.Success(entity.toModel())
            } catch (e: SQLException) {
                installer.uninstall(id)
                storageFailure(AppError.Storage.Kind.WriteFailed, e)
            }
        }
    }

/**
 * The file's name without its extension, which is what the user called it; else the pack's
 * own `name`; else blank, which the UI shows as a generic, translated name.
 */
private fun packName(
    displayName: String?,
    installed: InstalledPack,
): String = displayName
    ?.trim()
    ?.replace(MBTILES_EXTENSION, "")
    ?.takeIf { it.isNotBlank() }
    ?: installed.metadata.name
        ?.trim()
        .orEmpty()

private val MBTILES_EXTENSION = Regex("""\.mbtiles$""", RegexOption.IGNORE_CASE)

private fun InstalledPack.toEntity(
    id: String,
    name: String,
    installedAt: Long,
): TilePackEntity = TilePackEntity(
    id = id,
    name = name,
    // Sideloaded packs say nothing about their region; the extent is what the map matches.
    regionCode = "",
    path = file.absolutePath,
    bytes = bytes,
    minZoom = metadata.minZoom,
    maxZoom = metadata.maxZoom,
    bboxMinLatitude = bounds.south,
    bboxMinLongitude = bounds.west,
    bboxMaxLatitude = bounds.north,
    bboxMaxLongitude = bounds.east,
    source = TilePackSource.SIDELOADED,
    installedAt = installedAt,
)

private fun storageFailure(
    kind: AppError.Storage.Kind,
    cause: Throwable,
): AppResult.Failure = AppResult.Failure(AppError.Storage(kind, cause))

internal fun TilePackEntity.toModel(): TilePack = TilePack(
    id = id,
    name = name,
    regionCode = regionCode,
    path = path,
    bytes = bytes,
    minZoom = minZoom,
    maxZoom = maxZoom,
    bounds =
        BoundingBox(
            minLatitude = bboxMinLatitude,
            minLongitude = bboxMinLongitude,
            maxLatitude = bboxMaxLatitude,
            maxLongitude = bboxMaxLongitude,
        ),
    source = source,
    installedAt = installedAt,
    styleId = styleId,
)
