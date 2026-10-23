package et.alpha.meret.core.maps.style

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StyleProviderTest {
    @get:Rule val temp = TemporaryFolder()

    private val template =
        File(requireNotNull(System.getProperty("meret.styleDir")), "meret-style.json").readText()

    @Test
    fun `the real template gets the loopback source and keeps assets on asset scheme`() {
        val style = Json.parseToJsonElement(StyleProvider.render(template, URL)).jsonObject

        val source =
            style
                .getValue("sources")
                .jsonObject
                .getValue("openmaptiles")
                .jsonObject
        assertThat(source.getValue("url").jsonPrimitive.content).isEqualTo(URL)
        assertThat(style.getValue("glyphs").jsonPrimitive.content).startsWith("asset://")
        assertThat(style.getValue("sprite").jsonPrimitive.content).startsWith("asset://")
    }

    @Test
    fun `a url off loopback is refused`() {
        listOf(
            "https://tiles.example.org/tiles.json",
            "http://localhost:4040/pack/tiles.json",
            "http://127.0.0.1.example.org:80/pack/tiles.json",
            "http://10.0.0.2:4040/pack/tiles.json",
            "http://127.0.0.1:4040/pack\"/tiles.json",
        ).forEach { url ->
            assertThrows(
                IllegalArgumentException::class.java,
            ) { StyleProvider.render(template, url) }
        }
    }

    @Test
    fun `a template without exactly one placeholder is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            StyleProvider.render("""{"sources":{}}""", URL)
        }
        assertThrows(IllegalArgumentException::class.java) {
            StyleProvider.render("""{"a":"{{TILE_SERVER}}","b":"{{TILE_SERVER}}"}""", URL)
        }
    }

    @Test
    fun `write puts the style in the cache and replaces it on the next start`() = runTest {
        val provider =
            StyleProvider(
                openTemplate = { template.byteInputStream() },
                cacheDir = temp.root,
                io = StandardTestDispatcher(testScheduler),
            )

        val first = provider.write(URL)
        val second = provider.write("http://127.0.0.1:5151/pack/tiles.json")

        assertThat(second).isEqualTo(first)
        assertThat(second).startsWith("file:///")
        val file = File(second.removePrefix("file://"))
        assertThat(file.readText()).contains("http://127.0.0.1:5151/pack/tiles.json")
        assertThat(file.readText()).doesNotContain(URL)
        assertThat(file.parentFile!!.list()).asList().containsExactly("style.json")
    }

    private companion object {
        const val URL = "http://127.0.0.1:4040/ethiopia/tiles.json"
    }
}
