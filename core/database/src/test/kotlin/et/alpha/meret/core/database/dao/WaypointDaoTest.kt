package et.alpha.meret.core.database.dao

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.database.DatabaseTest
import et.alpha.meret.core.database.Fixtures
import et.alpha.meret.core.model.PhotoUploadState
import et.alpha.meret.core.model.SyncState
import kotlinx.coroutines.test.runTest
import org.junit.Test

class WaypointDaoTest : DatabaseTest() {
    private val waypointDao: WaypointDao get() = database.waypointDao()
    private val photoDao: WaypointPhotoDao get() = database.waypointPhotoDao()

    @Test
    fun `waypoints for a project come back newest first`() = runTest {
        waypointDao.upsertWaypoints(
            listOf(
                Fixtures.waypoint(id = "first", recordedAt = 1_000L),
                Fixtures.waypoint(id = "last", recordedAt = 9_000L),
            ),
        )

        waypointDao.observeWaypoints(Fixtures.PROJECT_ID).test {
            assertThat(awaitItem().map { it.id }).containsExactly("last", "first").inOrder()
        }
    }

    @Test
    fun `waypoints captured during a track are reachable from it`() = runTest {
        waypointDao.upsertWaypoint(Fixtures.waypoint(id = "attached", trackId = Fixtures.TRACK_ID))
        waypointDao.upsertWaypoint(Fixtures.waypoint(id = "standalone"))

        waypointDao.observeWaypointsForTrack(Fixtures.TRACK_ID).test {
            assertThat(awaitItem().map { it.id }).containsExactly("attached")
        }
    }

    @Test
    fun `a viewport query returns only what is inside it`() = runTest {
        waypointDao.upsertWaypoints(
            listOf(
                Fixtures.waypoint(id = "inside", latitude = 9.0300, longitude = 38.7400),
                Fixtures.waypoint(id = "north", latitude = 9.5000, longitude = 38.7400),
                Fixtures.waypoint(id = "east", latitude = 9.0300, longitude = 39.5000),
            ),
        )

        waypointDao
            .observeWaypointsInBounds(
                projectId = Fixtures.PROJECT_ID,
                minLatitude = 9.00,
                minLongitude = 38.70,
                maxLatitude = 9.10,
                maxLongitude = 38.80,
            ).test {
                assertThat(awaitItem().map { it.id }).containsExactly("inside")
            }
    }

    @Test
    fun `an edit bumps nothing the caller did not ask it to`() = runTest {
        waypointDao.upsertWaypoint(Fixtures.waypoint())

        waypointDao.updateSyncState(Fixtures.WAYPOINT_ID, SyncState.CONFLICT)

        val stored = requireNotNull(waypointDao.getWaypoint(Fixtures.WAYPOINT_ID))
        assertThat(stored.syncState).isEqualTo(SyncState.CONFLICT)
        assertThat(stored.baseVersion).isEqualTo(0)
        assertThat(stored.updatedAt).isEqualTo(1_000L)
    }

    @Test
    fun `form data survives a round trip through storage unchanged`() = runTest {
        val json = """{"crop":"ጤፍ","area_ha":1.25,"notes":"በጣም ጥሩ"}"""
        waypointDao.upsertWaypoint(
            Fixtures.waypoint().copy(
                formTemplateId = "template-1",
                formVersion = 3,
                formData = json,
            ),
        )

        val stored = requireNotNull(waypointDao.getWaypoint(Fixtures.WAYPOINT_ID))
        assertThat(stored.formData).isEqualTo(json)
        assertThat(stored.formVersion).isEqualTo(3)
    }

    @Test
    fun `photos hang off their waypoint and cascade with it`() = runTest {
        waypointDao.upsertWaypoint(Fixtures.waypoint())
        photoDao.upsertPhotos(
            listOf(
                Fixtures.photo(id = "p1", sha256 = "a".repeat(64)),
                Fixtures.photo(id = "p2", sha256 = "c".repeat(64)),
            ),
        )

        photoDao.observePhotos(Fixtures.WAYPOINT_ID).test {
            assertThat(awaitItem()).hasSize(2)
        }

        waypointDao.deleteWaypoint(Fixtures.WAYPOINT_ID)

        assertThat(photoDao.getPhoto("p1")).isNull()
        assertThat(photoDao.getPhoto("p2")).isNull()
    }

    @Test
    fun `the same bytes arriving twice are found by hash`() = runTest {
        waypointDao.upsertWaypoint(Fixtures.waypoint())
        photoDao.upsertPhoto(Fixtures.photo(id = "p1", sha256 = "d".repeat(64)))

        assertThat(photoDao.getPhotoBySha256("d".repeat(64))?.id).isEqualTo("p1")
        assertThat(photoDao.getPhotoBySha256("e".repeat(64))).isNull()
    }

    @Test
    fun `an upload attempt is counted whether it succeeded or not`() = runTest {
        waypointDao.upsertWaypoint(Fixtures.waypoint())
        photoDao.upsertPhoto(Fixtures.photo(id = "p1"))

        photoDao.updateUploadState("p1", PhotoUploadState.FAILED, null, attemptIncrement = 1)
        photoDao.updateUploadState("p1", PhotoUploadState.UPLOADED, "photos/p1.jpg", 1)

        val stored = requireNotNull(photoDao.getPhoto("p1"))
        assertThat(stored.uploadState).isEqualTo(PhotoUploadState.UPLOADED)
        assertThat(stored.remotePath).isEqualTo("photos/p1.jpg")
        assertThat(stored.uploadAttempts).isEqualTo(2)
    }

    @Test
    fun `the outstanding upload badge counts everything not yet uploaded`() = runTest {
        waypointDao.upsertWaypoint(Fixtures.waypoint())
        photoDao.upsertPhotos(
            listOf(
                Fixtures.photo(id = "p1", sha256 = "a".repeat(64)),
                Fixtures.photo(id = "p2", sha256 = "c".repeat(64)),
            ),
        )

        photoDao.observeOutstandingUploadCount().test {
            assertThat(awaitItem()).isEqualTo(2)

            photoDao.updateUploadState("p1", PhotoUploadState.UPLOADED, "photos/p1.jpg", 1)

            assertThat(awaitItem()).isEqualTo(1)
        }
    }

    @Test
    fun `pending uploads are handed out oldest first and capped`() = runTest {
        waypointDao.upsertWaypoint(Fixtures.waypoint())
        photoDao.upsertPhotos(
            (1..5).map {
                Fixtures
                    .photo(
                        id = "p$it",
                        sha256 = "$it".repeat(64),
                    ).copy(capturedAt = it * 1_000L)
            },
        )

        val batch = photoDao.getPhotosByUploadState(PhotoUploadState.PENDING, limit = 3)

        assertThat(batch.map { it.id }).containsExactly("p1", "p2", "p3").inOrder()
    }
}
