package et.alpha.meret.core.data.tilepack

import android.database.SQLException
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.common.time.TimeProvider
import et.alpha.meret.core.common.uuid.UuidV7Generator
import et.alpha.meret.core.database.dao.TilePackDao
import et.alpha.meret.core.database.entity.TilePackEntity
import et.alpha.meret.core.maps.mbtiles.InvalidMbtilesException
import et.alpha.meret.core.maps.mbtiles.MbtilesMetadata
import et.alpha.meret.core.maps.mbtiles.TileBounds
import et.alpha.meret.core.maps.server.InstalledPack
import et.alpha.meret.core.maps.server.InsufficientSpaceException
import et.alpha.meret.core.maps.server.TilePackInstaller
import et.alpha.meret.core.model.TilePack
import et.alpha.meret.core.model.TilePackSource
import et.alpha.meret.core.model.geo.BoundingBox
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

class TilePackRepositoryTest {
    private val dao = FakeTilePackDao()
    private val installer = FakeInstaller()
    private val repository =
        DefaultTilePackRepository(
            dao = dao,
            installer = installer,
            ids = UuidV7Generator(FixedTime),
            time = FixedTime,
            dispatchers = TestDispatchers(UnconfinedTestDispatcher()),
        )

    @Test
    fun `a row becomes the domain pack, extent included`() = runTest {
        dao.rows.value = listOf(ETHIOPIA)

        val pack = repository.observeTilePacks().first().single()

        assertThat(pack).isEqualTo(
            TilePack(
                id = "ethiopia",
                name = "Ethiopia",
                regionCode = "ET",
                path = "/data/packs/ethiopia.mbtiles",
                bytes = 412_000_000L,
                minZoom = 0,
                maxZoom = 14,
                bounds = BoundingBox(3.4, 33.0, 14.9, 48.0),
                source = TilePackSource.SIDELOADED,
                installedAt = 1_760_000_000_000L,
                styleId = "meret",
            ),
        )
    }

    @Test
    fun `the order of the table is kept`() = runTest {
        dao.rows.value = listOf(ETHIOPIA.copy(id = "newer"), ETHIOPIA)

        val ids = repository.observeTilePacks().first().map { it.id }

        assertThat(ids).containsExactly("newer", "ethiopia").inOrder()
    }

    @Test
    fun `no rows, no packs`() = runTest {
        assertThat(repository.observeTilePacks().first()).isEmpty()
    }

    @Test
    fun `an imported pack gets a row the map can match to the user`() = runTest {
        val result = repository.importPack("ethiopia-2026.mbtiles", 1_000L) { stream() }

        val pack = (result as AppResult.Success).value
        assertThat(pack.name).isEqualTo("ethiopia-2026")
        assertThat(pack.source).isEqualTo(TilePackSource.SIDELOADED)
        assertThat(pack.installedAt).isEqualTo(FixedTime.nowMillis())
        assertThat(pack.bounds).isEqualTo(BoundingBox(3.40, 32.99, 14.89, 47.99))
        assertThat(pack.minZoom to pack.maxZoom).isEqualTo(0 to 14)
        assertThat(pack.path).endsWith("${pack.id}.mbtiles")
        assertThat(pack.bytes).isEqualTo(INSTALLED_BYTES)
        assertThat(installer.installs).containsExactly(pack.id to 1_000L)
        assertThat(repository.observeTilePacks().first()).containsExactly(pack)
    }

    @Test
    fun `every import gets a new id`() = runTest {
        val first = repository.importPack("a.mbtiles", null) { stream() } as AppResult.Success
        val second = repository.importPack("a.mbtiles", null) { stream() } as AppResult.Success

        assertThat(first.value.id).isNotEqualTo(second.value.id)
        assertThat(dao.rows.value).hasSize(2)
    }

    @Test
    fun `the name falls back to the pack's own, then to blank`() = runTest {
        val fromMetadata = repository.importPack(null, null) { stream() } as AppResult.Success
        installer.metadataName = null
        val blank = repository.importPack(" .MBTiles", null) { stream() } as AppResult.Success

        assertThat(fromMetadata.value.name).isEqualTo("OpenMapTiles")
        assertThat(blank.value.name).isEmpty()
    }

    @Test
    fun `a file that is not a vector pack is Corrupt, and writes no row`() = runTest {
        installer.failure = InvalidMbtilesException("format is not pbf")

        val result = repository.importPack("photo.mbtiles", null) { stream() }

        assertThat(result.storageKind()).isEqualTo(AppError.Storage.Kind.Corrupt)
        assertThat(dao.rows.value).isEmpty()
    }

    @Test
    fun `a pack too big for the phone is OutOfSpace`() = runTest {
        installer.failure = InsufficientSpaceException(neededBytes = 2, availableBytes = 1)

        val result = repository.importPack("big.mbtiles", 2) { stream() }

        assertThat(result.storageKind()).isEqualTo(AppError.Storage.Kind.OutOfSpace)
        assertThat(dao.rows.value).isEmpty()
    }

    @Test
    fun `a failed copy is WriteFailed`() = runTest {
        installer.failure = IOException("disk error")

        val result = repository.importPack("a.mbtiles", null) { stream() }

        assertThat(result.storageKind()).isEqualTo(AppError.Storage.Kind.WriteFailed)
    }

    @Test
    fun `a file that cannot be opened is ReadFailed, and nothing is installed`() = runTest {
        val missing = repository.importPack("a.mbtiles", null) { throw FileNotFoundException() }
        val revoked = repository.importPack("a.mbtiles", null) { throw SecurityException() }

        assertThat(missing.storageKind()).isEqualTo(AppError.Storage.Kind.ReadFailed)
        assertThat(revoked.storageKind()).isEqualTo(AppError.Storage.Kind.ReadFailed)
        assertThat(installer.installs).isEmpty()
    }

    @Test
    fun `when the row cannot be written the file is removed too`() = runTest {
        dao.failWrites = true

        val result = repository.importPack("a.mbtiles", null) { stream() }

        assertThat(result.storageKind()).isEqualTo(AppError.Storage.Kind.WriteFailed)
        val installedId = installer.installs.single().first
        assertThat(installer.uninstalled).containsExactly(installedId)
    }

    private fun stream(): InputStream = "pack".byteInputStream()

    private fun AppResult<*>.storageKind(): AppError.Storage.Kind? =
        ((this as? AppResult.Failure)?.error as? AppError.Storage)?.kind

    private class FakeInstaller : TilePackInstaller {
        val installs = mutableListOf<Pair<String, Long?>>()
        val uninstalled = mutableListOf<String>()
        var failure: Exception? = null
        var metadataName: String? = "OpenMapTiles"

        override fun install(
            packId: String,
            source: InputStream,
            sizeBytes: Long?,
        ): InstalledPack {
            source.close()
            failure?.let { throw it }
            installs += packId to sizeBytes
            return InstalledPack(
                file = File("/data/packs/$packId.mbtiles"),
                bytes = INSTALLED_BYTES,
                metadata =
                    MbtilesMetadata(
                        name = metadataName,
                        format = "pbf",
                        minZoom = 0,
                        maxZoom = 14,
                        bounds = null,
                        center = null,
                        attribution = null,
                        json = null,
                    ),
                bounds = TileBounds(west = 32.99, south = 3.40, east = 47.99, north = 14.89),
            )
        }

        override fun uninstall(packId: String) {
            uninstalled += packId
        }
    }

    private object FixedTime : TimeProvider {
        override fun nowMillis(): Long = 1_761_000_000_000L

        override fun elapsedRealtimeNanos(): Long = 0L
    }

    private class TestDispatchers(
        dispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val default: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val main: CoroutineDispatcher = dispatcher
        override val mainImmediate: CoroutineDispatcher = dispatcher
    }

    private class FakeTilePackDao : TilePackDao {
        val rows = MutableStateFlow<List<TilePackEntity>>(emptyList())

        override fun observeTilePacks(): Flow<List<TilePackEntity>> = rows

        override suspend fun getTilePack(tilePackId: String): TilePackEntity? =
            rows.value.firstOrNull { it.id == tilePackId }

        override suspend fun getTilePacksCovering(
            latitude: Double,
            longitude: Double,
        ): List<TilePackEntity> = error("not used")

        var failWrites = false

        override suspend fun upsertTilePack(tilePack: TilePackEntity) {
            if (failWrites) throw SQLException("disk I/O error")
            rows.value = listOf(tilePack) + rows.value.filterNot { it.id == tilePack.id }
        }

        override suspend fun deleteTilePack(tilePackId: String): Unit = error("not used")
    }

    private companion object {
        const val INSTALLED_BYTES = 412_000_000L

        val ETHIOPIA =
            TilePackEntity(
                id = "ethiopia",
                name = "Ethiopia",
                regionCode = "ET",
                path = "/data/packs/ethiopia.mbtiles",
                bytes = 412_000_000L,
                minZoom = 0,
                maxZoom = 14,
                bboxMinLatitude = 3.4,
                bboxMinLongitude = 33.0,
                bboxMaxLatitude = 14.9,
                bboxMaxLongitude = 48.0,
                source = TilePackSource.SIDELOADED,
                installedAt = 1_760_000_000_000L,
                styleId = "meret",
            )
    }
}
