package et.alpha.meret.core.maps.view

import androidx.lifecycle.Lifecycle
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.maps.OfflineMapSession
import et.alpha.meret.core.maps.server.LocalTileServer
import et.alpha.meret.core.maps.style.StyleProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URI

/** The loopback port is open only while the map's screen is started. Real server, real style. */
@OptIn(ExperimentalCoroutinesApi::class)
class StyleHoldTest {
    @get:Rule val temp = TemporaryFolder()

    private val template =
        File(requireNotNull(System.getProperty("meret.styleDir")), "meret-style.json")
    private val server = LocalTileServer { null }
    private val owner = TestLifecycleOwner()
    private val styles = mutableListOf<String>()

    @After
    fun tearDown() {
        server.stop()
        Dispatchers.resetMain()
    }

    @Test
    fun `nothing is held before the screen starts`() = test { session ->
        owner.moveTo(Lifecycle.State.CREATED)
        hold(session)
        runCurrent()

        assertThat(server.port).isNull()
        assertThat(styles).isEmpty()
    }

    @Test
    fun `the server runs from start to stop`() = test { session ->
        owner.moveTo(Lifecycle.State.CREATED)
        hold(session)

        owner.moveTo(Lifecycle.State.RESUMED)
        runCurrent()
        assertThat(server.port).isNotNull()
        assertThat(styles).hasSize(1)

        owner.moveTo(Lifecycle.State.CREATED)
        runCurrent()
        assertThat(server.port).isNull()
    }

    @Test
    fun `each start applies a style for the new port`() = test { session ->
        owner.moveTo(Lifecycle.State.STARTED)
        hold(session)
        runCurrent()
        owner.moveTo(Lifecycle.State.CREATED)
        runCurrent()

        owner.moveTo(Lifecycle.State.STARTED)
        runCurrent()

        assertThat(styles).hasSize(2)
        val style = File(URI(styles.last())).readText()
        assertThat(style).contains("\"http://127.0.0.1:${server.port}/$PACK/tiles.json\"")
    }

    @Test
    fun `destroying the screen ends the hold`() = test { session ->
        owner.moveTo(Lifecycle.State.STARTED)
        val job = hold(session)
        runCurrent()

        owner.moveTo(Lifecycle.State.DESTROYED)
        runCurrent()

        assertThat(job.isCompleted).isTrue()
        assertThat(server.port).isNull()
    }

    /**
     * In [TestScope.backgroundScope], so a hold that never ends is cancelled after the test.
     * Background work is not counted by `advanceUntilIdle`, hence `runCurrent` throughout.
     */
    private fun TestScope.hold(session: OfflineMapSession) = backgroundScope.launch {
        holdStyleWhileStarted(owner.lifecycle, session, PACK) { styles += it }
    }

    private fun test(block: suspend TestScope.(OfflineMapSession) -> Unit) = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        // repeatOnLifecycle runs its block on Dispatchers.Main.
        Dispatchers.setMain(dispatcher)
        val styleProvider = StyleProvider({ template.inputStream() }, temp.newFolder(), dispatcher)
        block(OfflineMapSession(server, styleProvider, dispatcher))
    }

    private companion object {
        const val PACK = "ethiopia"
    }
}
