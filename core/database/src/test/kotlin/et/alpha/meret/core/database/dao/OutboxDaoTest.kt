package et.alpha.meret.core.database.dao

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.database.DatabaseTest
import et.alpha.meret.core.database.Fixtures
import et.alpha.meret.core.model.ConflictResolution
import et.alpha.meret.core.model.OutboxEntityType
import kotlinx.coroutines.test.runTest
import org.junit.Test

class OutboxDaoTest : DatabaseTest() {
    private val outboxDao: OutboxDao get() = database.outboxDao()
    private val conflictDao: SyncConflictDao get() = database.syncConflictDao()

    @Test
    fun `a row still inside its backoff is not handed out`() = runTest {
        outboxDao.upsertEntries(
            listOf(
                Fixtures.outboxEntry(id = "due", nextAttemptAt = 5_000L),
                Fixtures.outboxEntry(id = "waiting", nextAttemptAt = 50_000L),
            ),
        )

        val due = outboxDao.getDueEntries(now = 10_000L, limit = 100)

        assertThat(due.map { it.id }).containsExactly("due")
    }

    @Test
    fun `a batch drains by priority then by age`() = runTest {
        outboxDao.upsertEntries(
            listOf(
                Fixtures.outboxEntry(id = "late-track", priority = 10, createdAt = 9_000L),
                Fixtures.outboxEntry(id = "waypoint", priority = 20, createdAt = 1_000L),
                Fixtures.outboxEntry(id = "early-track", priority = 10, createdAt = 1_000L),
            ),
        )

        val due = outboxDao.getDueEntries(now = 10_000L, limit = 100)

        assertThat(due.map { it.id })
            .containsExactly("early-track", "late-track", "waypoint")
            .inOrder()
    }

    @Test
    fun `a drain batch is capped`() = runTest {
        outboxDao.upsertEntries((1..10).map { Fixtures.outboxEntry(id = "o$it") })

        assertThat(outboxDao.getDueEntries(now = 10_000L, limit = 4)).hasSize(4)
    }

    @Test
    fun `a transient failure counts an attempt and pushes the next one out`() = runTest {
        outboxDao.upsertEntry(Fixtures.outboxEntry(id = "o1"))

        outboxDao.recordFailure("o1", error = "timeout", nextAttemptAt = 60_000L)
        outboxDao.recordFailure("o1", error = "503", nextAttemptAt = 120_000L)

        val entry = outboxDao.getEntriesFor(OutboxEntityType.TRACK, Fixtures.TRACK_ID).single()
        assertThat(entry.attempts).isEqualTo(2)
        assertThat(entry.lastError).isEqualTo("503")
        assertThat(entry.nextAttemptAt).isEqualTo(120_000L)
        assertThat(outboxDao.getDueEntries(now = 119_999L, limit = 100)).isEmpty()
    }

    @Test
    fun `a delivered row leaves no trace`() = runTest {
        outboxDao.upsertEntries(
            listOf(Fixtures.outboxEntry(id = "o1"), Fixtures.outboxEntry(id = "o2")),
        )

        outboxDao.deleteEntries(listOf("o1", "o2"))

        outboxDao.observePendingCount().test {
            assertThat(awaitItem()).isEqualTo(0)
        }
    }

    @Test
    fun `the pending badge tracks the queue`() = runTest {
        outboxDao.observePendingCount().test {
            assertThat(awaitItem()).isEqualTo(0)

            outboxDao.upsertEntry(Fixtures.outboxEntry(id = "o1"))
            assertThat(awaitItem()).isEqualTo(1)

            outboxDao.deleteEntry("o1")
            assertThat(awaitItem()).isEqualTo(0)
        }
    }

    @Test
    fun `the peer digest lists a hash for every queued write`() = runTest {
        outboxDao.upsertEntries(
            listOf(
                Fixtures.outboxEntry(id = "o1"),
                Fixtures.outboxEntry(id = "o2", entityType = OutboxEntityType.WAYPOINT),
            ),
        )

        assertThat(outboxDao.getAllDigests()).hasSize(2)
        assertThat(outboxDao.countByType(OutboxEntityType.WAYPOINT)).isEqualTo(1)
    }

    @Test
    fun `a queued write outlives the row it describes`() = runTest {
        // A delete is queued exactly when the local row is going away. No foreign key
        // may take the instruction with it.
        outboxDao.upsertEntry(Fixtures.outboxEntry(id = "o1", entityId = "a-track-never-stored"))

        assertThat(outboxDao.getDueEntries(now = 10_000L, limit = 100)).hasSize(1)
    }

    @Test
    fun `an unresolved conflict stays visible until someone decides`() = runTest {
        conflictDao.upsertConflict(Fixtures.conflict(id = "c1"))

        conflictDao.observeUnresolvedCount().test {
            assertThat(awaitItem()).isEqualTo(1)

            conflictDao.resolve("c1", ConflictResolution.LOCAL, resolvedAt = 9_000L)

            assertThat(awaitItem()).isEqualTo(0)
        }
    }

    @Test
    fun `resolving keeps both payloads on the device`() = runTest {
        conflictDao.upsertConflict(Fixtures.conflict(id = "c1"))

        conflictDao.resolve("c1", ConflictResolution.REMOTE, resolvedAt = 9_000L)

        val stored = requireNotNull(conflictDao.getConflict("c1"))
        assertThat(stored.resolution).isEqualTo(ConflictResolution.REMOTE)
        assertThat(stored.resolvedAt).isEqualTo(9_000L)
        assertThat(stored.localPayload).isEqualTo("""{"title":"mine"}""")
        assertThat(stored.remotePayload).isEqualTo("""{"title":"theirs"}""")
    }
}
