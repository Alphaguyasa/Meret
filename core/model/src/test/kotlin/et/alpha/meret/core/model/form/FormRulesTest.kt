package et.alpha.meret.core.model.form

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormRulesTest {
    private val conditions = parseFixture("conditions.json")
    private val allTypes = parseFixture("all_types.json")

    @Test
    fun `with no answers, only unconditional fields and not-equals on a missing value show`() {
        val visible = FormRules.visibleKeys(conditions, emptyMap())

        // "size" shows: present has no value, and no value is not "no".
        assertThat(visible).containsExactly("present", "size", "crops").inOrder()
    }

    @Test
    fun `answering yes shows the name, and a hidden field hides what depends on it`() {
        val yes = mapOf("present" to choice("yes"), "name" to text("Abebe"), "size" to number(10.0))
        assertThat(FormRules.visibleKeys(conditions, yes)).contains("big")

        // Same answers, but "no": name and size hide, so "big" (which reads them) hides too,
        // even though their stored answers would satisfy it.
        val no = yes + ("present" to choice("no"))
        assertThat(
            FormRules.visibleKeys(conditions, no),
        ).containsExactly("present", "crops").inOrder()
    }

    @Test
    fun `or shows a field when any branch holds, and a multiselect equals any of its choices`() {
        val coffee = mapOf("crops" to choices("teff", "coffee"))
        val teff = mapOf("crops" to choices("teff"))

        assertThat(FormRules.visibleKeys(conditions, coffee)).contains("coffee_age")
        assertThat(FormRules.visibleKeys(conditions, teff)).doesNotContain("coffee_age")
    }

    @Test
    fun `in matches a number answer numerically`() {
        val answers = mapOf("present" to choice("yes"), "name" to text("A"), "size" to number(20.0))

        assertThat(FormRules.visibleKeys(conditions, answers)).contains("big")
        assertThat(FormRules.visibleKeys(conditions, answers + ("size" to number(15.0))))
            .doesNotContain("big")
    }

    @Test
    fun `a hidden required field is not required`() {
        val errors = FormRules.validate(conditions, mapOf("present" to choice("no")))

        assertThat(errors).isEmpty()
    }

    @Test
    fun `a visible required field with no answer, or a blank one, is required`() {
        assertThat(FormRules.validate(conditions, emptyMap()))
            .containsExactly("present", FieldError.Required)
        assertThat(
            FormRules.validate(
                conditions,
                mapOf(
                    "present" to choice("yes"),
                    "name" to text("  "),
                ),
            ),
        ).containsExactly("name", FieldError.Required)
    }

    @Test
    fun `optional empty answers are fine`() {
        val answers =
            mapOf(
                "name" to text("Abe"),
                "inputs" to choices(),
                "photos" to FormAnswer.Photos(emptyList()),
            )

        assertThat(FormRules.validate(allTypes, answers)).isEmpty()
    }

    @Test
    fun `each field type checks its own rules`() {
        val answers =
            mapOf(
                "name" to text("Abebech"),
                "count" to number(2.5),
                "crop" to choice("barley"),
                "inputs" to choices("seed", "compost", "none"),
                "planted" to FormAnswer.Date("2026-02-30"),
                "photos" to FormAnswer.Photos(listOf("p1", "p2", "p3")),
                "corner" to FormAnswer.Point(91.0, 38.7, 5.0),
                "signature" to FormAnswer.Raw("\"anything\""),
            )

        assertThat(FormRules.validate(allTypes, answers))
            .containsExactly(
                "name",
                FieldError.TooLong(5),
                "count",
                FieldError.NotWholeNumber,
                "crop",
                FieldError.UnknownOption,
                "inputs",
                FieldError.TooManyChoices(2),
                "planted",
                FieldError.InvalidDate,
                "photos",
                FieldError.TooManyPhotos(2),
                "corner",
                FieldError.InvalidPoint,
            )
    }

    @Test
    fun `number range, multiselect options and good answers`() {
        val base = mapOf("name" to text("Abe"))

        assertThat(FormRules.validate(allTypes, base + ("count" to number(0.0))))
            .containsExactly("count", FieldError.BelowMin(1.0))
        assertThat(FormRules.validate(allTypes, base + ("count" to number(11.0))))
            .containsExactly("count", FieldError.AboveMax(10.0))
        assertThat(FormRules.validate(allTypes, base + ("count" to number(Double.NaN))))
            .containsExactly("count", FieldError.WrongType)
        assertThat(FormRules.validate(allTypes, base + ("inputs" to choices("rain"))))
            .containsExactly("inputs", FieldError.UnknownOption)

        val good =
            base +
                mapOf(
                    "count" to number(4.0),
                    "crop" to choice("teff"),
                    "inputs" to choices("seed", "compost"),
                    "planted" to FormAnswer.Date("2024-02-29"),
                    "photos" to FormAnswer.Photos(listOf("p1")),
                    "corner" to FormAnswer.Point(9.03, 38.74, null),
                )
        assertThat(FormRules.validate(allTypes, good)).isEmpty()
    }

    @Test
    fun `the length limit counts characters, so Amharic is not cut short`() {
        // Five Ethiopic characters.
        assertThat(
            FormRules.validate(allTypes, mapOf("name" to text("አበበ በቀ"))),
        ).containsKey("name")
        assertThat(FormRules.validate(allTypes, mapOf("name" to text("አበበ በ")))).isEmpty()
    }

    @Test
    fun `an answer of the wrong kind is reported, for every field type`() {
        val wrong = text("x")
        val answers =
            allTypes.fields
                .filterNot { it is FormField.Text || it is FormField.Unsupported }
                .associate { it.key to wrong } + ("name" to number(1.0))

        val errors = FormRules.validate(allTypes, answers)

        assertThat(
            errors.keys,
        ).containsExactly("name", "count", "crop", "inputs", "planted", "photos", "corner")
        assertThat(errors.values.toSet()).containsExactly(FieldError.WrongType)
    }

    @Test
    fun `dates are checked against the real calendar`() {
        fun dateError(iso: String) = FormRules.validate(
            allTypes,
            mapOf(
                "name" to text("A"),
                "planted" to FormAnswer.Date(iso),
            ),
        )

        assertThat(dateError("2024-02-29")).isEmpty()
        assertThat(dateError("2000-02-29")).isEmpty()
        assertThat(dateError("2026-12-31")).isEmpty()
        assertThat(dateError("1900-02-29")).isNotEmpty()
        assertThat(dateError("2026-04-31")).isNotEmpty()
        assertThat(dateError("2026-13-01")).isNotEmpty()
        assertThat(dateError("2026-00-10")).isNotEmpty()
        assertThat(dateError("26-1-1")).isNotEmpty()
    }

    @Test
    fun `saving drops hidden answers and keeps everything else`() {
        val answers =
            mapOf(
                "present" to choice("no"),
                "name" to text("typed before no was chosen"),
                "crops" to choices("teff"),
                "from_an_older_version" to text("kept"),
            )

        assertThat(FormRules.answersToKeep(conditions, answers))
            .containsExactly(
                "present",
                choice("no"),
                "crops",
                choices("teff"),
                "from_an_older_version",
                text("kept"),
            )
    }

    @Test
    fun `unsupported answers are kept`() {
        val answers = mapOf("signature" to FormAnswer.Raw("{\"strokes\":3}"))

        assertThat(FormRules.answersToKeep(allTypes, answers)).isEqualTo(answers)
    }

    private fun text(value: String) = FormAnswer.Text(value)

    private fun number(value: Double) = FormAnswer.Number(value)

    private fun choice(value: String) = FormAnswer.Choice(value)

    private fun choices(vararg values: String) = FormAnswer.Choices(values.toList())
}
