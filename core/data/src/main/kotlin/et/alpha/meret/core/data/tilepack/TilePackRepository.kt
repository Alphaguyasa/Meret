package et.alpha.meret.core.data.tilepack

import et.alpha.meret.core.database.dao.TilePackDao
import et.alpha.meret.core.database.entity.TilePackEntity
import et.alpha.meret.core.model.TilePack
import et.alpha.meret.core.model.geo.BoundingBox
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** The installed offline basemaps. Rows are written by the tile pack import (6g). */
public interface TilePackRepository {
    /** Every installed pack, newest first. */
    public fun observeTilePacks(): Flow<List<TilePack>>
}

public class DefaultTilePackRepository
    @Inject
    constructor(
        private val dao: TilePackDao,
    ) : TilePackRepository {
        override fun observeTilePacks(): Flow<List<TilePack>> =
            dao.observeTilePacks().map { packs -> packs.map { it.toModel() } }
    }

internal fun TilePackEntity.toModel(): TilePack = TilePack(
    id = id,
    name = name,
    regionCode = regionCode,
    path = path,
    bytes = bytes,
    minZoom = minZoom,
    maxZoom = maxZoom,
    bounds =
        BoundingBox(
            minLatitude = bboxMinLatitude,
            minLongitude = bboxMinLongitude,
            maxLatitude = bboxMaxLatitude,
            maxLongitude = bboxMaxLongitude,
        ),
    source = source,
    installedAt = installedAt,
    styleId = styleId,
)
