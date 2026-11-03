package et.alpha.meret.core.database.migration

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import et.alpha.meret.core.database.MeretDatabase

/**
 * Version 1 → 2: the `waypoint_draft` table (roadmap item 32b). Additive only: no existing
 * table or row is touched.
 *
 * The statement is the one Room generates for `WaypointDraftEntity` (compare
 * `schemas/.../2.json`); Room checks the result against the entity on open, and
 * `MeretDatabaseMigrationTest` opens a version 1 database through it.
 */
public val MIGRATION_1_2: Migration =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `waypoint_draft` (" +
                    "`project_id` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`category` TEXT, " +
                    "`notes` TEXT NOT NULL, " +
                    "`form_template_id` TEXT, " +
                    "`form_version` INTEGER, " +
                    "`form_data` TEXT, " +
                    "`number_texts` TEXT, " +
                    "`show_errors` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`project_id`))",
            )
        }
    }

/**
 * Every migration, in order. The app's builder and every test that opens a file database
 * call this, so a new migration is added in one place.
 */
public fun RoomDatabase.Builder<MeretDatabase>.addMeretMigrations(): MeretDatabaseBuilder =
    addMigrations(MIGRATION_1_2)

/** Room's builder for this database. */
public typealias MeretDatabaseBuilder = RoomDatabase.Builder<MeretDatabase>
