package et.alpha.meret.core.maps.style

import android.content.res.AssetManager
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.IOException

/**
 * Every glyph range and sprite file the bundled style can ask for is in the APK's assets.
 *
 * With no glyph server on the phone, a missing range means labels in that range silently
 * vanish, and a missing Ethiopic range means Amharic draws as boxes. MapLibre requests the
 * whole font stack joined by a comma (`Noto Sans Regular,Noto Sans Ethiopic Regular`), in
 * 256-codepoint ranges from `0-255` to `65280-65535`.
 */
@RunWith(RobolectricTestRunner::class)
class MapAssetsTest {
    private val assets: AssetManager = RuntimeEnvironment.getApplication().assets
    private val style =
        Json
            .parseToJsonElement(read(StyleProvider.TEMPLATE_ASSET).decodeToString())
            .jsonObject
    private val layouts =
        style
            .getValue("layers")
            .jsonArray
            .mapNotNull { it.jsonObject["layout"]?.jsonObject }

    /** As MapLibre requests them: the stack's font names joined by a comma, no space. */
    private val stacks =
        layouts
            .mapNotNull { it["text-font"] }
            .map { stack -> stack.jsonArray.joinToString(",") { it.jsonPrimitive.content } }
            .toSet()

    @Test
    fun `every range of every font stack the style uses is bundled`() {
        assertThat(stacks).isNotEmpty()
        val missing =
            stacks.flatMap { stack ->
                RANGES.map { glyphPath(stack, it) }.filterNot(::exists)
            }
        assertThat(missing).isEmpty()
    }

    @Test
    fun `the Ethiopic range holds real glyphs in every stack`() {
        stacks.forEach { stack ->
            // U+1200–U+137F. An empty range is a few bytes; real Ethiopic is ~130 KB.
            assertThat(read(glyphPath(stack, "4608-4863")).size).isGreaterThan(MIN_GLYPH_BYTES)
            assertThat(read(glyphPath(stack, "0-255")).size).isGreaterThan(MIN_GLYPH_BYTES)
        }
    }

    @Test
    fun `the sprite is bundled at 1x and 2x with every icon the style uses`() {
        val used = layouts.mapNotNull { it["icon-image"] }.flatMap(::iconNames).toSet()
        val base = assetPath("sprite")

        assertThat(used).isNotEmpty()
        listOf("", "@2x").forEach { ratio ->
            assertThat(exists("$base$ratio.png")).isTrue()
            val index = Json.parseToJsonElement(read("$base$ratio.json").decodeToString())
            assertThat(index.jsonObject.keys).containsAtLeastElementsIn(used)
        }
    }

    @Test
    fun `the font licence ships with the glyphs`() {
        val glyphRoot = assetPath("glyphs").substringBefore("/{fontstack}")

        assertThat(read("$glyphRoot/OFL.txt").decodeToString()).contains("SIL Open Font License")
    }

    private fun glyphPath(
        stack: String,
        range: String,
    ): String = assetPath("glyphs").replace("{fontstack}", stack).replace("{range}", range)

    /** A style URL such as `asset://map/sprite/sprite`, as a path inside the assets. */
    private fun assetPath(key: String): String = style
        .getValue(key)
        .jsonPrimitive
        .content
        .removePrefix(ASSET_SCHEME)

    private fun exists(path: String): Boolean = try {
        assets.open(path).close()
        true
    } catch (_: IOException) {
        false
    }

    private fun read(path: String): ByteArray = assets.open(path).use { it.readBytes() }

    /** A literal name, or the outputs of `[match, input, label, out, ..., fallback]`. */
    private fun iconNames(value: JsonElement): List<String> = when (value) {
        is JsonPrimitive -> listOf(value.content)
        is JsonArray -> value.drop(2).chunked(2).map { it.last().jsonPrimitive.content }
        is JsonObject -> error("unsupported icon-image: $value")
    }

    private companion object {
        const val ASSET_SCHEME = "asset://"
        const val MIN_GLYPH_BYTES = 1000
        val RANGES = (0 until 256).map { "${it * 256}-${it * 256 + 255}" }
    }
}
