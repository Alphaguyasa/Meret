package et.alpha.meret.core.model.form

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormSchemaParserTest {
    @Test
    fun `every field type is read with its options, in template order`() {
        val schema = parseFixture("all_types.json")

        assertThat(schema.format).isEqualTo(1)
        assertThat(schema.fields.map { it.key })
            .containsExactly(
                "name",
                "count",
                "crop",
                "inputs",
                "planted",
                "photos",
                "corner",
                "signature",
            ).inOrder()
        assertThat(schema.field("name"))
            .isEqualTo(
                FormField.Text(
                    key = "name",
                    label = LocalizedText.of("Name"),
                    hint = LocalizedText(mapOf("en" to "Full name", "am" to "ሙሉ ስም")),
                    required = true,
                    multiline = true,
                    maxLength = 5,
                ),
            )
        assertThat(schema.field("count"))
            .isEqualTo(
                FormField.Number(
                    key = "count",
                    label = LocalizedText.of("Count"),
                    min = 1.0,
                    max = 10.0,
                    integer = true,
                    unit = "people",
                ),
            )
        val crop = schema.field("crop") as FormField.Select
        assertThat(crop.options.map { it.value }).containsExactly("teff", "maize").inOrder()
        assertThat(crop.options[0].label.resolve("am")).isEqualTo("ጤፍ")
        // An option without a label shows its value.
        assertThat(crop.options[1].label.resolve("am")).isEqualTo("maize")
        assertThat((schema.field("inputs") as FormField.MultiSelect).maxSelected).isEqualTo(2)
        assertThat(schema.field("planted")).isInstanceOf(FormField.Date::class.java)
        assertThat((schema.field("photos") as FormField.Photo).maxCount).isEqualTo(2)
        assertThat(schema.field("corner")).isInstanceOf(FormField.GpsPoint::class.java)
    }

    @Test
    fun `an unknown field type is kept as unsupported, never dropped, never required`() {
        val field = parseFixture("all_types.json").field("signature")

        assertThat(field)
            .isEqualTo(
                FormField.Unsupported("signature", LocalizedText.of("Signature"), "signature"),
            )
        assertThat(field?.required).isFalse()
    }

    @Test
    fun `defaults are format 1, not required, three photos, no limits`() {
        val schema =
            parseValid(
                """{"fields":[
                  {"key":"p","type":"photo","label":"P"},
                  {"key":"t","type":"text","label":"T"}
                ]}""",
            )

        assertThat(schema.format).isEqualTo(FormSchemaParser.FORMAT)
        val photo = schema.field("p") as FormField.Photo
        assertThat(photo.maxCount).isEqualTo(FormField.DEFAULT_MAX_PHOTOS)
        assertThat(photo.required).isFalse()
        val text = schema.field("t") as FormField.Text
        assertThat(text.maxLength).isNull()
        assertThat(text.multiline).isFalse()
        assertThat(text.visibleIf).isNull()
    }

    @Test
    fun `conditions are read, nested and or included`() {
        val schema = parseFixture("conditions.json")

        assertThat(schema.field("name")?.visibleIf).isEqualTo(VisibleIf.Equals("present", "yes"))
        assertThat(schema.field("size")?.visibleIf).isEqualTo(VisibleIf.NotEquals("present", "no"))
        assertThat(schema.field("big")?.visibleIf)
            .isEqualTo(
                VisibleIf.And(
                    listOf(
                        VisibleIf.In("size", listOf("10", "20")),
                        VisibleIf.NotEquals("name", ""),
                    ),
                ),
            )
        assertThat(schema.field("coffee_age")?.visibleIf?.fieldKeys).containsExactly("crops", "big")
    }

    @Test
    fun `the bundled farm visit has twelve fields and parses`() {
        val template = BundledFormTemplates.farmVisit(projectId = "local")
        val schema = parseValid(template.schemaJson)

        assertThat(template.id).isEqualTo(BundledFormTemplates.FARM_VISIT_ID)
        assertThat(template.projectId).isEqualTo("local")
        assertThat(schema.fields).hasSize(12)
        assertThat(schema.fields.none { it is FormField.Unsupported }).isTrue()
        // Every label has Amharic.
        assertThat(schema.fields.all { "am" in it.label.byLanguage }).isTrue()
    }

    @Test
    fun `a label falls back to English, then to whatever language there is`() {
        val both = LocalizedText(mapOf("am" to "ሰብል", "en" to "Crop"))
        val amharicOnly = LocalizedText(mapOf("am" to "ሰብል"))

        assertThat(both.resolve("am")).isEqualTo("ሰብል")
        assertThat(both.resolve("om")).isEqualTo("Crop")
        assertThat(amharicOnly.resolve("en")).isEqualTo("ሰብል")
    }

    @Test
    fun `templates that would lose or corrupt answers are refused with a reason`() {
        assertAllInvalid(
            mapOf(
                "not json" to "not valid JSON",
                "[]" to "not a JSON object",
                "{}" to "fields is missing",
                """{"format":2,"fields":[]}""" to "format 2 is newer",
                """{"format":"one","fields":[]}""" to "format is not a number",
                """{"fields":[1]}""" to "field 1 is not an object",
                """{"fields":[{"type":"text","label":"A"}]}""" to "field 1 has no key",
                """{"fields":[{"key":" ","type":"text","label":"A"}]}""" to "field 1 has no key",
                """{"fields":[{"key":"a","label":"A"}]}""" to "field 'a' has no type",
                """{"fields":[{"key":"a","type":"text"}]}""" to "field 'a' has no label",
                """{"fields":[{"key":"a","type":"text","label":""}]}""" to "label is empty",
                """{"fields":[{"key":"a","type":"text","label":{}}]}""" to "label is empty",
                """{"fields":[{"key":"a","type":"text","label":{"en":1}}]}""" to "'en' is not text",
                """{"fields":[{"key":"a","type":"text","label":5}]}""" to
                    "neither text nor a language map",
                """{"fields":[
                    {"key":"a","type":"text","label":"A"},
                    {"key":"a","type":"date","label":"B"}]}"""
                    to "'a' is used twice",
                """{"fields":[{"key":"a","type":"select","label":"A"}]}""" to "has no options",
                """{"fields":[{"key":"a","type":"select","label":"A","options":[]}]}""" to
                    "has no options",
                """{"fields":[{"key":"a","type":"select","label":"A","options":[1]}]}""" to
                    "is not an object",
                """{"fields":[{"key":"a","type":"select","label":"A","options":[{"label":"x"}]}]}"""
                    to "an option has no value",
                """{"fields":[
                    {"key":"a","type":"select","label":"A","options":[
                        {"value":"x"},{"value":"x"}]}]}"""
                    to "option 'x' is listed twice",
                """{"fields":[{"key":"a","type":"number","label":"A","min":5,"max":1}]}""" to
                    "min is above max",
                """{"fields":[{"key":"a","type":"number","label":"A","min":"5"}]}""" to
                    "min is not a number",
                """{"fields":[{"key":"a","type":"number","label":"A","max":[]}]}""" to
                    "max is not a number",
                """{"fields":[{"key":"a","type":"text","label":"A","required":"yes"}]}"""
                    to "required is not true or false",
                """{"fields":[{"key":"a","type":"text","label":"A","max_length":0}]}"""
                    to "max_length is not a whole number above 0",
                """{"fields":[{"key":"a","type":"photo","label":"A","max_count":"3"}]}"""
                    to "max_count is not a whole number above 0",
            ),
        )
    }

    @Test
    fun `a visible_if that cannot be evaluated refuses the template`() {
        assertAllInvalid(
            mapOf(
                """{"fields":[
                    {"key":"a","type":"text","label":"A","visible_if":{"field":"b","equals":"x"}},
                    {"key":"b","type":"text","label":"B"}]}""" to
                    "names 'b', which is not an earlier field",
                """{"fields":[
                    {"key":"a","type":"text","label":"A",
                        "visible_if":{"field":"a","equals":"x"}}]}"""
                    to "names 'a', which is not an earlier field",
                """{"fields":[{"key":"a","type":"text","label":"A"},
                    {"key":"b","type":"text","label":"B",
                        "visible_if":{"field":"a","contains":"x"}}]}"""
                    to "visible_if needs equals",
                """{"fields":[{"key":"a","type":"text","label":"A"},
                    {"key":"b","type":"text","label":"B","visible_if":{"equals":"x"}}]}""" to
                    "visible_if has no field",
                """{"fields":[{"key":"a","type":"text","label":"A"},
                    {"key":"b","type":"text","label":"B","visible_if":"a"}]}""" to
                    "visible_if is not an object",
                """{"fields":[{"key":"a","type":"text","label":"A"},
                    {"key":"b","type":"text","label":"B","visible_if":{"and":[]}}]}""" to
                    "'and' needs a list",
                """{"fields":[{"key":"a","type":"text","label":"A"},
                    {"key":"b","type":"text","label":"B","visible_if":{"field":"a","in":"x"}}]}"""
                    to "'in' is not a list",
                """{"fields":[{"key":"a","type":"text","label":"A"},
                    {"key":"b","type":"text","label":"B",
                        "visible_if":{"field":"a","equals":null}}]}"""
                    to "not a value",
                """{"fields":[{"key":"a","type":"text","label":"A"},
                    {"key":"b","type":"text","label":"B",
                        "visible_if":{"field":"a","equals":{}}}]}"""
                    to "not a value",
            ),
        )
    }

    private fun assertAllInvalid(cases: Map<String, String>) {
        cases.forEach { (json, reason) ->
            val result = FormSchemaParser.parse(json)
            assertThat(result).isInstanceOf(FormSchemaResult.Invalid::class.java)
            assertThat((result as FormSchemaResult.Invalid).reason).contains(reason)
        }
    }

    @Test
    fun `a condition compares with numbers and booleans as their text`() {
        val schema =
            parseValid(
                """{"fields":[
                  {"key":"a","type":"number","label":"A"},
                  {"key":"b","type":"text","label":"B","visible_if":{"field":"a","equals":3}},
                  {"key":"c","type":"text","label":"C","visible_if":{"field":"a","not_equals":true}}
                ]}""",
            )

        assertThat(schema.field("b")?.visibleIf).isEqualTo(VisibleIf.Equals("a", "3"))
        assertThat(schema.field("c")?.visibleIf).isEqualTo(VisibleIf.NotEquals("a", "true"))
    }
}

internal fun parseValid(json: String): FormSchema {
    val result = FormSchemaParser.parse(json)
    check(result is FormSchemaResult.Valid) { "expected a valid template, got $result" }
    return result.schema
}

internal fun parseFixture(name: String): FormSchema {
    val stream = checkNotNull(FormSchemaParserTest::class.java.getResourceAsStream("/forms/$name"))
    return parseValid(stream.bufferedReader().use { it.readText() })
}
