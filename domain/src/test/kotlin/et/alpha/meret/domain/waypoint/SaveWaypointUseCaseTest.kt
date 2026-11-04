package et.alpha.meret.domain.waypoint

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.model.form.FormAnswer
import et.alpha.meret.core.model.form.FormField
import et.alpha.meret.core.model.form.FormSchema
import et.alpha.meret.core.model.form.LocalizedText
import et.alpha.meret.core.model.form.SelectOption
import et.alpha.meret.core.model.form.VisibleIf
import et.alpha.meret.core.model.geo.DevicePosition
import et.alpha.meret.core.model.geo.LatLng
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SaveWaypointUseCaseTest {
    private val store = FakeWaypointStore()
    private val photos = FakePhotoStore()
    private val saveWaypoint = SaveWaypointUseCase(store, photos)

    private val here =
        DevicePosition(
            position = LatLng(9.0301, 38.7402),
            accuracyMetres = 4.5,
            isLive = true,
            altitudeMetres = 2_355.0,
        )

    @Test
    fun `while idle the waypoint is saved where the phone is, on no track`() = runTest {
        val input =
            WaypointInput(
                title = "Borehole",
                category = WaypointCategory.WATER_POINT,
                notes = "Pump broken",
            )

        val result = saveWaypoint(fix = here, recordingTrackId = null, input = input)

        assertThat(result).isEqualTo(AppResult.Success("waypoint-1"))
        assertThat(store.saved)
            .containsExactly(
                NewWaypoint(
                    position = LatLng(9.0301, 38.7402),
                    accuracyMetres = 4.5,
                    altitudeMetres = 2_355.0,
                    trackId = null,
                    category = WaypointCategory.WATER_POINT,
                    title = "Borehole",
                    notes = "Pump broken",
                ),
            )
    }

    @Test
    fun `while recording the waypoint belongs to the track`() = runTest {
        saveWaypoint(fix = here, recordingTrackId = "track-7", input = WaypointInput())

        assertThat(store.saved.single().trackId).isEqualTo("track-7")
    }

    @Test
    fun `with no fix nothing is written`() = runTest {
        val result = saveWaypoint(fix = null, recordingTrackId = null, input = WaypointInput("A"))

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Gps(AppError.Gps.Kind.NoFix)))
        assertThat(store.saved).isEmpty()
    }

    @Test
    fun `a cached fix is not a position to save at`() = runTest {
        val cached = here.copy(isLive = false)

        val result = saveWaypoint(fix = cached, recordingTrackId = null, input = WaypointInput())

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Gps(AppError.Gps.Kind.NoFix)))
        assertThat(store.saved).isEmpty()
    }

    @Test
    fun `text is trimmed and blank text is saved as none`() = runTest {
        val input = WaypointInput(title = "  Kebele office \n", notes = "   ")

        saveWaypoint(fix = here, recordingTrackId = null, input = input)

        assertThat(store.saved.single().title).isEqualTo("Kebele office")
        assertThat(store.saved.single().notes).isNull()
        assertThat(store.saved.single().category).isNull()
    }

    @Test
    fun `a failed write is returned as it is`() = runTest {
        val failure = AppResult.Failure(AppError.Storage(AppError.Storage.Kind.OutOfSpace))
        store.failure = failure

        assertThat(saveWaypoint(fix = here, recordingTrackId = null, input = WaypointInput()))
            .isEqualTo(failure)
    }

    @Test
    fun `without a form no form columns are written`() = runTest {
        saveWaypoint(fix = here, recordingTrackId = null, input = WaypointInput())

        val saved = store.saved.single()
        assertThat(saved.formTemplateId).isNull()
        assertThat(saved.formVersion).isNull()
        assertThat(saved.formData).isNull()
    }

    @Test
    fun `a form with a missing required answer is refused and nothing is written`() = runTest {
        val input =
            WaypointInput(form = form, answers = mapOf("present" to FormAnswer.Choice("yes")))

        val result = saveWaypoint(fix = here, recordingTrackId = null, input = input)

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Invalid(setOf("name"))))
        assertThat(store.saved).isEmpty()
    }

    @Test
    fun `answers are saved under the template and version, hidden ones dropped`() = runTest {
        val answers =
            mapOf(
                "present" to FormAnswer.Choice("no"),
                // Typed before "present" was changed to "no", which hid the field.
                "name" to FormAnswer.Text("Abebe"),
                "legacy" to FormAnswer.Raw("42"),
            )

        val result =
            saveWaypoint(
                fix = here,
                recordingTrackId = null,
                input = WaypointInput(form = form, answers = answers),
            )

        assertThat(result).isEqualTo(AppResult.Success("waypoint-1"))
        val saved = store.saved.single()
        assertThat(saved.formTemplateId).isEqualTo("template-1")
        assertThat(saved.formVersion).isEqualTo(3)
        assertThat(saved.formData).isEqualTo("""{"present":"no","legacy":42}""")
    }

    @Test
    fun `a form left blank but valid is saved as an empty object`() = runTest {
        val optional =
            form.copy(
                schema =
                    FormSchema(
                        1,
                        listOf(
                            form.schema.fields
                                .first()
                                .copyOptional(),
                        ),
                    ),
            )

        saveWaypoint(fix = here, recordingTrackId = null, input = WaypointInput(form = optional))

        assertThat(store.saved.single().formData).isEqualTo("{}")
    }

    @Test
    fun `the photos the answers name are saved with the waypoint`() = runTest {
        photos.stored("p-1", "p-2")
        val answers =
            mapOf(
                "present" to FormAnswer.Choice("yes"),
                "name" to FormAnswer.Text("Abebe"),
                "farm_photos" to FormAnswer.Photos(listOf("p-1", "p-2")),
            )

        saveWaypoint(here, null, WaypointInput(form = photoForm, answers = answers))

        val saved = store.saved.single()
        assertThat(saved.photos.map { it.id }).containsExactly("p-1", "p-2").inOrder()
        assertThat(saved.photos.first().sha256).isEqualTo("sha-p-1")
        assertThat(saved.formData)
            .isEqualTo("""{"present":"yes","name":"Abebe","farm_photos":["p-1","p-2"]}""")
        assertThat(photos.deleted).isEmpty()
    }

    @Test
    fun `a photo whose file is gone is left out, and the rest is still saved`() = runTest {
        photos.stored("p-2")
        val answers =
            mapOf(
                "present" to FormAnswer.Choice("yes"),
                "name" to FormAnswer.Text("Abebe"),
                "farm_photos" to FormAnswer.Photos(listOf("p-1", "p-2")),
            )

        val result = saveWaypoint(here, null, WaypointInput(form = photoForm, answers = answers))

        assertThat(result).isEqualTo(AppResult.Success("waypoint-1"))
        val saved = store.saved.single()
        assertThat(saved.photos.map { it.id }).containsExactly("p-2")
        assertThat(saved.formData)
            .isEqualTo("""{"present":"yes","name":"Abebe","farm_photos":["p-2"]}""")
    }

    @Test
    fun `photos of a field the form hid are not saved, and their files are deleted`() = runTest {
        photos.stored("p-1")
        val answers =
            mapOf(
                "present" to FormAnswer.Choice("no"),
                // Taken while "present" was "yes"; "no" hid the field.
                "farm_photos" to FormAnswer.Photos(listOf("p-1")),
            )

        saveWaypoint(here, null, WaypointInput(form = photoForm, answers = answers))

        val saved = store.saved.single()
        assertThat(saved.photos).isEmpty()
        assertThat(saved.formData).isEqualTo("""{"present":"no"}""")
        assertThat(photos.deleted).containsExactly("p-1")
    }

    @Test
    fun `a failed write deletes no photo, so the draft can be saved again`() = runTest {
        photos.stored("p-1")
        store.failure = AppResult.Failure(AppError.Storage(AppError.Storage.Kind.WriteFailed))
        val answers =
            mapOf(
                "present" to FormAnswer.Choice("no"),
                "farm_photos" to FormAnswer.Photos(listOf("p-1")),
            )

        saveWaypoint(here, null, WaypointInput(form = photoForm, answers = answers))

        assertThat(photos.deleted).isEmpty()
    }

    @Test
    fun `a form that is refused reads and deletes no photo`() = runTest {
        photos.stored("p-1")
        val answers =
            mapOf(
                "present" to FormAnswer.Choice("yes"),
                "farm_photos" to FormAnswer.Photos(listOf("p-1")),
            )

        val result = saveWaypoint(here, null, WaypointInput(form = photoForm, answers = answers))

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Invalid(setOf("name"))))
        assertThat(photos.described).isEmpty()
        assertThat(photos.deleted).isEmpty()
    }

    @Test
    fun `category keys round-trip and unknown keys are no category`() {
        WaypointCategory.entries.forEach { category ->
            assertThat(WaypointCategory.fromKey(category.key)).isEqualTo(category)
        }
        assertThat(WaypointCategory.fromKey("granary")).isNull()
        assertThat(WaypointCategory.fromKey(null)).isNull()
        assertThat(WaypointCategory.entries.map { it.key }.toSet())
            .hasSize(WaypointCategory.entries.size)
    }

    private val form =
        WaypointForm(
            templateId = "template-1",
            version = 3,
            name = "Visit",
            schema =
                FormSchema(
                    format = 1,
                    fields =
                        listOf(
                            FormField.Select(
                                key = "present",
                                label = LocalizedText.of("Present?"),
                                required = true,
                                options =
                                    listOf(
                                        SelectOption("yes", LocalizedText.of("Yes")),
                                        SelectOption("no", LocalizedText.of("No")),
                                    ),
                            ),
                            FormField.Text(
                                key = "name",
                                label = LocalizedText.of("Name"),
                                required = true,
                                visibleIf = VisibleIf.Equals("present", "yes"),
                            ),
                        ),
                ),
        )

    private val photoForm =
        form.copy(
            schema =
                form.schema.copy(
                    fields =
                        form.schema.fields +
                            FormField.Photo(
                                key = "farm_photos",
                                label = LocalizedText.of("Photos of the farm"),
                                visibleIf = VisibleIf.Equals("present", "yes"),
                            ),
                ),
        )

    private fun FormField.copyOptional(): FormField =
        (this as FormField.Select).copy(required = false)

    private class FakeWaypointStore : WaypointStore {
        val saved = mutableListOf<NewWaypoint>()
        var failure: AppResult.Failure? = null

        override suspend fun save(waypoint: NewWaypoint): AppResult<String> {
            failure?.let { return it }
            saved += waypoint
            return AppResult.Success("waypoint-${saved.size}")
        }
    }

    private class FakePhotoStore : PhotoStore {
        private val files = mutableMapOf<String, StoredPhoto>()
        val described = mutableListOf<String>()
        val deleted = mutableListOf<String>()

        fun stored(vararg ids: String) {
            ids.forEach { id ->
                files[id] =
                    StoredPhoto(
                        id = id,
                        path = "/photos/$id.jpg",
                        width = 1600,
                        height = 1200,
                        bytes = 300_000,
                        sha256 = "sha-$id",
                        capturedAt = 1_000,
                    )
            }
        }

        override suspend fun store(capturePath: String): AppResult<StoredPhoto> = error("not used")

        override suspend fun describe(id: String): StoredPhoto? {
            described += id
            return files[id]
        }

        override suspend fun delete(id: String) {
            deleted += id
            files -= id
        }
    }
}
