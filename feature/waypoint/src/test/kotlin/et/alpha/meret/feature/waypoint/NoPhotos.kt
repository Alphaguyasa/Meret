package et.alpha.meret.feature.waypoint

import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.domain.waypoint.PhotoStore
import et.alpha.meret.domain.waypoint.StoredPhoto

/** A photo store with nothing in it, for sheets whose forms take no photos. */
internal object NoPhotos : PhotoStore {
    override suspend fun store(capturePath: String): AppResult<StoredPhoto> =
        error("no photo is taken in these tests")

    override suspend fun describe(id: String): StoredPhoto? = null

    override suspend fun delete(id: String) = Unit
}
