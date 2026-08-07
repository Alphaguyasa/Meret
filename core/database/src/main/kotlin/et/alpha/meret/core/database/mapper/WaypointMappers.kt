package et.alpha.meret.core.database.mapper

import et.alpha.meret.core.database.entity.WaypointEntity
import et.alpha.meret.core.database.entity.WaypointPhotoEntity
import et.alpha.meret.core.model.Waypoint
import et.alpha.meret.core.model.WaypointPhoto
import et.alpha.meret.core.model.geo.LatLng

public fun WaypointEntity.toDomain(): Waypoint = Waypoint(
    id = id,
    projectId = projectId,
    trackId = trackId,
    position = LatLng(latitude, longitude),
    accuracyMeters = accuracyMeters,
    altitudeMeters = altitudeMeters,
    recordedAt = recordedAt,
    category = category,
    title = title,
    notes = notes,
    formTemplateId = formTemplateId,
    formVersion = formVersion,
    formData = formData,
    baseVersion = baseVersion,
    syncState = syncState,
    updatedAt = updatedAt,
)

public fun Waypoint.toEntity(): WaypointEntity = WaypointEntity(
    id = id,
    projectId = projectId,
    trackId = trackId,
    latitude = position.latitude,
    longitude = position.longitude,
    accuracyMeters = accuracyMeters,
    altitudeMeters = altitudeMeters,
    recordedAt = recordedAt,
    category = category,
    title = title,
    notes = notes,
    formTemplateId = formTemplateId,
    formVersion = formVersion,
    formData = formData,
    baseVersion = baseVersion,
    syncState = syncState,
    updatedAt = updatedAt,
)

public fun WaypointPhotoEntity.toDomain(): WaypointPhoto = WaypointPhoto(
    id = id,
    waypointId = waypointId,
    localPath = localPath,
    remotePath = remotePath,
    width = width,
    height = height,
    bytes = bytes,
    sha256 = sha256,
    capturedAt = capturedAt,
    position = toPosition(),
    uploadState = uploadState,
    uploadAttempts = uploadAttempts,
)

public fun WaypointPhoto.toEntity(): WaypointPhotoEntity = WaypointPhotoEntity(
    id = id,
    waypointId = waypointId,
    localPath = localPath,
    remotePath = remotePath,
    width = width,
    height = height,
    bytes = bytes,
    sha256 = sha256,
    capturedAt = capturedAt,
    latitude = position?.latitude,
    longitude = position?.longitude,
    uploadState = uploadState,
    uploadAttempts = uploadAttempts,
)

/** A photo taken before the first fix has no location; half of one would be a lie. */
private fun WaypointPhotoEntity.toPosition(): LatLng? = latLngOrNull(latitude, longitude)
