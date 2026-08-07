package et.alpha.meret.core.database.converter

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.model.ConflictResolution
import et.alpha.meret.core.model.LocationProvider
import et.alpha.meret.core.model.OutboxOperation
import et.alpha.meret.core.model.PhotoUploadState
import et.alpha.meret.core.model.SyncState
import et.alpha.meret.core.model.TrackPurpose
import et.alpha.meret.core.model.TrackStatus
import org.junit.Test

class EnumConvertersTest {
    private val converters = EnumConverters()

    @Test
    fun `every enum survives the trip to a column and back`() {
        for (value in SyncState.entries) {
            assertThat(converters.toSyncState(converters.fromSyncState(value))).isEqualTo(value)
        }
        for (value in TrackStatus.entries) {
            assertThat(converters.toTrackStatus(converters.fromTrackStatus(value))).isEqualTo(value)
        }
        for (value in LocationProvider.entries) {
            val stored = converters.fromLocationProvider(value)
            assertThat(converters.toLocationProvider(stored)).isEqualTo(value)
        }
        for (value in PhotoUploadState.entries) {
            val stored = converters.fromPhotoUploadState(value)
            assertThat(converters.toPhotoUploadState(stored)).isEqualTo(value)
        }
    }

    @Test
    fun `a nullable enum column stays null in both directions`() {
        assertThat(converters.fromTrackPurpose(null)).isNull()
        assertThat(converters.toTrackPurpose(null)).isNull()
        assertThat(converters.fromConflictResolution(null)).isNull()
        assertThat(converters.toConflictResolution(null)).isNull()

        assertThat(converters.toTrackPurpose(converters.fromTrackPurpose(TrackPurpose.SURVEY)))
            .isEqualTo(TrackPurpose.SURVEY)
        val resolution = converters.fromConflictResolution(ConflictResolution.MERGED)
        assertThat(converters.toConflictResolution(resolution)).isEqualTo(ConflictResolution.MERGED)
    }

    @Test
    fun `a value written by a newer build degrades instead of crashing`() {
        assertThat(converters.toSyncState("quantum")).isEqualTo(SyncState.PENDING)
        assertThat(converters.toTrackStatus("hibernating")).isEqualTo(TrackStatus.COMPLETED)
        assertThat(converters.toLocationProvider("galileo")).isEqualTo(LocationProvider.GPS)
        assertThat(converters.toPhotoUploadState("beaming")).isEqualTo(PhotoUploadState.PENDING)
        assertThat(converters.toTrackPurpose("reconnaissance")).isNull()
        assertThat(converters.toConflictResolution("coin-flip")).isNull()
    }

    @Test
    fun `an unreadable outbox opcode never degrades into a delete`() {
        // Misreading an unknown opcode as DELETE would destroy server rows.
        assertThat(converters.toOutboxOperation("purge")).isEqualTo(OutboxOperation.UPSERT)
    }

    @Test
    fun `the stored form is the documented wire string, not the Kotlin name`() {
        assertThat(converters.fromTrackStatus(TrackStatus.ACTIVE)).isEqualTo("active")
        assertThat(converters.fromSyncState(SyncState.SYNCED)).isEqualTo("synced")
        assertThat(converters.fromLocationProvider(LocationProvider.FUSED)).isEqualTo("fused")
    }
}
