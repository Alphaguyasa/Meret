package et.alpha.meret.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Wire names are a persistence contract, not a formatting detail. Every one of these
 * strings already exists in somebody's Room database and in Postgres; renaming a constant
 * must not change one.
 */
class WireNamedTest {
    @Test
    fun `every enum round-trips through its wire name`() {
        assertRoundTrip(SyncState.entries)
        assertRoundTrip(TrackStatus.entries)
        assertRoundTrip(TrackPurpose.entries)
        assertRoundTrip(LocationProvider.entries)
        assertRoundTrip(PhotoUploadState.entries)
        assertRoundTrip(MeasurementKind.entries)
        assertRoundTrip(MeasurementMethod.entries)
        assertRoundTrip(AssignmentStatus.entries)
        assertRoundTrip(TilePackSource.entries)
        assertRoundTrip(OutboxEntityType.entries)
        assertRoundTrip(OutboxOperation.entries)
        assertRoundTrip(ConflictResolution.entries)
    }

    @Test
    fun `wire names are the exact strings the schema documents`() {
        assertThat(SyncState.entries.map { it.wireName })
            .containsExactly("pending", "syncing", "synced", "conflict")
            .inOrder()
        assertThat(TrackStatus.entries.map { it.wireName })
            .containsExactly("active", "paused", "completed", "discarded")
            .inOrder()
        assertThat(TrackPurpose.entries.map { it.wireName })
            .containsExactly("survey", "transit", "measurement")
            .inOrder()
        assertThat(LocationProvider.entries.map { it.wireName })
            .containsExactly("fused", "gps", "network", "replay")
            .inOrder()
        assertThat(PhotoUploadState.entries.map { it.wireName })
            .containsExactly("pending", "uploading", "uploaded", "failed")
            .inOrder()
        assertThat(MeasurementKind.entries.map { it.wireName })
            .containsExactly("area", "distance")
            .inOrder()
        assertThat(MeasurementMethod.entries.map { it.wireName })
            .containsExactly("walked", "tapped")
            .inOrder()
        assertThat(AssignmentStatus.entries.map { it.wireName })
            .containsExactly("open", "in_progress", "done")
            .inOrder()
        assertThat(TilePackSource.entries.map { it.wireName })
            .containsExactly("downloaded", "sideloaded")
            .inOrder()
        assertThat(OutboxEntityType.entries.map { it.wireName })
            .containsExactly("track", "track_points", "waypoint", "photo", "measurement")
            .inOrder()
        assertThat(OutboxOperation.entries.map { it.wireName })
            .containsExactly("upsert", "delete")
            .inOrder()
        assertThat(ConflictResolution.entries.map { it.wireName })
            .containsExactly("local", "remote", "merged")
            .inOrder()
    }

    @Test
    fun `an unrecognised wire name resolves to null rather than throwing`() {
        // A row written by a newer build must not crash an older one.
        assertThat(SyncState.entries.fromWireName("quantum")).isNull()
        assertThat(TrackStatus.entries.fromWireName("")).isNull()
        assertThat(LocationProvider.entries.fromWireName("FUSED")).isNull()
    }

    private fun <T> assertRoundTrip(entries: List<T>) where T : Enum<T>, T : WireNamed {
        for (entry in entries) {
            assertThat(entries.fromWireName(entry.wireName)).isEqualTo(entry)
        }
        assertThat(entries.map { it.wireName }.toSet()).hasSize(entries.size)
    }
}
