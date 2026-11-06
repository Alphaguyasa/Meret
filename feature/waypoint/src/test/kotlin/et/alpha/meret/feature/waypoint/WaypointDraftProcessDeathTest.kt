package et.alpha.meret.feature.waypoint

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.dispatcher.StandardDispatcherProvider
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.common.time.SystemTimeProvider
import et.alpha.meret.core.data.location.DevicePositionRepository
import et.alpha.meret.core.data.waypoint.RoomWaypointDraftStore
import et.alpha.meret.core.database.MeretDatabase
import et.alpha.meret.core.database.entity.WaypointDraftEntity
import et.alpha.meret.core.database.migration.addMeretMigrations
import et.alpha.meret.core.model.form.FormAnswer
import et.alpha.meret.core.model.form.FormField
import et.alpha.meret.core.model.form.FormSchema
import et.alpha.meret.core.model.form.LocalizedText
import et.alpha.meret.core.model.form.SelectOption
import et.alpha.meret.core.model.geo.DevicePosition
import et.alpha.meret.domain.waypoint.NewWaypoint
import et.alpha.meret.domain.waypoint.SaveWaypointUseCase
import et.alpha.meret.domain.waypoint.WaypointCategory
import et.alpha.meret.domain.waypoint.WaypointForm
import et.alpha.meret.domain.waypoint.WaypointFormSource
import et.alpha.meret.domain.waypoint.WaypointStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BUILD_PLAN Phase 8's "survives a forced process death", minus photos: everything on the sheet
 * reaches the database file on its own, and a new ViewModel over a newly opened database, with
 * nothing else carried over (no saved state, no object from the first), shows all of it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WaypointDraftProcessDeathTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
        Dispatchers.resetMain()
    }

    @Test
    fun `a killed sheet comes back with every value from Room alone`() = runBlocking<Unit> {
        val firstProcess = openDatabase()
        val sheet = viewModel(firstProcess)
        withTimeout(TIMEOUT_MS) { sheet.uiState.first { it.form is FormUiState.Ready } }

        sheet.onTitleChange("Borehole")
        sheet.onCategoryTap(WaypointCategory.WATER_POINT)
        sheet.onNotesChange("Pump broken")
        sheet.onAnswerChange("present", FormAnswer.Choice("yes"))
        sheet.onAnswerChange("crops", FormAnswer.Choices(listOf("teff", "maize")))
        sheet.onAnswerChange("name", FormAnswer.Text("Abebe"))
        sheet.onNumberTextChange("people", "4.")

        // The writes are the ViewModel's own; the test only waits for the last to land.
        val written = awaitDraft(firstProcess) { it.numberTexts?.contains("4.") == true }
        assertThat(written.title).isEqualTo("Borehole")
        // The process dies: the database is closed, and nothing from it is kept.
        firstProcess.close()

        val secondProcess = openDatabase()
        try {
            val restored =
                withTimeout(TIMEOUT_MS) {
                    viewModel(secondProcess).uiState.first {
                        it.form is FormUiState.Ready && it.title.isNotEmpty()
                    }
                }

            assertThat(restored.title).isEqualTo("Borehole")
            assertThat(restored.category).isEqualTo(WaypointCategory.WATER_POINT)
            assertThat(restored.notes).isEqualTo("Pump broken")
            val fields = (restored.form as FormUiState.Ready).fields.associateBy { it.field.key }
            assertThat(fields.getValue("present").answer).isEqualTo(FormAnswer.Choice("yes"))
            assertThat(fields.getValue("crops").answer)
                .isEqualTo(FormAnswer.Choices(listOf("teff", "maize")))
            assertThat(fields.getValue("name").answer).isEqualTo(FormAnswer.Text("Abebe"))
            assertThat(fields.getValue("people").numberText).isEqualTo("4.")
        } finally {
            secondProcess.close()
        }
    }

    private fun openDatabase(): MeretDatabase = Room
        .databaseBuilder(context, MeretDatabase::class.java, DB_NAME)
        .addMeretMigrations()
        .build()

    private fun viewModel(database: MeretDatabase) = WaypointCaptureViewModel(
        devicePosition =
            object : DevicePositionRepository {
                override fun observePosition() = MutableStateFlow<DevicePosition?>(null)
            },
        tracking = FakeTrackingController(),
        saveWaypoint = SaveWaypointUseCase(NoWaypoints, NoPhotos),
        drafts =
            RoomWaypointDraftStore(
                dao = database.waypointDraftDao(),
                time = SystemTimeProvider(),
                dispatchers = StandardDispatcherProvider(),
            ),
        formSource = FixedForm,
        photos = NoPhotos,
        photoSweeper = FakePhotoSweeper(),
    )

    private suspend fun awaitDraft(
        database: MeretDatabase,
        done: (WaypointDraftEntity) -> Boolean,
    ): WaypointDraftEntity = withTimeout(TIMEOUT_MS) {
        var row = database.waypointDraftDao().getDraft(PROJECT_ID)
        while (row == null || !done(row)) {
            delay(POLL_MS)
            row = database.waypointDraftDao().getDraft(PROJECT_ID)
        }
        row
    }

    private object NoWaypoints : WaypointStore {
        override suspend fun save(waypoint: NewWaypoint): AppResult<String> =
            error("nothing is saved in this test")
    }

    private object FixedForm : WaypointFormSource {
        private val yesNo =
            listOf(
                SelectOption("yes", LocalizedText.of("Yes")),
                SelectOption("no", LocalizedText.of("No")),
            )

        override suspend fun currentForm() = WaypointForm(
            templateId = "template-1",
            version = 1,
            name = "Visit",
            schema =
                FormSchema(
                    format = 1,
                    fields =
                        listOf(
                            FormField.Select(
                                key = "present",
                                label = LocalizedText.of("Present?"),
                                options = yesNo,
                            ),
                            FormField.MultiSelect(
                                key = "crops",
                                label = LocalizedText.of("Crops"),
                                options =
                                    listOf(
                                        SelectOption("teff", LocalizedText.of("Teff")),
                                        SelectOption("maize", LocalizedText.of("Maize")),
                                    ),
                            ),
                            FormField.Text(key = "name", label = LocalizedText.of("Name")),
                            FormField.Number(key = "people", label = LocalizedText.of("People")),
                        ),
                ),
        )
    }

    private companion object {
        const val DB_NAME = "draft-process-death-test.db"
        const val PROJECT_ID = "local"
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 20L
    }
}
