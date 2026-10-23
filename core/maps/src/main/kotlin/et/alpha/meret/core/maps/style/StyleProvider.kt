package et.alpha.meret.core.maps.style

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Turns the bundled style template into a style MapLibre can load offline.
 *
 * The template (`assets/map/style.json`, copied from `tools/style/meret-style.json` at
 * build time) names its vector source `{{TILE_SERVER}}`. The loopback port changes on every
 * start, so the source URL is substituted here, and the result is written to [cacheDir] for
 * MapLibre to open as a `file://` URI. Glyphs and sprite stay on `asset://`.
 *
 * @param openTemplate opens the template; in the app, `assets.open(TEMPLATE_ASSET)`.
 */
public class StyleProvider(
    private val openTemplate: () -> InputStream,
    private val cacheDir: File,
    private val io: CoroutineDispatcher,
) {
    /**
     * Writes the style for [tileJsonUrl] (`LocalTileServer.baseUrl(packId) + "/tiles.json"`)
     * and returns its `file://` URI, ready for `Style.Builder().fromUri()`. A reader never
     * sees a half-written style: the file is written beside the target and renamed over it.
     */
    public suspend fun write(tileJsonUrl: String): String = withContext(io) {
        val template = openTemplate().use { it.readBytes().decodeToString() }
        val style = render(template, tileJsonUrl)
        val dir = File(cacheDir, CACHE_DIR).apply { mkdirs() }
        val target = File(dir, STYLE_FILE)
        val partial = File(dir, "$STYLE_FILE.partial")
        partial.writeText(style)
        if (!partial.renameTo(target)) {
            partial.delete()
            throw IOException("could not replace the map style")
        }
        "file://${target.absolutePath}"
    }

    public companion object {
        /** Where the build puts the template, relative to the assets root. */
        public const val TEMPLATE_ASSET: String = "map/style.json"

        /** The source URL as a whole JSON string; the style's notes mention it in prose. */
        private const val PLACEHOLDER = "\"{{TILE_SERVER}}\""
        private const val CACHE_DIR = "map"
        private const val STYLE_FILE = "style.json"
        private val LOOPBACK_URL = Regex("""^http://127\.0\.0\.1:\d{1,5}/[^\s"\\]+$""")

        /**
         * Substitutes [tileJsonUrl] for the placeholder. Refuses a URL off loopback, because
         * the network security config allows cleartext to 127.0.0.1 only and the map must
         * never reach the network. Refuses a template without exactly one
         * `"{{TILE_SERVER}}"` value.
         */
        internal fun render(
            template: String,
            tileJsonUrl: String,
        ): String {
            require(LOOPBACK_URL.matches(tileJsonUrl)) { "tile source must be on 127.0.0.1" }
            val count = template.split(PLACEHOLDER).size - 1
            require(count == 1) { "style template must name the tile server once, found $count" }
            return template.replace(PLACEHOLDER, "\"$tileJsonUrl\"")
        }
    }
}
