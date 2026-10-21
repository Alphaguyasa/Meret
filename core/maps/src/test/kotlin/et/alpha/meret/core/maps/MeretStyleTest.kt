package et.alpha.meret.core.maps

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
import java.io.File

/**
 * Guards `tools/style/meret-style.json`, the style the app will load offline.
 *
 * The failures this catches all look fine on a desk with Wi-Fi and break in the field: a
 * glyph or sprite URL that reaches the network, a font stack that `build-glyphs.sh` cannot
 * produce (labels vanish), a stack without the Ethiopic font (Amharic renders as boxes), and
 * an icon that is not in the sprite.
 */
class MeretStyleTest {
    private val styleDir = File(requireNotNull(System.getProperty("meret.styleDir")))
    private val style = readJson("meret-style.json")
    private val layers = style.getValue("layers").jsonArray.map { it.jsonObject }

    @Test
    fun `glyphs and sprite load from bundled assets`() {
        assertThat(style.string("glyphs")).isEqualTo("asset://map/fonts/{fontstack}/{range}.pbf")
        assertThat(style.string("sprite")).isEqualTo("asset://map/sprite/sprite")
    }

    @Test
    fun `nothing in the style points at the network`() {
        val urls = strings(style).filter { "http" in it.lowercase() || "://" in it }

        assertThat(urls).containsExactly(
            "asset://map/fonts/{fontstack}/{range}.pbf",
            "asset://map/sprite/sprite",
        )
    }

    @Test
    fun `the vector source is the loopback tile server placeholder`() {
        val sources = style.getValue("sources").jsonObject

        assertThat(sources.keys).containsExactly("openmaptiles")
        val url = sources.getValue("openmaptiles").jsonObject.string("url")
        assertThat(url).isEqualTo("{{TILE_SERVER}}")
    }

    @Test
    fun `every font stack can be built from the fonts build-glyphs knows`() {
        val known = readJson("fonts.json").keys

        assertThat(fontStacks()).isNotEmpty()
        fontStacks().forEach { stack -> assertThat(known).containsAtLeastElementsIn(stack) }
    }

    @Test
    fun `every font stack falls back to Ethiopic so Amharic never renders as boxes`() {
        fontStacks().forEach { stack -> assertThat(stack).contains("Noto Sans Ethiopic Regular") }
    }

    @Test
    fun `every label prefers the Amharic name`() {
        val expected = Json.parseToJsonElement("""["coalesce",["get","name:am"],["get","name"]]""")
        val textFields = layers.mapNotNull { it.layout?.get("text-field") }

        assertThat(textFields).isNotEmpty()
        textFields.forEach { assertThat(it).isEqualTo(expected) }
    }

    @Test
    fun `every icon the style uses is in the sprite`() {
        val packed =
            File(styleDir, "sprite-icons.txt")
                .readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
        val used = layers.mapNotNull { it.layout?.get("icon-image") }.flatMap(::iconNames).toSet()

        assertThat(used).isNotEmpty()
        assertThat(packed).containsAtLeastElementsIn(used)
    }

    @Test
    fun `the documented insertion point is the first symbol layer`() {
        val documented = style.getValue("metadata").jsonObject.string("meret:first-symbol-layer")
        val firstSymbol = layers.indexOfFirst { it.string("type") == "symbol" }

        assertThat(layers[firstSymbol].string("id")).isEqualTo(documented)
        // Everything above it is a label, so app layers inserted below it never cover one.
        val typesFromThere = layers.drop(firstSymbol).map { it.string("type") }.toSet()
        assertThat(typesFromThere).containsExactly("symbol")
    }

    @Test
    fun `layer ids are unique`() {
        val ids = layers.map { it.string("id") }

        assertThat(ids).containsNoDuplicates()
    }

    private fun fontStacks(): Set<List<String>> = layers
        .mapNotNull { it.layout?.get("text-font") }
        .map { stack -> stack.jsonArray.map { it.jsonPrimitive.content } }
        .toSet()

    /** A literal name, or the outputs of `[match, input, label, out, ..., fallback]`. */
    private fun iconNames(value: JsonElement): List<String> = when (value) {
        is JsonPrimitive -> {
            listOf(value.content)
        }

        is JsonArray -> {
            require(value.first().jsonPrimitive.content == "match") {
                "unsupported icon-image expression: $value"
            }
            val outputs = value.drop(2)
            outputs.chunked(2).map { it.last() }.map { it.jsonPrimitive.content }
        }

        else -> {
            error("unsupported icon-image: $value")
        }
    }

    private fun strings(element: JsonElement): List<String> = when (element) {
        is JsonPrimitive -> if (element.isString) listOf(element.content) else emptyList()
        is JsonArray -> element.flatMap(::strings)
        is JsonObject -> element.keys.toList() + element.values.flatMap(::strings)
    }

    private fun readJson(name: String): JsonObject =
        Json.parseToJsonElement(File(styleDir, name).readText()).jsonObject

    private val JsonObject.layout: JsonObject? get() = get("layout")?.jsonObject

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
}
