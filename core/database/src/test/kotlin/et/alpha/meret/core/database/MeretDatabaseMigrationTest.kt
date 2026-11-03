package et.alpha.meret.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.database.entity.WaypointDraftEntity
import et.alpha.meret.core.database.migration.addMeretMigrations
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Every migration, run on the JVM against a real file database.
 *
 * The version 1 database is built from the committed `schemas/.../1.json`, statement by
 * statement, the way Room's `MigrationTestHelper` builds one; that helper reads the schema
 * from test APK assets, which only the device test (`MigrationTest`) has. Opening it with the
 * real builder then runs the migration, and Room checks every table against the entities
 * before handing out a DAO: a migration that made the wrong table fails here.
 */
@RunWith(RobolectricTestRunner::class)
class MeretDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var file: File

    @Before
    fun setUp() {
        file = context.getDatabasePath(TEST_DB)
        file.parentFile?.mkdirs()
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun `version 1 opens at the current version and keeps its waypoints`() = runBlocking<Unit> {
        createVersion1 { db ->
            db.execSQL(
                "INSERT INTO waypoint (id, project_id, lat, lon, accuracy_m, recorded_at, " +
                    "title, base_version, sync_state, updated_at) " +
                    "VALUES ('wp-1', 'local', 9.03, 38.74, 4.5, 1000, 'Borehole', 0, " +
                    "'pending', 1000)",
            )
        }

        val database = openCurrent()
        try {
            val waypoint = checkNotNull(database.waypointDao().getWaypoint("wp-1"))
            assertThat(waypoint.title).isEqualTo("Borehole")
            assertThat(waypoint.latitude).isEqualTo(9.03)
            assertThat(database.openHelper.readableDatabase.version)
                .isEqualTo(MeretDatabase.VERSION)
        } finally {
            database.close()
        }
    }

    @Test
    fun `after 1 to 2 the draft table stores and returns a draft`() = runBlocking<Unit> {
        createVersion1 {}

        val database = openCurrent()
        try {
            val draft =
                WaypointDraftEntity(
                    projectId = "local",
                    title = "Borehole",
                    category = "water_point",
                    notes = "Pump broken",
                    formTemplateId = "template-1",
                    formVersion = 2,
                    formData = """{"present":"yes"}""",
                    numberTexts = """{"people":"4"}""",
                    showErrors = true,
                    updatedAt = 2_000,
                )
            database.waypointDraftDao().upsertDraft(draft)

            assertThat(database.waypointDraftDao().getDraft("local")).isEqualTo(draft)
        } finally {
            database.close()
        }
    }

    private fun createVersion1(seed: (SQLiteDatabase) -> Unit) {
        val schema = JSONObject(schemaFile(1).readText()).getJSONObject("database")
        assertThat(schema.getInt("version")).isEqualTo(1)
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").withTable(table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").withTable(table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = 1
            seed(db)
        } finally {
            db.close()
        }
    }

    private fun openCurrent(): MeretDatabase = Room
        .databaseBuilder(context, MeretDatabase::class.java, TEST_DB)
        .addMeretMigrations()
        .allowMainThreadQueries()
        .build()

    private fun String.withTable(table: String) = replace("\${TABLE_NAME}", table)

    /** Unit tests run in the module directory, where the schemas are committed. */
    private fun schemaFile(version: Int) =
        File("schemas/${MeretDatabase::class.java.name}/$version.json")

    private companion object {
        const val TEST_DB = "migration-jvm-test.db"
    }
}
