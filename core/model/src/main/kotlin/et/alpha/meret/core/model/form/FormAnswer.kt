package et.alpha.meret.core.model.form

/** One field's answer, typed by its field. An unanswered field has no entry at all. */
public sealed interface FormAnswer {
    public data class Text(
        public val value: String,
    ) : FormAnswer

    public data class Number(
        public val value: Double,
    ) : FormAnswer

    /** A select's chosen option value. */
    public data class Choice(
        public val value: String,
    ) : FormAnswer

    /** A multiselect's chosen option values, in the order chosen. */
    public data class Choices(
        public val values: List<String>,
    ) : FormAnswer

    /** `yyyy-MM-dd`, as [FormField.Date] stores it. */
    public data class Date(
        public val isoDate: String,
    ) : FormAnswer

    /** `waypoint_photo` ids, in the order taken. */
    public data class Photos(
        public val photoIds: List<String>,
    ) : FormAnswer

    public data class Point(
        public val latitude: Double,
        public val longitude: Double,
        public val accuracyMetres: Double?,
    ) : FormAnswer

    /**
     * An answer kept exactly as stored, as JSON text: the answer to an [FormField.Unsupported]
     * field, or one whose shape this build does not read. Written back unchanged.
     */
    public data class Raw(
        public val json: String,
    ) : FormAnswer
}
