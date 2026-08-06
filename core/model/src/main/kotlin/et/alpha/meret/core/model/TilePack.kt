package et.alpha.meret.core.model

import et.alpha.meret.core.model.geo.BoundingBox

public enum class TilePackSource(
    override val wireName: String,
) : WireNamed {
    /** Fetched over the network by the app. */
    DOWNLOADED("downloaded"),

    /** Imported from a file the user supplied — SD card, USB, another device. */
    SIDELOADED("sideloaded"),
}

/**
 * An installed offline basemap: one `.mbtiles` file plus the metadata needed to decide
 * whether it covers where the user is standing.
 *
 * [path] is a local filesystem path, never a URL. Sideloading is the primary distribution
 * route for the deployments this app targets, where a 400 MB download is not realistic.
 */
public data class TilePack(
    public val id: String,
    public val name: String,
    public val regionCode: String,
    public val path: String,
    public val bytes: Long,
    public val minZoom: Int,
    public val maxZoom: Int,
    public val bounds: BoundingBox,
    public val source: TilePackSource,
    public val installedAt: Long,
    public val styleId: String? = null,
)
