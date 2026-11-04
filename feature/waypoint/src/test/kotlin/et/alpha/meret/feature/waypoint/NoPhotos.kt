package et.alpha.meret.feature.waypoint

import et.alpha.meret.core.common.result.AppError
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

/**
 * Photos in memory: a capture at a path becomes `photo-<n>`, stored at `/photos/photo-<n>.jpg`.
 * [failure] makes the next stores fail, as an unreadable capture or a full phone would.
 */
internal class FakePhotoStore : PhotoStore {
    val photos = linkedMapOf<String, StoredPhoto>()
    val captures = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    var failure: AppResult.Failure? = null

    /** Called on every delete, before the file goes: what the draft named at that moment. */
    var onDelete: (String) -> Unit = {}

    override suspend fun store(capturePath: String): AppResult<StoredPhoto> {
        captures += capturePath
        failure?.let { return it }
        return AppResult.Success(add("photo-${photos.size + 1}"))
    }

    fun add(id: String): StoredPhoto {
        val photo =
            StoredPhoto(
                id = id,
                path = "/photos/$id.jpg",
                width = 1600,
                height = 1200,
                bytes = 300_000,
                sha256 = "sha-$id",
                capturedAt = 1_000,
            )
        photos[id] = photo
        return photo
    }

    override suspend fun describe(id: String): StoredPhoto? = photos[id]

    override suspend fun delete(id: String) {
        onDelete(id)
        deleted += id
        photos -= id
    }

    companion object {
        val Full: AppResult.Failure =
            AppResult.Failure(AppError.Storage(AppError.Storage.Kind.OutOfSpace))
    }
}
