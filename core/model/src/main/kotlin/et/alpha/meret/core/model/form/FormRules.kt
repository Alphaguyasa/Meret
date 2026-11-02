package et.alpha.meret.core.model.form

/** Why one field's answer cannot be saved as it is. The renderer turns it into a message. */
public sealed interface FieldError {
    /** A visible required field has no answer. */
    public data object Required : FieldError

    /** The answer is not the kind the field takes (text for a number field, say). */
    public data object WrongType : FieldError

    public data class TooLong(
        public val maxLength: Int,
    ) : FieldError

    public data class BelowMin(
        public val min: Double,
    ) : FieldError

    public data class AboveMax(
        public val max: Double,
    ) : FieldError

    public data object NotWholeNumber : FieldError

    /** A choice that is not among the field's options. */
    public data object UnknownOption : FieldError

    public data class TooManyChoices(
        public val maxSelected: Int,
    ) : FieldError

    /** Not a real `yyyy-MM-dd` date. */
    public data object InvalidDate : FieldError

    public data class TooManyPhotos(
        public val maxCount: Int,
    ) : FieldError

    /** Latitude or longitude out of range. */
    public data object InvalidPoint : FieldError
}

/**
 * What a form's answers mean under its schema: which fields show, which answers are wrong, and
 * which answers are kept when the waypoint is saved.
 */
public object FormRules {
    /**
     * The keys of the fields that show for [answers], in form order. Conditions are read in
     * order, and only the answers of fields already found visible count: a field that depends
     * on a hidden one sees no value there, so hiding cascades.
     */
    public fun visibleKeys(
        schema: FormSchema,
        answers: Map<String, FormAnswer>,
    ): Set<String> {
        val visible = linkedSetOf<String>()
        val visibleAnswers = mutableMapOf<String, FormAnswer>()
        for (field in schema.fields) {
            if (field.visibleIf?.holds(visibleAnswers) != false) {
                visible += field.key
                answers[field.key]?.let { visibleAnswers[field.key] = it }
            }
        }
        return visible
    }

    /**
     * Every visible field's problem, by key; empty when the form can be saved. Hidden fields
     * are never checked, so they are neither required nor wrong.
     */
    public fun validate(
        schema: FormSchema,
        answers: Map<String, FormAnswer>,
    ): Map<String, FieldError> {
        val visible = visibleKeys(schema, answers)
        val errors = linkedMapOf<String, FieldError>()
        for (field in schema.fields) {
            if (field.key !in visible) continue
            val answer = answers[field.key]
            val error =
                if (answer == null || answer.isBlank()) {
                    FieldError.Required.takeIf { field.required }
                } else {
                    check(field, answer)
                }
            if (error != null) errors[field.key] = error
        }
        return errors
    }

    /**
     * The answers to store: hidden fields' answers are dropped, as the template means them to
     * be. Everything else is kept, including answers to unsupported fields and to keys this
     * schema does not have (a waypoint started on an older template version): an answer the
     * worker collected is only ever dropped because the template hid its field.
     */
    public fun answersToKeep(
        schema: FormSchema,
        answers: Map<String, FormAnswer>,
    ): Map<String, FormAnswer> {
        val visible = visibleKeys(schema, answers)
        return answers.filterKeys { key -> schema.field(key) == null || key in visible }
    }

    private fun FormAnswer.isBlank(): Boolean = when (this) {
        is FormAnswer.Text -> value.isBlank()
        is FormAnswer.Choices -> values.isEmpty()
        is FormAnswer.Photos -> photoIds.isEmpty()
        else -> false
    }

    private fun check(
        field: FormField,
        answer: FormAnswer,
    ): FieldError? = when (field) {
        is FormField.Text -> checkText(field, answer)
        is FormField.Number -> checkNumber(field, answer)
        is FormField.Select -> checkSelect(field, answer)
        is FormField.MultiSelect -> checkMultiSelect(field, answer)
        is FormField.Date -> checkDate(answer)
        is FormField.Photo -> checkPhotos(field, answer)
        is FormField.GpsPoint -> checkPoint(answer)
        is FormField.Unsupported -> null
    }

    private fun checkText(
        field: FormField.Text,
        answer: FormAnswer,
    ): FieldError? {
        if (answer !is FormAnswer.Text) return FieldError.WrongType
        val max = field.maxLength
        return if (max != null && answer.value.codePointCount(0, answer.value.length) > max) {
            FieldError.TooLong(max)
        } else {
            null
        }
    }

    private fun checkNumber(
        field: FormField.Number,
        answer: FormAnswer,
    ): FieldError? {
        if (answer !is FormAnswer.Number || !answer.value.isFinite()) return FieldError.WrongType
        val value = answer.value
        val min = field.min
        val max = field.max
        return when {
            field.integer && value != Math.rint(value) -> FieldError.NotWholeNumber
            min != null && value < min -> FieldError.BelowMin(min)
            max != null && value > max -> FieldError.AboveMax(max)
            else -> null
        }
    }

    private fun checkSelect(
        field: FormField.Select,
        answer: FormAnswer,
    ): FieldError? = when {
        answer !is FormAnswer.Choice -> FieldError.WrongType
        field.options.none { it.value == answer.value } -> FieldError.UnknownOption
        else -> null
    }

    private fun checkMultiSelect(
        field: FormField.MultiSelect,
        answer: FormAnswer,
    ): FieldError? {
        if (answer !is FormAnswer.Choices) return FieldError.WrongType
        val max = field.maxSelected
        return when {
            answer.values.any { value ->
                field.options.none { it.value == value }
            } -> FieldError.UnknownOption

            max != null && answer.values.distinct().size > max -> FieldError.TooManyChoices(max)

            else -> null
        }
    }

    private fun checkDate(answer: FormAnswer): FieldError? = when {
        answer !is FormAnswer.Date -> FieldError.WrongType
        !IsoDate.isValid(answer.isoDate) -> FieldError.InvalidDate
        else -> null
    }

    private fun checkPhotos(
        field: FormField.Photo,
        answer: FormAnswer,
    ): FieldError? = when {
        answer !is FormAnswer.Photos -> FieldError.WrongType
        answer.photoIds.size > field.maxCount -> FieldError.TooManyPhotos(field.maxCount)
        else -> null
    }

    private fun checkPoint(answer: FormAnswer): FieldError? = when {
        answer !is FormAnswer.Point -> FieldError.WrongType

        answer.latitude !in MIN_LATITUDE..MAX_LATITUDE ||
            answer.longitude !in MIN_LONGITUDE..MAX_LONGITUDE -> FieldError.InvalidPoint

        else -> null
    }

    private const val MIN_LATITUDE = -90.0
    private const val MAX_LATITUDE = 90.0
    private const val MIN_LONGITUDE = -180.0
    private const val MAX_LONGITUDE = 180.0
}

/** `yyyy-MM-dd`, Gregorian, checked without `java.time` so this module stays plain Kotlin. */
internal object IsoDate {
    private val pattern = Regex("""(\d{4})-(\d{2})-(\d{2})""")

    fun isValid(text: String): Boolean {
        val match = pattern.matchEntire(text) ?: return false
        val (year, month, day) = match.destructured.toList().map { it.toInt() }
        return month in 1..MONTHS && day in 1..daysIn(year, month)
    }

    private fun daysIn(
        year: Int,
        month: Int,
    ): Int = when (month) {
        FEBRUARY -> if (isLeap(year)) LEAP_FEBRUARY_DAYS else FEBRUARY_DAYS
        APRIL, JUNE, SEPTEMBER, NOVEMBER -> SHORT_MONTH_DAYS
        else -> LONG_MONTH_DAYS
    }

    private fun isLeap(year: Int): Boolean =
        year % LEAP_EVERY == 0 && (year % CENTURY != 0 || year % LEAP_CENTURY_EVERY == 0)

    private const val MONTHS = 12
    private const val LEAP_EVERY = 4
    private const val CENTURY = 100
    private const val LEAP_CENTURY_EVERY = 400
    private const val FEBRUARY = 2
    private const val APRIL = 4
    private const val JUNE = 6
    private const val SEPTEMBER = 9
    private const val NOVEMBER = 11
    private const val FEBRUARY_DAYS = 28
    private const val LEAP_FEBRUARY_DAYS = 29
    private const val SHORT_MONTH_DAYS = 30
    private const val LONG_MONTH_DAYS = 31
}
