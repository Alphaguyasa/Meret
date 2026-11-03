package et.alpha.meret.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * What is typed on the waypoint sheet and not yet saved, so a killed app reopens the sheet
 * with every answer. One row per project: the sheet has one draft at a time.
 *
 * Its own table, not a `draft` status on `waypoint`: every query on `waypoint` (the map, the
 * list, sync's pending rows) would have to leave drafts out, and one that forgot would draw
 * or upload a waypoint nobody saved. A draft has no position either; that is taken at Save.
 *
 * [formData] is `FormAnswersJson` text, as on `waypoint`. [numberTexts] is a JSON object of
 * number boxes' text as typed ("1." is on its way to a number but is not one).
 *
 * Added in database version 2 (`MIGRATION_1_2`).
 */
@Entity(tableName = "waypoint_draft")
public data class WaypointDraftEntity(
    @PrimaryKey
    @ColumnInfo(name = "project_id")
    public val projectId: String,
    @ColumnInfo(name = "title")
    public val title: String,
    @ColumnInfo(name = "category")
    public val category: String? = null,
    @ColumnInfo(name = "notes")
    public val notes: String,
    @ColumnInfo(name = "form_template_id")
    public val formTemplateId: String? = null,
    @ColumnInfo(name = "form_version")
    public val formVersion: Int? = null,
    @ColumnInfo(name = "form_data")
    public val formData: String? = null,
    @ColumnInfo(name = "number_texts")
    public val numberTexts: String? = null,
    @ColumnInfo(name = "show_errors")
    public val showErrors: Boolean = false,
    @ColumnInfo(name = "updated_at")
    public val updatedAt: Long,
)
