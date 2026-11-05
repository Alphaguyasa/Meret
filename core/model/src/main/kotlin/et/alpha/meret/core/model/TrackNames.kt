package et.alpha.meret.core.model

/**
 * What a typed track name is stored as. Enumerators name tracks after the kebele or farm they
 * walked, on a phone keyboard, so stray spaces and a pasted line break are expected.
 */
public object TrackNames {
    /**
     * The longest name kept, in characters (code points, so an Ethiopic syllable is one). It
     * still fits a list row on a small phone in two lines.
     */
    public const val MAX_LENGTH: Int = 80

    private val whitespace = Regex("\\s+")

    /**
     * [raw] with every run of whitespace, line breaks included, made one space, trimmed, and
     * cut to [MAX_LENGTH]. Nothing left means no name: the track is shown by its date again.
     */
    public fun normalise(raw: String): String? {
        val collapsed = raw.replace(whitespace, " ").trim()
        val kept =
            if (collapsed.codePointCount(0, collapsed.length) <= MAX_LENGTH) {
                collapsed
            } else {
                collapsed.substring(0, collapsed.offsetByCodePoints(0, MAX_LENGTH)).trimEnd()
            }
        return kept.ifEmpty { null }
    }
}
