package et.alpha.meret.core.model.form

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull

/**
 * Answers to and from `waypoint.form_data`: one JSON object, answer by field key (shapes in
 * `docs/FORM_SCHEMA.md`). The same text goes into Room and onto the wire.
 *
 * Reading never loses an answer. One whose field is unknown, unsupported, or of a different
 * type than its JSON suggests is kept as [FormAnswer.Raw] and written back exactly as it was.
 */
public object FormAnswersJson {
    private val parser = Json

    public fun encode(answers: Map<String, FormAnswer>): String = buildJsonObject {
        answers.forEach { (key, answer) -> put(key, answer.toJson()) }
    }.toString()

    /**
     * The answers in [formData] as [schema] types them. Null or blank is no answers. Text that
     * is not a JSON object cannot be read as answers and is returned as [Unreadable], so the
     * caller keeps the stored text rather than overwrite it.
     */
    public fun decode(
        formData: String?,
        schema: FormSchema?,
    ): Decoded {
        val root = if (formData.isNullOrBlank()) JsonObject(emptyMap()) else parseObject(formData)
        return if (root == null) {
            Decoded.Unreadable
        } else {
            Decoded.Answers(
                root.mapValues { (key, element) ->
                    schema?.field(key)?.let { element.toAnswer(it) }
                        ?: FormAnswer.Raw(element.toString())
                },
            )
        }
    }

    /** The JSON object in [text], or null when it is not one (or not JSON at all). */
    private fun parseObject(text: String): JsonObject? =
        runCatching { parser.parseToJsonElement(text) }.getOrNull() as? JsonObject

    public sealed interface Decoded {
        public data class Answers(
            public val answers: Map<String, FormAnswer>,
        ) : Decoded

        public data object Unreadable : Decoded
    }

    private fun FormAnswer.toJson(): JsonElement = when (this) {
        is FormAnswer.Text -> {
            JsonPrimitive(value)
        }

        is FormAnswer.Number -> {
            JsonPrimitive(value)
        }

        is FormAnswer.Choice -> {
            JsonPrimitive(value)
        }

        is FormAnswer.Choices -> {
            JsonArray(values.map(::JsonPrimitive))
        }

        is FormAnswer.Date -> {
            JsonPrimitive(isoDate)
        }

        is FormAnswer.Photos -> {
            JsonArray(photoIds.map(::JsonPrimitive))
        }

        is FormAnswer.Point -> {
            buildJsonObject {
                put(LATITUDE, JsonPrimitive(latitude))
                put(LONGITUDE, JsonPrimitive(longitude))
                accuracyMetres?.let { put(ACCURACY, JsonPrimitive(it)) }
            }
        }

        is FormAnswer.Raw -> {
            parser.parseToJsonElement(json)
        }
    }

    private fun JsonElement.toAnswer(field: FormField): FormAnswer {
        val typed =
            when (field) {
                is FormField.Text -> text()?.let(FormAnswer::Text)
                is FormField.Number -> number()?.let(FormAnswer::Number)
                is FormField.Select -> text()?.let(FormAnswer::Choice)
                is FormField.MultiSelect -> texts()?.let(FormAnswer::Choices)
                is FormField.Date -> text()?.let(FormAnswer::Date)
                is FormField.Photo -> texts()?.let(FormAnswer::Photos)
                is FormField.GpsPoint -> point()
                is FormField.Unsupported -> null
            }
        return typed ?: FormAnswer.Raw(toString())
    }

    private fun JsonElement.text(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonElement.number(): Double? = (this as? JsonPrimitive)
        ?.takeUnless {
            it.isString
        }?.doubleOrNull

    private fun JsonElement.texts(): List<String>? {
        val array = this as? JsonArray ?: return null
        val texts = array.mapNotNull { it.text() }
        return texts.takeIf { it.size == array.size }
    }

    private fun JsonElement.point(): FormAnswer.Point? {
        val obj = this as? JsonObject
        val latitude = obj?.get(LATITUDE)?.number()
        val longitude = obj?.get(LONGITUDE)?.number()
        return if (latitude != null && longitude != null) {
            FormAnswer.Point(latitude, longitude, obj[ACCURACY]?.number())
        } else {
            null
        }
    }

    private const val LATITUDE = "lat"
    private const val LONGITUDE = "lon"
    private const val ACCURACY = "accuracy_m"
}
