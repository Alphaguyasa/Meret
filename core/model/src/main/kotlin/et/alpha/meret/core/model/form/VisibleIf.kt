package et.alpha.meret.core.model.form

/**
 * When a field shows, as a condition on the answers to fields before it.
 *
 * Comparison rules, the same for every operator:
 * - an unanswered field, or one that is itself hidden, has no value: [Equals] and [In] are
 *   false, [NotEquals] is true;
 * - a number answer compares numerically (`"2"` equals `2.0`);
 * - a multiselect answer [Equals] a value when that value is among its choices, and is [In]
 *   a list when any of its choices is.
 */
public sealed interface VisibleIf {
    /** The field keys this condition reads. */
    public val fieldKeys: Set<String>

    public data class Equals(
        public val fieldKey: String,
        public val value: String,
    ) : VisibleIf {
        override val fieldKeys: Set<String> get() = setOf(fieldKey)
    }

    public data class NotEquals(
        public val fieldKey: String,
        public val value: String,
    ) : VisibleIf {
        override val fieldKeys: Set<String> get() = setOf(fieldKey)
    }

    public data class In(
        public val fieldKey: String,
        public val values: List<String>,
    ) : VisibleIf {
        override val fieldKeys: Set<String> get() = setOf(fieldKey)
    }

    /** Every condition holds. */
    public data class And(
        public val conditions: List<VisibleIf>,
    ) : VisibleIf {
        override val fieldKeys: Set<String> get() =
            conditions.flatMapTo(
                mutableSetOf(),
            ) { it.fieldKeys }
    }

    /** At least one condition holds. */
    public data class Or(
        public val conditions: List<VisibleIf>,
    ) : VisibleIf {
        override val fieldKeys: Set<String> get() =
            conditions.flatMapTo(
                mutableSetOf(),
            ) { it.fieldKeys }
    }
}

/**
 * Whether this condition holds for [answers], where only the answers of visible fields count
 * (see [FormRules.visibleKeys]).
 */
public fun VisibleIf.holds(answers: Map<String, FormAnswer>): Boolean = when (this) {
    is VisibleIf.Equals -> {
        answers[fieldKey]?.matches(value) == true
    }

    is VisibleIf.NotEquals -> {
        answers[fieldKey]?.matches(value) != true
    }

    is VisibleIf.In -> {
        answers[fieldKey]?.let { answer -> values.any { answer.matches(it) } } ==
            true
    }

    is VisibleIf.And -> {
        conditions.all { it.holds(answers) }
    }

    is VisibleIf.Or -> {
        conditions.any { it.holds(answers) }
    }
}

private fun FormAnswer.matches(expected: String): Boolean = when (this) {
    is FormAnswer.Text -> value == expected
    is FormAnswer.Choice -> value == expected
    is FormAnswer.Choices -> expected in values
    is FormAnswer.Number -> expected.toDoubleOrNull() == value
    is FormAnswer.Date -> isoDate == expected
    is FormAnswer.Photos, is FormAnswer.Point, is FormAnswer.Raw -> false
}
