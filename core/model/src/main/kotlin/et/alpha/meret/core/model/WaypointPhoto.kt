package et.alpha.meret.core.model

import et.alpha.meret.core.model.geo.LatLng

public enum class PhotoUploadState(
    override val wireName: String,
) : WireNamed {
    PENDING("pending"),
    UPLOADING("uploading"),
    UPLOADED("uploaded"),
    FAILED("failed"),
}

/**
 * A photo attached to a waypoint.
 *
 * Photos sync on their own worker, constrained to unmetered networks, which is why they
 * carry [uploadState] rather than riding the waypoint's [SyncState]. A waypoint is
 * usefully synced long before its photos are.
 *
 * [width], [height] and [bytes] describe the compressed file on disk, not the capture.
 * [sha256] is both the integrity check and the peer-sync dedup key.
 */
public data class WaypointPhoto(
    public val id: String,
    public val waypointId: String,
    public val localPath: String,
    public val remotePath: String? = null,
    public val width: Int,
    public val height: Int,
    public val bytes: Long,
    public val sha256: String,
    public val capturedAt: Long,
    public val position: LatLng? = null,
    public val uploadState: PhotoUploadState = PhotoUploadState.PENDING,
    public val uploadAttempts: Int = 0,
)
