package et.alpha.meret.core.database.converter

import androidx.room.TypeConverter
import et.alpha.meret.core.model.AssignmentStatus
import et.alpha.meret.core.model.ConflictResolution
import et.alpha.meret.core.model.LocationProvider
import et.alpha.meret.core.model.MeasurementKind
import et.alpha.meret.core.model.MeasurementMethod
import et.alpha.meret.core.model.OutboxEntityType
import et.alpha.meret.core.model.OutboxOperation
import et.alpha.meret.core.model.PhotoUploadState
import et.alpha.meret.core.model.SyncState
import et.alpha.meret.core.model.TilePackSource
import et.alpha.meret.core.model.TrackPurpose
import et.alpha.meret.core.model.TrackStatus
import et.alpha.meret.core.model.fromWireName

/**
 * Enum columns, stored as their [et.alpha.meret.core.model.WireNamed.wireName] rather than
 * as `Enum.name` or an ordinal. An ordinal column silently reinterprets every row the day
 * someone reorders a constant; a `name` column breaks on a rename. The wire string is the
 * one form that is already a documented contract, shared with Postgres.
 *
 * Reading is total by necessity — a converter cannot decline. An unrecognised string means
 * the row was written by a newer build, so each reader falls back to the value that loses
 * the least: never one that would delete data, resume recording, or claim a row has
 * already reached the server.
 */
public class EnumConverters {
    @TypeConverter
    public fun fromSyncState(value: SyncState): String = value.wireName

    /** Falls back to [SyncState.PENDING]: re-sending a synced row is idempotent, and the
     *  opposite mistake strands the row on the device forever. */
    @TypeConverter
    public fun toSyncState(value: String): SyncState =
        SyncState.entries.fromWireName(value) ?: SyncState.PENDING

    @TypeConverter
    public fun fromTrackStatus(value: TrackStatus): String = value.wireName

    /** Falls back to [TrackStatus.COMPLETED]: the track and its points are preserved, but
     *  an unknown state is never treated as one we may append fixes to. */
    @TypeConverter
    public fun toTrackStatus(value: String): TrackStatus =
        TrackStatus.entries.fromWireName(value) ?: TrackStatus.COMPLETED

    @TypeConverter
    public fun fromTrackPurpose(value: TrackPurpose?): String? = value?.wireName

    @TypeConverter
    public fun toTrackPurpose(value: String?): TrackPurpose? =
        value?.let { TrackPurpose.entries.fromWireName(it) }

    @TypeConverter
    public fun fromLocationProvider(value: LocationProvider): String = value.wireName

    /** Falls back to [LocationProvider.GPS] — the assumption that credits the fix with the
     *  least, since a `fused` or `replay` label would imply provenance we cannot confirm. */
    @TypeConverter
    public fun toLocationProvider(value: String): LocationProvider =
        LocationProvider.entries.fromWireName(value) ?: LocationProvider.GPS

    @TypeConverter
    public fun fromPhotoUploadState(value: PhotoUploadState): String = value.wireName

    /** Falls back to [PhotoUploadState.PENDING]: re-uploading costs bytes, not photos. */
    @TypeConverter
    public fun toPhotoUploadState(value: String): PhotoUploadState =
        PhotoUploadState.entries.fromWireName(value) ?: PhotoUploadState.PENDING

    @TypeConverter
    public fun fromMeasurementKind(value: MeasurementKind): String = value.wireName

    @TypeConverter
    public fun toMeasurementKind(value: String): MeasurementKind =
        MeasurementKind.entries.fromWireName(value) ?: MeasurementKind.AREA

    @TypeConverter
    public fun fromMeasurementMethod(value: MeasurementMethod): String = value.wireName

    @TypeConverter
    public fun toMeasurementMethod(value: String): MeasurementMethod =
        MeasurementMethod.entries.fromWireName(value) ?: MeasurementMethod.TAPPED

    @TypeConverter
    public fun fromAssignmentStatus(value: AssignmentStatus): String = value.wireName

    /** Falls back to [AssignmentStatus.OPEN]: unfinished work stays visible. */
    @TypeConverter
    public fun toAssignmentStatus(value: String): AssignmentStatus =
        AssignmentStatus.entries.fromWireName(value) ?: AssignmentStatus.OPEN

    @TypeConverter
    public fun fromTilePackSource(value: TilePackSource): String = value.wireName

    /** Falls back to [TilePackSource.SIDELOADED]: we must not offer to re-download a pack
     *  whose provenance we no longer understand. */
    @TypeConverter
    public fun toTilePackSource(value: String): TilePackSource =
        TilePackSource.entries.fromWireName(value) ?: TilePackSource.SIDELOADED

    @TypeConverter
    public fun fromOutboxEntityType(value: OutboxEntityType): String = value.wireName

    /** Falls back to [OutboxEntityType.TRACK]. An outbox row whose type we cannot read is
     *  undrainable either way; the fallback only keeps the row queryable so it can be
     *  surfaced in diagnostics rather than crashing the drain loop. */
    @TypeConverter
    public fun toOutboxEntityType(value: String): OutboxEntityType =
        OutboxEntityType.entries.fromWireName(value) ?: OutboxEntityType.TRACK

    @TypeConverter
    public fun fromOutboxOperation(value: OutboxOperation): String = value.wireName

    /** Falls back to [OutboxOperation.UPSERT]. Never `DELETE` — misreading an unknown
     *  opcode as a delete would destroy server rows. */
    @TypeConverter
    public fun toOutboxOperation(value: String): OutboxOperation =
        OutboxOperation.entries.fromWireName(value) ?: OutboxOperation.UPSERT

    @TypeConverter
    public fun fromConflictResolution(value: ConflictResolution?): String? = value?.wireName

    @TypeConverter
    public fun toConflictResolution(value: String?): ConflictResolution? =
        value?.let { ConflictResolution.entries.fromWireName(it) }
}
