package et.alpha.meret.core.database.dao

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.database.DatabaseTest
import et.alpha.meret.core.database.Fixtures
import et.alpha.meret.core.model.SyncState
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The pull-only tables, plus measurements. */
class CatalogDaoTest : DatabaseTest() {
    private val projectDao: ProjectDao get() = database.projectDao()
    private val templateDao: FormTemplateDao get() = database.formTemplateDao()
    private val assignmentDao: AssignmentDao get() = database.assignmentDao()
    private val tilePackDao: TilePackDao get() = database.tilePackDao()
    private val measurementDao: MeasurementDao get() = database.measurementDao()

    @Test
    fun `an archived project drops out of the picker but is still readable`() = runTest {
        projectDao.upsertProjects(
            listOf(
                Fixtures.project(id = "live", name = "Live"),
                Fixtures.project(id = "archived", name = "Archived", isArchived = true),
            ),
        )

        projectDao.observeProjects().test {
            assertThat(awaitItem().map { it.id }).containsExactly("live")
        }
        assertThat(projectDao.getProject("archived")).isNotNull()
    }

    @Test
    fun `the delta cursor is the newest server clock this device has seen`() = runTest {
        assertThat(projectDao.getLatestUpdatedAt()).isNull()

        projectDao.upsertProjects(
            listOf(
                Fixtures.project(id = "a", updatedAt = 5_000L),
                Fixtures.project(id = "b", updatedAt = 9_000L),
            ),
        )

        assertThat(projectDao.getLatestUpdatedAt()).isEqualTo(9_000L)
    }

    @Test
    fun `an old template version is still reachable after a newer one arrives`() = runTest {
        templateDao.upsertTemplates(
            listOf(
                Fixtures.formTemplate(id = "t-v1", version = 1),
                Fixtures.formTemplate(id = "t-v2", version = 2),
            ),
        )

        assertThat(templateDao.getTemplateVersion("t-v1", version = 1)).isNotNull()
        // A submission captured against v1 must not silently render against v2.
        assertThat(templateDao.getTemplateVersion("t-v1", version = 2)).isNull()
    }

    @Test
    fun `coverage is written locally and survives a pull that rewrites the rest`() = runTest {
        assignmentDao.upsertAssignments(listOf(Fixtures.assignment(id = "a1")))
        assignmentDao.updateCoverage("a1", coveragePercent = 62.5)

        assertThat(requireNotNull(assignmentDao.getAssignment("a1")).coveragePercent)
            .isWithin(1e-9)
            .of(62.5)
    }

    @Test
    fun `assignments are listed for the user they belong to`() = runTest {
        assignmentDao.upsertAssignments(
            listOf(
                Fixtures.assignment(id = "mine"),
                Fixtures.assignment(id = "theirs").copy(assignedUserId = "user-2"),
            ),
        )

        assignmentDao.observeAssignmentsForUser("user-1").test {
            assertThat(awaitItem().map { it.id }).containsExactly("mine")
        }
    }

    @Test
    fun `the map can ask which installed pack covers where the user is standing`() = runTest {
        tilePackDao.upsertTilePack(Fixtures.tilePack(id = "ethiopia"))
        tilePackDao.upsertTilePack(
            Fixtures.tilePack(id = "addis").copy(
                bboxMinLatitude = 8.8,
                bboxMinLongitude = 38.6,
                bboxMaxLatitude = 9.2,
                bboxMaxLongitude = 38.9,
                maxZoom = 16,
            ),
        )

        val covering = tilePackDao.getTilePacksCovering(9.03, 38.74)
        assertThat(covering.map { it.id }).containsExactly("addis", "ethiopia").inOrder()

        assertThat(tilePackDao.getTilePacksCovering(51.5, -0.12)).isEmpty()
    }

    @Test
    fun `an area measurement keeps its distance column null rather than zero`() = runTest {
        measurementDao.upsertMeasurement(Fixtures.measurement())

        val stored = requireNotNull(measurementDao.getMeasurement("measurement-1"))
        assertThat(stored.areaSquareMeters).isWithin(1e-9).of(2_500.0)
        assertThat(stored.distanceMeters).isNull()
        assertThat(stored.syncState).isEqualTo(SyncState.PENDING)
    }

    @Test
    fun `a measurement can be renamed and re-synced`() = runTest {
        measurementDao.upsertMeasurement(Fixtures.measurement())
        measurementDao.updateSyncState("measurement-1", SyncState.SYNCED)
        measurementDao.upsertMeasurement(
            Fixtures.measurement().copy(name = "South plot", syncState = SyncState.PENDING),
        )

        measurementDao.observeMeasurements(Fixtures.PROJECT_ID).test {
            val stored = awaitItem().single()
            assertThat(stored.name).isEqualTo("South plot")
            assertThat(stored.syncState).isEqualTo(SyncState.PENDING)
        }
    }
}
