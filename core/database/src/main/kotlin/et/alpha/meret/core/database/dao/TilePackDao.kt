package et.alpha.meret.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import et.alpha.meret.core.database.entity.TilePackEntity
import kotlinx.coroutines.flow.Flow

@Dao
public interface TilePackDao {
    @Query("SELECT * FROM tile_pack ORDER BY installed_at DESC")
    public fun observeTilePacks(): Flow<List<TilePackEntity>>

    @Query("SELECT * FROM tile_pack WHERE id = :tilePackId")
    public suspend fun getTilePack(tilePackId: String): TilePackEntity?

    /**
     * Every installed pack whose extent contains a point, best zoom first — what the map
     * asks when it needs to know if it can draw where the user is standing.
     */
    @Query(
        """
        SELECT * FROM tile_pack
        WHERE :latitude BETWEEN bbox_min_lat AND bbox_max_lat
          AND :longitude BETWEEN bbox_min_lon AND bbox_max_lon
        ORDER BY max_zoom DESC
        """,
    )
    public suspend fun getTilePacksCovering(
        latitude: Double,
        longitude: Double,
    ): List<TilePackEntity>

    @Upsert
    public suspend fun upsertTilePack(tilePack: TilePackEntity)

    @Query("DELETE FROM tile_pack WHERE id = :tilePackId")
    public suspend fun deleteTilePack(tilePackId: String)
}
