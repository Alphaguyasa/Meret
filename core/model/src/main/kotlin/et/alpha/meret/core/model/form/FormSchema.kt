package et.alpha.meret.core.model.form

/**
 * A waypoint form, parsed from `form_template.schema_json` by [FormSchemaParser]. The format
 * is documented in `docs/FORM_SCHEMA.md`.
 *
 * [fields] keep the template's order, which is the order the form shows them in and the order
 * `visible_if` may refer back along: a condition only ever names a field before its own.
 */
public data class FormSchema(
    public val format: Int,
    public val fields: List<FormField>,
) {
    /** The field saved under [key], or null. */
    public fun field(key: String): FormField? = fields.firstOrNull { it.key == key }
}

/**
 * Text in the languages the template provides. A plain JSON string in the template is the same
 * text for every language.
 */
public data class LocalizedText(
    /** Language code (`en`, `am`) to text. Never empty. */
    public val byLanguage: Map<String, String>,
) {
    /**
     * The text for [language], else English, else whichever the template gave first: a label
     * in the wrong language is better than a blank one.
     */
    public fun resolve(language: String): String = byLanguage[language]
        ?: byLanguage[FALLBACK_LANGUAGE]
        ?: byLanguage.values.first()

    public companion object {
        public const val FALLBACK_LANGUAGE: String = "en"

        /** The same [text] for every language. */
        public fun of(text: String): LocalizedText = LocalizedText(mapOf(FALLBACK_LANGUAGE to text))
    }
}

/** One choice of a select or multiselect. [value] is what is stored; [label] what is shown. */
public data class SelectOption(
    public val value: String,
    public val label: LocalizedText,
)

/** One question on the form. */
public sealed interface FormField {
    /** The answer's key in `waypoint.form_data`. Unique within the form. */
    public val key: String
    public val label: LocalizedText
    public val hint: LocalizedText?

    /** Whether a visible field must be answered. A hidden field never is. */
    public val required: Boolean

    /** When the field shows; null is always. */
    public val visibleIf: VisibleIf?

    public data class Text(
        override val key: String,
        override val label: LocalizedText,
        override val hint: LocalizedText? = null,
        override val required: Boolean = false,
        override val visibleIf: VisibleIf? = null,
        public val multiline: Boolean = false,
        /** Longest answer in characters, or null for no limit. */
        public val maxLength: Int? = null,
    ) : FormField

    public data class Number(
        override val key: String,
        override val label: LocalizedText,
        override val hint: LocalizedText? = null,
        override val required: Boolean = false,
        override val visibleIf: VisibleIf? = null,
        public val min: Double? = null,
        public val max: Double? = null,
        /** Whole numbers only (a count of people, of trees). */
        public val integer: Boolean = false,
        /** Shown after the box, such as "ha" or "kg". Not translated: units are symbols. */
        public val unit: String? = null,
    ) : FormField

    public data class Select(
        override val key: String,
        override val label: LocalizedText,
        override val hint: LocalizedText? = null,
        override val required: Boolean = false,
        override val visibleIf: VisibleIf? = null,
        public val options: List<SelectOption>,
    ) : FormField

    public data class MultiSelect(
        override val key: String,
        override val label: LocalizedText,
        override val hint: LocalizedText? = null,
        override val required: Boolean = false,
        override val visibleIf: VisibleIf? = null,
        public val options: List<SelectOption>,
        /** Most choices allowed, or null for any number. */
        public val maxSelected: Int? = null,
    ) : FormField

    /** A calendar date, without a time or zone: stored as `yyyy-MM-dd` (Gregorian). */
    public data class Date(
        override val key: String,
        override val label: LocalizedText,
        override val hint: LocalizedText? = null,
        override val required: Boolean = false,
        override val visibleIf: VisibleIf? = null,
    ) : FormField

    /** Photos taken for this field; the answer holds `waypoint_photo` ids. */
    public data class Photo(
        override val key: String,
        override val label: LocalizedText,
        override val hint: LocalizedText? = null,
        override val required: Boolean = false,
        override val visibleIf: VisibleIf? = null,
        public val maxCount: Int = DEFAULT_MAX_PHOTOS,
    ) : FormField

    /** A position, captured from a fix: a second place besides the waypoint's own. */
    public data class GpsPoint(
        override val key: String,
        override val label: LocalizedText,
        override val hint: LocalizedText? = null,
        override val required: Boolean = false,
        override val visibleIf: VisibleIf? = null,
    ) : FormField

    /**
     * A field type this build does not know, from a newer template. It is shown read-only and
     * its answer, if any, is kept as it came: never dropped, never required.
     */
    public data class Unsupported(
        override val key: String,
        override val label: LocalizedText,
        public val type: String,
        override val hint: LocalizedText? = null,
        override val visibleIf: VisibleIf? = null,
    ) : FormField {
        override val required: Boolean get() = false
    }

    public companion object {
        public const val DEFAULT_MAX_PHOTOS: Int = 3
    }
}
