package et.alpha.meret.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The migration harness, wired at version 1 so that phase 4's first schema change has
 * somewhere to land.
 *
 * There is nothing to migrate yet, which makes the two tests below sound trivial. They are
 * not: [MigrationTestHelper] builds its database from the committed schema JSON in the test
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
