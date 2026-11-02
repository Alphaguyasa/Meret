package et.alpha.meret.core.model.form

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** What [FormSchemaParser.parse] made of a template. */
public sealed interface FormSchemaResult {
    public data class Valid(
        public val schema: FormSchema,
    ) : FormSchemaResult

    /**
     * The template cannot be used. [reason] names the field and the rule, for the diagnostics
     * report and the supervisor; it holds template text only, never an answer.
     */
    public data class Invalid(
        public val reason: String,
    ) : FormSchemaResult
}

/**
 * Reads `form_template.schema_json` (format in `docs/FORM_SCHEMA.md`).
 *
 * Lenient where a newer template may add things: unknown properties are ignored and an
 * unknown field `type` becomes [FormField.Unsupported]. Strict where a mistake would lose or
 * corrupt answers: duplicate keys, a select without options, an impossible number range, or a
 * `visible_if` that names a field that is not before it make the whole template
 * [Invalid][FormSchemaResult.Invalid].
 */
public object FormSchemaParser {
    /** The newest format this build reads. A higher `format` is a breaking change. */
    public const val FORMAT: Int = 1

    private val json = Json { isLenient = false }

    public fun parse(schemaJson: String): FormSchemaResult = try {
        val root =
            json.parseToJsonElement(schemaJson) as? JsonObject
                ?: fail("the template is not a JSON object")
        FormSchemaResult.Valid(parseSchema(root))
    } catch (e: FormSchemaException) {
        FormSchemaResult.Invalid(e.message.orEmpty())
    } catch (e: SerializationException) {
        FormSchemaResult.Invalid("not valid JSON: ${e.message}")
    }

    private fun parseSchema(root: JsonObject): FormSchema {
        val format =
            root["format"]?.let {
                (it as? JsonPrimitive)?.intOrNull
                    ?: fail("format is not a number")
            }
                ?: FORMAT
        if (format > FORMAT) fail("format $format is newer than this app reads ($FORMAT)")
        val elements = root["fields"] as? JsonArray ?: fail("fields is missing or not a list")
        val fields = mutableListOf<FormField>()
        elements.forEachIndexed { index, element ->
            val obj = element as? JsonObject ?: fail("field ${index + 1} is not an object")
            val field = parseField(obj, index)
            if (fields.any { it.key == field.key }) fail("field key '${field.key}' is used twice")
            field.visibleIf?.fieldKeys?.forEach { referenced ->
                if (fields.none { it.key == referenced }) {
                    fail(
                        "field '${field.key}': visible_if names '$referenced', " +
                            "which is not an earlier field",
                    )
                }
            }
            fields += field
        }
        return FormSchema(format, fields)
    }

    private fun parseField(
        obj: JsonObject,
        index: Int,
    ): FormField {
        val key =
            obj.string("key")?.takeIf { it.isNotBlank() } ?: fail("field ${index + 1} has no key")
        val type = obj.string("type") ?: fail("field '$key' has no type")
        return buildField(type, obj, parseCommon(obj, key))
    }

    private fun parseCommon(
        obj: JsonObject,
        key: String,
    ) = Common(
        key = key,
        label =
            obj["label"]?.let { parseText(it, "field '$key' label") }
                ?: fail("field '$key' has no label"),
        hint = obj["hint"]?.let { parseText(it, "field '$key' hint") },
        required = obj.boolean("required", key) ?: false,
        visibleIf = obj["visible_if"]?.let { parseCondition(it, key) },
    )

    private fun buildField(
        type: String,
        obj: JsonObject,
        common: Common,
    ): FormField = with(common) {
        when (type) {
            "text" -> textField(obj, common)
            "number" -> numberField(obj, common)
            "select" -> selectField(obj, common)
            "multiselect" -> multiSelectField(obj, common)
            "date" -> FormField.Date(key, label, hint, required, visibleIf)
            "photo" -> photoField(obj, common)
            "gps_point" -> FormField.GpsPoint(key, label, hint, required, visibleIf)
            else -> FormField.Unsupported(key, label, type, hint, visibleIf)
        }
    }

    /** What every field type has. */
    private data class Common(
        val key: String,
        val label: LocalizedText,
        val hint: LocalizedText?,
        val required: Boolean,
        val visibleIf: VisibleIf?,
    )

    private fun textField(
        obj: JsonObject,
        c: Common,
    ) = FormField.Text(
        key = c.key,
        label = c.label,
        hint = c.hint,
        required = c.required,
        visibleIf = c.visibleIf,
        multiline = obj.boolean("multiline", c.key) ?: false,
        maxLength = obj.positiveInt("max_length", c.key),
    )

    private fun numberField(
        obj: JsonObject,
        c: Common,
    ): FormField.Number {
        val min = obj.number("min", c.key)
        val max = obj.number("max", c.key)
        if (min != null && max != null && min > max) fail("field '${c.key}': min is above max")
        return FormField.Number(
            key = c.key,
            label = c.label,
            hint = c.hint,
            required = c.required,
            visibleIf = c.visibleIf,
            min = min,
            max = max,
            integer = obj.boolean("integer", c.key) ?: false,
            unit = obj.string("unit"),
        )
    }

    private fun selectField(
        obj: JsonObject,
        c: Common,
    ) = FormField.Select(
        key = c.key,
        label = c.label,
        hint = c.hint,
        required = c.required,
        visibleIf = c.visibleIf,
        options = parseOptions(obj, c.key),
    )

    private fun multiSelectField(
        obj: JsonObject,
        c: Common,
    ) = FormField.MultiSelect(
        key = c.key,
        label = c.label,
        hint = c.hint,
        required = c.required,
        visibleIf = c.visibleIf,
        options = parseOptions(obj, c.key),
        maxSelected = obj.positiveInt("max_selected", c.key),
    )

    private fun photoField(
        obj: JsonObject,
        c: Common,
    ) = FormField.Photo(
        key = c.key,
        label = c.label,
        hint = c.hint,
        required = c.required,
        visibleIf = c.visibleIf,
        maxCount = obj.positiveInt("max_count", c.key) ?: FormField.DEFAULT_MAX_PHOTOS,
    )

    private fun parseOptions(
        obj: JsonObject,
        key: String,
    ): List<SelectOption> {
        val array = obj["options"] as? JsonArray
        if (array.isNullOrEmpty()) fail("field '$key' has no options")
        val options =
            array.map { element ->
                val option =
                    element as? JsonObject ?: fail("field '$key': an option is not an object")
                val value =
                    option.string("value")?.takeIf { it.isNotEmpty() }
                        ?: fail("field '$key': an option has no value")
                val label = option["label"]?.let { parseText(it, "field '$key' option '$value'") }
                SelectOption(value, label ?: LocalizedText.of(value))
            }
        val duplicate = options.groupBy { it.value }.entries.firstOrNull { it.value.size > 1 }
        if (duplicate != null) fail("field '$key': option '${duplicate.key}' is listed twice")
        return options
    }

    private fun parseText(
        element: JsonElement,
        what: String,
    ): LocalizedText {
        if (element is JsonPrimitive && element.isString) {
            if (element.content.isBlank()) fail("$what is empty")
            return LocalizedText.of(element.content)
        }
        val obj = element as? JsonObject ?: fail("$what is neither text nor a language map")
        val byLanguage =
            obj
                .mapValues { (language, value) ->
                    val text = value as? JsonPrimitive
                    if (text == null || !text.isString) fail("$what: '$language' is not text")
                    text.content
                }.filterValues { it.isNotBlank() }
        if (byLanguage.isEmpty()) fail("$what is empty")
        return LocalizedText(byLanguage)
    }

    private fun parseCondition(
        element: JsonElement,
        key: String,
    ): VisibleIf {
        val obj = element as? JsonObject ?: fail("field '$key': visible_if is not an object")
        return when {
            "and" in obj -> VisibleIf.And(parseConditions(obj.getValue("and"), key, "and"))
            "or" in obj -> VisibleIf.Or(parseConditions(obj.getValue("or"), key, "or"))
            else -> parseComparison(obj, key)
        }
    }

    private fun parseComparison(
        obj: JsonObject,
        key: String,
    ): VisibleIf {
        val fieldKey = obj.string("field") ?: fail("field '$key': visible_if has no field")
        return when {
            "equals" in obj -> {
                VisibleIf.Equals(fieldKey, scalar(obj.getValue("equals"), key))
            }

            "not_equals" in obj -> {
                VisibleIf.NotEquals(fieldKey, scalar(obj.getValue("not_equals"), key))
            }

            "in" in obj -> {
                VisibleIf.In(fieldKey, scalarList(obj.getValue("in"), key))
            }

            else -> {
                fail("field '$key': visible_if needs equals, not_equals, in, and or or")
            }
        }
    }

    private fun scalarList(
        element: JsonElement,
        key: String,
    ): List<String> {
        val array = element as? JsonArray ?: fail("field '$key': visible_if 'in' is not a list")
        return array.map { scalar(it, key) }
    }

    private fun parseConditions(
        element: JsonElement,
        key: String,
        operator: String,
    ): List<VisibleIf> {
        val array = element as? JsonArray
        if (array.isNullOrEmpty()) {
            fail(
                "field '$key': visible_if '$operator' needs a list of conditions",
            )
        }
        return array.map { parseCondition(it, key) }
    }

    /** A comparison value: text, number or true/false, compared as its text. */
    private fun scalar(
        element: JsonElement,
        key: String,
    ): String {
        val primitive = element as? JsonPrimitive
        if (primitive == null || (primitive.content == "null" && !primitive.isString)) {
            fail("field '$key': visible_if compares with something that is not a value")
        }
        return primitive.content
    }
}
