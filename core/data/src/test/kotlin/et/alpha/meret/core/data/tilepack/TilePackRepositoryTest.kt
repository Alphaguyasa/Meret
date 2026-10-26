package et.alpha.meret.core.data.tilepack

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.database.dao.TilePackDao
import et.alpha.meret.core.database.entity.TilePackEntity
import et.alpha.meret.core.model.TilePack
import et.alpha.meret.core.model.TilePackSource
import et.alpha.meret.core.model.geo.BoundingBox
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TilePackRepositoryTest {
    private val dao = FakeTilePackDao()
    private val repository = DefaultTilePackRepository(dao)

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

    private class FakeTilePackDao : TilePackDao {
        val rows = MutableStateFlow<List<TilePackEntity>>(emptyList())

        override fun observeTilePacks(): Flow<List<TilePackEntity>> = rows

        override suspend fun getTilePack(tilePackId: String): TilePackEntity? =
            rows.value.firstOrNull { it.id == tilePackId }

        override suspend fun getTilePacksCovering(
            latitude: Double,
            longitude: Double,
        ): List<TilePackEntity> = error("not used")

        override suspend fun upsertTilePack(tilePack: TilePackEntity): Unit = error("not used")

        override suspend fun deleteTilePack(tilePackId: String): Unit = error("not used")
    }

    private companion object {
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
