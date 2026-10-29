package et.alpha.meret.feature.tiles

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.data.tilepack.TilePackRepository
import et.alpha.meret.core.model.TilePack
import et.alpha.meret.core.model.TilePackSource
import et.alpha.meret.core.model.geo.BoundingBox
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.InputStream

@OptIn(ExperimentalCoroutinesApi::class)
class TilePacksViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeRepository()
    private val files = FakeFiles()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = TilePacksViewModel(repository, files, TestDispatchers(dispatcher))

    @Test
    fun `opening the screen sweeps pack files with no row`() {
        viewModel()

        assertThat(repository.sweeps).isEqualTo(1)
    }

    @Test
    fun `the installed packs are listed in the repository's order`() = runTest {
        repository.packs.value = listOf(pack("newer", "Addis"), pack("older", ""))

        viewModel().uiState.test {
            val ready = awaitReady()
            assertThat(ready.packs)
                .containsExactly(
                    TilePackItem("newer", "Addis", PACK_BYTES),
                    TilePackItem("older", "", PACK_BYTES),
                ).inOrder()
            assertThat(ready.importing).isFalse()
            assertThat(ready.message).isNull()
        }
    }

    @Test
    fun `an import passes the picked file's name, size and stream, then says it worked`() =
        runTest {
            val viewModel = viewModel()
            viewModel.uiState.test {
                awaitReady()

                viewModel.importPack(PICKED)

                val done = expectMostRecentItem() as TilePacksUiState.Ready
                assertThat(done.message).isEqualTo(PackMessage.Imported("ethiopia-2026"))
                assertThat(done.importing).isFalse()
            }
            assertThat(repository.imports).containsExactly("ethiopia-2026.mbtiles" to 1_000L)
            assertThat(files.opened).containsExactly(PICKED)
        }

    @Test
    fun `the button waits while a pack copies, and a second pick is ignored`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitReady()

            viewModel.importPack(PICKED)
            assertThat((expectMostRecentItem() as TilePacksUiState.Ready).importing).isTrue()
            viewModel.importPack(PICKED)
            gate.complete(Unit)

            assertThat((expectMostRecentItem() as TilePacksUiState.Ready).importing).isFalse()
        }
        assertThat(repository.imports).hasSize(1)
    }

    @Test
    fun `each storage failure becomes something the user can act on`() = runTest {
        val expected =
            mapOf(
                AppError.Storage.Kind.Corrupt to ImportProblem.NotAPack,
                AppError.Storage.Kind.OutOfSpace to ImportProblem.OutOfSpace,
                AppError.Storage.Kind.ReadFailed to ImportProblem.ReadFailed,
                AppError.Storage.Kind.NotFound to ImportProblem.ReadFailed,
                AppError.Storage.Kind.WriteFailed to ImportProblem.WriteFailed,
            )
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitReady()
            expected.forEach { (kind, problem) ->
                repository.importResult = AppResult.Failure(AppError.Storage(kind))

                viewModel.importPack(PICKED)

                val ready = expectMostRecentItem() as TilePacksUiState.Ready
                assertThat(ready.message).isEqualTo(PackMessage.ImportFailed(problem))
            }
        }
    }

    @Test
    fun `a failed delete says so, a dismissed message goes away`() = runTest {
        repository.deleteResult =
            AppResult.Failure(AppError.Storage(AppError.Storage.Kind.WriteFailed))
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitReady()

            viewModel.deletePack("a")
            assertThat((expectMostRecentItem() as TilePacksUiState.Ready).message)
                .isEqualTo(PackMessage.DeleteFailed)
            viewModel.dismissMessage()
            assertThat((expectMostRecentItem() as TilePacksUiState.Ready).message).isNull()
        }
        assertThat(repository.deleted).containsExactly("a")
    }

    @Test
    fun `a delete that works shows no message`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitReady()

            viewModel.deletePack("a")

            expectNoEvents()
        }
        assertThat(repository.deleted).containsExactly("a")
    }

    /** The first [TilePacksUiState.Ready], whether or not `Loading` was seen first. */
    private suspend fun ReceiveTurbine<TilePacksUiState>.awaitReady(): TilePacksUiState.Ready {
        var item = awaitItem()
        while (item !is TilePacksUiState.Ready) item = awaitItem()
        return item
    }

    private class FakeRepository : TilePackRepository {
        val packs = MutableStateFlow<List<TilePack>>(emptyList())
        val imports = mutableListOf<Pair<String?, Long?>>()
        val deleted = mutableListOf<String>()
        var sweeps = 0
        var gate: CompletableDeferred<Unit>? = null
        var importResult: AppResult<TilePack>? = null
        var deleteResult: AppResult<Unit> = AppResult.Success(Unit)

        override fun observeTilePacks(): Flow<List<TilePack>> = packs

        override suspend fun importPack(
            displayName: String?,
            sizeBytes: Long?,
            open: () -> InputStream,
        ): AppResult<TilePack> {
            imports += displayName to sizeBytes
            open().close()
            gate?.await()
            return importResult
                ?: AppResult.Success(pack("new", displayName.orEmpty().removeSuffix(".mbtiles")))
        }

        override suspend fun deletePack(packId: String): AppResult<Unit> {
            deleted += packId
            return deleteResult
        }

        override suspend fun deleteUnregisteredPacks(): Int {
            sweeps++
            return 0
        }
    }

    private class FakeFiles : PickedFileSource {
        val opened = mutableListOf<String>()

        override fun describe(uri: String): PickedFile =
            PickedFile(displayName = "ethiopia-2026.mbtiles", sizeBytes = 1_000L)

        override fun open(uri: String): InputStream {
            opened += uri
            return "pack".byteInputStream()
        }
    }

    private class TestDispatchers(
        dispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val default: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val main: CoroutineDispatcher = dispatcher
        override val mainImmediate: CoroutineDispatcher = dispatcher
    }

    private companion object {
        const val PICKED = "content://com.android.externalstorage.documents/document/1"
        const val PACK_BYTES = 412_000_000L

        fun pack(
            id: String,
            name: String,
        ) = TilePack(
            id = id,
            name = name,
            regionCode = "",
            path = "/data/packs/$id.mbtiles",
            bytes = PACK_BYTES,
            minZoom = 0,
            maxZoom = 14,
            bounds = BoundingBox(3.4, 33.0, 14.9, 48.0),
            source = TilePackSource.SIDELOADED,
            installedAt = 1_760_000_000_000L,
            styleId = "meret",
        )
    }
}
