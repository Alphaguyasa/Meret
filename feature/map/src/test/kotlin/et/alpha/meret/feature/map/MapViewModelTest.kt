package et.alpha.meret.feature.map

import androidx.lifecycle.viewModelScope
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.data.tilepack.TilePackRepository
import et.alpha.meret.core.maps.OfflineMapSession
import et.alpha.meret.core.maps.server.LocalTileServer
import et.alpha.meret.core.maps.style.StyleProvider
import et.alpha.meret.core.model.TilePack
import et.alpha.meret.core.model.TilePackSource
import et.alpha.meret.core.model.geo.BoundingBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val packs = MutableStateFlow<List<TilePack>>(emptyList())
    private val repository =
        object : TilePackRepository {
            override fun observeTilePacks(): Flow<List<TilePack>> = packs
        }

    // Never acquired here: the map view does that, and these tests draw no map.
    private val session =
        OfflineMapSession(
            server = LocalTileServer { null },
            styles = StyleProvider({ error("no style in this test") }, File("unused"), dispatcher),
            io = dispatcher,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `with no pack installed, the placeholder shows`() = runMapTest { viewModel ->
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.basemapPackId).isNull()
            assertThat(state.basemapInstalled).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the newest installed pack is drawn`() = runMapTest { viewModel ->
        packs.value = listOf(pack("addis-2026"), pack("ethiopia"))

        viewModel.uiState.test {
            val state = awaitPack("addis-2026")
            assertThat(state.basemapInstalled).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `installing and removing a pack reach the screen`() = runMapTest { viewModel ->
        viewModel.uiState.test {
            awaitPack(null)

            packs.value = listOf(pack("ethiopia"))
            awaitPack("ethiopia")

            packs.value = emptyList()
            awaitPack(null)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The first item may be the initial state or the first pack list, depending on when the
     * upstream starts; skip until [packId] is drawn. Times out if it never is.
     */
    private suspend fun ReceiveTurbine<MapUiState>.awaitPack(packId: String?): MapUiState {
        var state = awaitItem()
        while (state.basemapPackId != packId) state = awaitItem()
        return state
    }

    private fun runMapTest(body: suspend TestScope.(MapViewModel) -> Unit) = runTest(dispatcher) {
        val viewModel = MapViewModel(repository, session)
        try {
            body(viewModel)
        } finally {
            viewModel.viewModelScope.coroutineContext.job
                .cancelAndJoin()
        }
    }

    private fun pack(id: String) = TilePack(
        id = id,
        name = id,
        regionCode = "ET",
        path = "/packs/$id.mbtiles",
        bytes = 1L,
        minZoom = 0,
        maxZoom = 14,
        bounds = BoundingBox(3.4, 33.0, 14.9, 48.0),
        source = TilePackSource.SIDELOADED,
        installedAt = 0L,
    )
}
