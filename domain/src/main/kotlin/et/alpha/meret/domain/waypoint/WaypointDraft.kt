package et.alpha.meret.domain.waypoint

import et.alpha.meret.core.common.result.AppResult

/**
 * What is on the waypoint sheet and not yet saved: kept in Room so that a killed app reopens
 * the sheet with every answer. It has no position; that is taken at Save.
 */
public data class WaypointDraft(
    public val title: String = "",
    public val categoryKey: String? = null,
    public val notes: String = "",
    /** The form the answers were given under. */
    public val formTemplateId: String? = null,
    public val formVersion: Int? = null,
    /** The answers as `FormAnswersJson` text, number fields excepted; null when there are none. */
    public val answersJson: String? = null,
    /** Number boxes' text as typed, by field key. */
    public val numberTexts: Map<String, String> = emptyMap(),
    /** A Save was refused, and the sheet is showing the problems. */
    public val showErrors: Boolean = false,
) {
    /** Nothing typed: there is no draft to keep. */
    public val isEmpty: Boolean
        get() =
            title.isEmpty() &&
                categoryKey == null &&
                notes.isEmpty() &&
                answersJson == null &&
                numberTexts.values.all { it.isEmpty() }
}

/** The one draft of the waypoint sheet. Room, through `:core:data`; never the network. */
public interface WaypointDraftStore {
    /** The draft left by the last sheet, or null when there is none or it cannot be read. */
    public suspend fun load(): WaypointDraft?

    /** Replaces the draft with [draft]. */
    public suspend fun save(draft: WaypointDraft): AppResult<Unit>

    /** There is no draft any more: the waypoint was saved, or everything was cleared. */
    public suspend fun clear(): AppResult<Unit>
}
