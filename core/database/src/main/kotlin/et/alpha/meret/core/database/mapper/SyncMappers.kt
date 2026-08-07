package et.alpha.meret.core.database.mapper

import et.alpha.meret.core.database.entity.OutboxEntity
import et.alpha.meret.core.database.entity.SyncConflictEntity
import et.alpha.meret.core.model.OutboxEntry
import et.alpha.meret.core.model.SyncConflict

public fun OutboxEntity.toDomain(): OutboxEntry = OutboxEntry(
    id = id,
    entityType = entityType,
    entityId = entityId,
    operation = operation,
    payload = payload,
    payloadSha256 = payloadSha256,
    priority = priority,
    attempts = attempts,
    lastError = lastError,
    nextAttemptAt = nextAttemptAt,
    createdAt = createdAt,
)

public fun OutboxEntry.toEntity(): OutboxEntity = OutboxEntity(
    id = id,
    entityType = entityType,
    entityId = entityId,
    operation = operation,
    payload = payload,
    payloadSha256 = payloadSha256,
    priority = priority,
    attempts = attempts,
    lastError = lastError,
    nextAttemptAt = nextAttemptAt,
    createdAt = createdAt,
)

public fun SyncConflictEntity.toDomain(): SyncConflict = SyncConflict(
    id = id,
    entityType = entityType,
    entityId = entityId,
    localPayload = localPayload,
    remotePayload = remotePayload,
    detectedAt = detectedAt,
    resolvedAt = resolvedAt,
    resolution = resolution,
)

public fun SyncConflict.toEntity(): SyncConflictEntity = SyncConflictEntity(
    id = id,
    entityType = entityType,
    entityId = entityId,
    localPayload = localPayload,
    remotePayload = remotePayload,
    detectedAt = detectedAt,
    resolvedAt = resolvedAt,
    resolution = resolution,
)
