package et.alpha.meret.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A fresh in-memory [MeretDatabase] per test.
 *
 * In-memory rather than a temp file, per CLAUDE.md — but note that it is the *same* Room
 * schema either way, foreign keys and indices included, so a cascade or a unique-constraint
 * violation fails here exactly as it would on a device.
 */
@RunWith(RobolectricTestRunner::class)
abstract class DatabaseTest {
    protected lateinit var database: MeretDatabase
        private set

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    MeretDatabase::class.java,
                )
                // Tests drive the DAOs directly from the test thread; production code never
                // does, and DatabaseModule deliberately does not set this.
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }
}
