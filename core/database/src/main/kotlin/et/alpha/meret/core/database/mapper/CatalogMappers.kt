package et.alpha.meret.core.database.mapper

import et.alpha.meret.core.database.entity.AssignmentEntity
import et.alpha.meret.core.database.entity.FormTemplateEntity
import et.alpha.meret.core.database.entity.MeasurementEntity
import et.alpha.meret.core.database.entity.ProjectEntity
import et.alpha.meret.core.database.entity.TilePackEntity
import et.alpha.meret.core.model.Assignment
import et.alpha.meret.core.model.FormTemplate
import et.alpha.meret.core.model.Measurement
import et.alpha.meret.core.model.Project
import et.alpha.meret.core.model.TilePack
import et.alpha.meret.core.model.geo.BoundingBox

public fun ProjectEntity.toDomain(): Project = Project(
    id = id,
    orgId = orgId,
    name = name,
    description = description,
    defaultFormTemplateId = defaultFormTemplateId,
    tilePackId = tilePackId,
    startsAt = startsAt,
    endsAt = endsAt,
    updatedAt = updatedAt,
    isArchived = isArchived,
)

public fun Project.toEntity(): ProjectEntity = ProjectEntity(
    id = id,
    orgId = orgId,
    name = name,
    description = description,
    defaultFormTemplateId = defaultFormTemplateId,
    tilePackId = tilePackId,
    startsAt = startsAt,
    endsAt = endsAt,
    updatedAt = updatedAt,
    isArchived = isArchived,
)

public fun FormTemplateEntity.toDomain(): FormTemplate = FormTemplate(
    id = id,
    projectId = projectId,
    version = version,
    name = name,
    schemaJson = schemaJson,
    updatedAt = updatedAt,
)

public fun FormTemplate.toEntity(): FormTemplateEntity = FormTemplateEntity(
    id = id,
    projectId = projectId,
    version = version,
    name = name,
    schemaJson = schemaJson,
    updatedAt = updatedAt,
)

public fun AssignmentEntity.toDomain(): Assignment = Assignment(
    id = id,
    projectId = projectId,
    assignedUserId = assignedUserId,
    name = name,
    polygonGeoJson = polygonGeoJson,
    dueAt = dueAt,
    status = status,
    coveragePercent = coveragePercent,
)

public fun Assignment.toEntity(): AssignmentEntity = AssignmentEntity(
    id = id,
    projectId = projectId,
    assignedUserId = assignedUserId,
    name = name,
    polygonGeoJson = polygonGeoJson,
    dueAt = dueAt,
    status = status,
    coveragePercent = coveragePercent,
)

public fun MeasurementEntity.toDomain(): Measurement = Measurement(
    id = id,
    projectId = projectId,
    name = name,
    kind = kind,
    verticesGeoJson = verticesGeoJson,
    areaSquareMeters = areaSquareMeters,
    perimeterMeters = perimeterMeters,
    distanceMeters = distanceMeters,
    method = method,
    recordedAt = recordedAt,
    updatedAt = updatedAt,
    syncState = syncState,
)

public fun Measurement.toEntity(): MeasurementEntity = MeasurementEntity(
    id = id,
    projectId = projectId,
    name = name,
    kind = kind,
    verticesGeoJson = verticesGeoJson,
    areaSquareMeters = areaSquareMeters,
    perimeterMeters = perimeterMeters,
    distanceMeters = distanceMeters,
    method = method,
    recordedAt = recordedAt,
    updatedAt = updatedAt,
    syncState = syncState,
)

/** Unlike a track's, a tile pack's extent is non-null on both sides — see [TilePackEntity]. */
public fun TilePackEntity.toDomain(): TilePack = TilePack(
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

public fun TilePack.toEntity(): TilePackEntity = TilePackEntity(
    id = id,
    name = name,
    regionCode = regionCode,
    path = path,
    bytes = bytes,
    minZoom = minZoom,
    maxZoom = maxZoom,
    bboxMinLatitude = bounds.minLatitude,
    bboxMinLongitude = bounds.minLongitude,
    bboxMaxLatitude = bounds.maxLatitude,
    bboxMaxLongitude = bounds.maxLongitude,
    source = source,
    installedAt = installedAt,
    styleId = styleId,
)
