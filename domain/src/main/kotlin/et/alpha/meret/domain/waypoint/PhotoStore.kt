package et.alpha.meret.domain.waypoint

import et.alpha.meret.core.common.result.AppResult

/** A waypoint photo as stored on the phone: shrunk, in app-private storage, checksummed. */
public data class StoredPhoto(
    /** A UUIDv7, also the file's name. */
    public val id: String,
    public val path: String,
    /** Of the stored file, in its pixels; the EXIF orientation says how to turn it. */
    public val width: Int,
    public val height: Int,
    public val bytes: Long,
    /** Lower-case hex of the stored file: the integrity check and the peer-sync dedup key. */
    public val sha256: String,
    public val capturedAt: Long,
    /** From the capture's GPS EXIF, if the camera wrote one. */
    public val latitude: Double? = null,
    public val longitude: Double? = null,
)

/** Turns a camera capture into a [StoredPhoto]. Never the network. */
public interface PhotoStore {
    /**
     * Shrinks the JPEG at [capturePath] to 1600 px on its long side at quality 80, keeping its
     * GPS, time and orientation EXIF, and stores it. The capture is deleted once the stored
     * file is complete; on any failure it is left where it is and nothing is stored.
     */
    public suspend fun store(capturePath: String): AppResult<StoredPhoto>
}
