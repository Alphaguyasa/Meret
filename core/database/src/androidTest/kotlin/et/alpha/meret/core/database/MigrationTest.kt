package et.alpha.meret.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.database.migration.MIGRATION_1_2
import et.alpha.meret.core.database.migration.addMeretMigrations
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The migrations on a device, against the committed schema JSON. The same migrations also
 * run on the JVM in `MeretDatabaseMigrationTest`, which CI runs.
 *
 * The last two tests sound trivial. They are not: [MigrationTestHelper] builds its database from the committed schema JSON in the test
 * APK's assets, so this fails the moment `schemas/` stops being exported or stops being
 * committed — the exact failure that, discovered later, leaves a released version with no
 * record of what its schema was and no way to write a migration off it.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MeretDatabase::class.java,
        )

    /** Room's own check: the migrated tables match `2.json` exactly, and a waypoint stays. */
    @Test
    fun migrate1To2AddsTheDraftTableAndKeepsWaypoints() {
        helper.createDatabase(TEST_DB, 1).use { database ->
            database.execSQL(
                "INSERT INTO waypoint (id, project_id, lat, lon, accuracy_m, recorded_at, " +
                    "base_version, sync_state, updated_at) " +
                    "VALUES ('wp-1', 'local', 9.03, 38.74, 4.5, 1000, 0, 'pending', 1000)",
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).use { database ->
            database.query("SELECT id FROM waypoint").use { cursor ->
                assertThat(cursor.count).isEqualTo(1)
            }
            database.query("SELECT * FROM waypoint_draft").use { cursor ->
                assertThat(cursor.count).isEqualTo(0)
            }
        }
    }

    @Test
    fun theCommittedSchemaForTheCurrentVersionOpens() {
        helper.createDatabase(TEST_DB, MeretDatabase.VERSION).use { database ->
            assertThat(database.version).isEqualTo(MeretDatabase.VERSION)
        }
    }

    /**
     * Room validates the runtime schema against the exported identity hash on open. A
     * database created from the committed JSON that the real builder then declines to open
     * means the two have drifted.
     */
    @Test
    fun theShippedSchemaMatchesTheCommittedOne() {
        helper.createDatabase(TEST_DB, MeretDatabase.VERSION).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database =
            Room
                .databaseBuilder(context, MeretDatabase::class.java, TEST_DB)
                .addMeretMigrations()
                .build()

        try {
            assertThat(database.openHelper.readableDatabase.isOpen).isTrue()
        } finally {
            database.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
