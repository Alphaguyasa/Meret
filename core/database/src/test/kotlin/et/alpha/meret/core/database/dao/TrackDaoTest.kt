package et.alpha.meret.core.database.dao

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.database.DatabaseTest
import et.alpha.meret.core.database.Fixtures
import et.alpha.meret.core.model.SyncState
import et.alpha.meret.core.model.TrackStatus
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TrackDaoTest : DatabaseTest() {
    private val trackDao: TrackDao get() = database.trackDao()
    private val pointDao: TrackPointDao get() = database.trackPointDao()

    @Test
    fun `tracks for a project come back newest first`() = runTest {
        trackDao.upsertTracks(
            listOf(
                Fixtures.track(id = "old", startedAt = 1_000L),
                Fixtures.track(id = "newest", startedAt = 9_000L),
                Fixtures.track(id = "middle", startedAt = 5_000L),
            ),
        )

        trackDao.observeTracks(Fixtures.PROJECT_ID).test {
            assertThat(awaitItem().map { it.id })
                .containsExactly("newest", "middle", "old")
                .inOrder()
        }
    }

    @Test
    fun `another project's tracks are not in the list`() = runTest {
        trackDao.upsertTrack(Fixtures.track(id = "ours"))
        trackDao.upsertTrack(Fixtures.track(id = "theirs", projectId = "project-2"))

        trackDao.observeTracks(Fixtures.PROJECT_ID).test {
            assertThat(awaitItem().map { it.id }).containsExactly("ours")
        }
    }

    @Test
    fun `a track that is still recording is discoverable after a cold start`() = runTest {
        trackDao.upsertTrack(Fixtures.track(id = "done", status = TrackStatus.COMPLETED))
        trackDao.upsertTrack(
            Fixtures.track(id = "live", status = TrackStatus.PAUSED, startedAt = 7_000L),
        )

        trackDao.observeActiveTrack().test {
            assertThat(awaitItem()?.id).isEqualTo("live")
        }
    }

    @Test
    fun `no active track is null rather than an error`() = runTest {
        trackDao.upsertTrack(Fixtures.track(status = TrackStatus.COMPLETED))

        trackDao.observeActiveTrack().test {
            assertThat(awaitItem()).isNull()
        }
    }

    @Test
    fun `a track observer sees a later edit`() = runTest {
        trackDao.upsertTrack(Fixtures.track())

        trackDao.observeTrack(Fixtures.TRACK_ID).test {
            assertThat(awaitItem()?.status).isEqualTo(TrackStatus.ACTIVE)

            trackDao.updateStatus(Fixtures.TRACK_ID, TrackStatus.COMPLETED, updatedAt = 9_000L)

            val updated = awaitItem()
            assertThat(updated?.status).isEqualTo(TrackStatus.COMPLETED)
            assertThat(updated?.updatedAt).isEqualTo(9_000L)
        }
    }

    @Test
    fun `the sync worker can find exactly what has not been sent`() = runTest {
        trackDao.upsertTrack(Fixtures.track(id = "a"))
        trackDao.upsertTrack(Fixtures.track(id = "b"))
        trackDao.updateSyncState("b", SyncState.SYNCED)

        assertThat(trackDao.getTracksBySyncState(SyncState.PENDING).map { it.id })
            .containsExactly("a")
        assertThat(trackDao.getTracksBySyncState(SyncState.SYNCED).map { it.id })
            .containsExactly("b")
    }

    @Test
    fun `a track loads with its points in one read`() = runTest {
        trackDao.upsertTrack(Fixtures.track())
        pointDao.appendBatch(Fixtures.TRACK_ID, Fixtures.points(1..30), 250.0, 30_000L, 5_000L)

        val loaded = requireNotNull(trackDao.getTrackWithPoints(Fixtures.TRACK_ID))

        assertThat(loaded.track.id).isEqualTo(Fixtures.TRACK_ID)
        assertThat(loaded.points).hasSize(30)
        assertThat(loaded.track.pointCount).isEqualTo(loaded.points.size)
    }

    @Test
    fun `observing a track with points re-emits when a batch arrives`() = runTest {
        trackDao.upsertTrack(Fixtures.track())

        trackDao.observeTrackWithPoints(Fixtures.TRACK_ID).test {
            assertThat(awaitItem()?.points).isEmpty()

            pointDao.appendBatch(Fixtures.TRACK_ID, Fixtures.points(1..20), 190.0, 20_000L, 5_000L)

            assertThat(awaitItem()?.points).hasSize(20)
        }
    }

    @Test
    fun `a missing track observes as null`() = runTest {
        trackDao.observeTrackWithPoints("nobody").test {
            assertThat(awaitItem()).isNull()
        }
    }

    @Test
    fun `simplified geometry is written on completion and can be cleared`() = runTest {
        trackDao.upsertTrack(Fixtures.track())

        trackDao.updateSimplifiedGeoJson(Fixtures.TRACK_ID, """{"type":"LineString"}""", 9_000L)
        assertThat(requireNotNull(trackDao.getTrack(Fixtures.TRACK_ID)).simplifiedGeoJson)
            .isEqualTo("""{"type":"LineString"}""")

        trackDao.updateSimplifiedGeoJson(Fixtures.TRACK_ID, null, 10_000L)
        assertThat(requireNotNull(trackDao.getTrack(Fixtures.TRACK_ID)).simplifiedGeoJson).isNull()
    }

    @Test
    fun `upserting an existing track replaces it rather than duplicating`() = runTest {
        trackDao.upsertTrack(Fixtures.track())
        trackDao.upsertTrack(Fixtures.track().copy(name = "Kebele 03 morning"))

        trackDao.observeTracks(Fixtures.PROJECT_ID).test {
            val tracks = awaitItem()
            assertThat(tracks).hasSize(1)
            assertThat(tracks.single().name).isEqualTo("Kebele 03 morning")
        }
    }

    @Test
    fun `a track can be recorded for a project this device has never pulled`() = runTest {
        // No project row exists. Offline-first: this must not throw.
        trackDao.upsertTrack(Fixtures.track(projectId = "never-pulled"))

        assertThat(trackDao.getTrack(Fixtures.TRACK_ID)).isNotNull()
    }
}
