package et.alpha.meret.core.data.waypoint

import android.database.SQLException
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.common.time.TimeProvider
import et.alpha.meret.core.data.tracking.RoomNewTrackStore
import et.alpha.meret.core.database.dao.WaypointDraftDao
import et.alpha.meret.core.database.entity.WaypointDraftEntity
import et.alpha.meret.domain.waypoint.WaypointDraft
import et.alpha.meret.domain.waypoint.WaypointDraftStore
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject

/**
 * The waypoint sheet's draft in Room (`waypoint_draft`), one row for the local project, the
 * same project new waypoints are saved under until sync.
 *
 * The constructor is public so the sheet's process-death test can build one on a real
 * database.
 */
public class RoomWaypointDraftStore
    @Inject
    constructor(
        private val dao: WaypointDraftDao,
        private val time: TimeProvider,
        private val dispatchers: DispatcherProvider,
    ) : WaypointDraftStore {
        override suspend fun load(): WaypointDraft? = withContext(dispatchers.io) {
            try {
                dao.getDraft(PROJECT_ID)?.toDraft()
            } catch (_: SQLException) {
                // A draft that cannot be read is a sheet that starts empty, not a crash.
                null
            }
        }

        override suspend fun save(draft: WaypointDraft): AppResult<Unit> = write {
            dao.upsertDraft(draft.toEntity(time.nowMillis()))
        }

        override suspend fun clear(): AppResult<Unit> = write { dao.deleteDraft(PROJECT_ID) }

        private suspend fun write(block: suspend () -> Unit): AppResult<Unit> =
            withContext(dispatchers.io) {
                try {
                    block()
                    AppResult.Success(Unit)
                } catch (e: SQLException) {
                    AppResult.Failure(AppError.Storage(AppError.Storage.Kind.WriteFailed, e))
                }
            }

        private companion object {
            val PROJECT_ID: String = RoomNewTrackStore.LOCAL_PROJECT_ID
        }
    }

private fun WaypointDraft.toEntity(now: Long) = WaypointDraftEntity(
    projectId = RoomNewTrackStore.LOCAL_PROJECT_ID,
    title = title,
    category = categoryKey,
    notes = notes,
    formTemplateId = formTemplateId,
    formVersion = formVersion,
    formData = answersJson,
    numberTexts = numberTexts.takeIf { it.isNotEmpty() }?.let { JSONObject(it).toString() },
    showErrors = showErrors,
    updatedAt = now,
)

private fun WaypointDraftEntity.toDraft() = WaypointDraft(
    title = title,
    categoryKey = category,
    notes = notes,
    formTemplateId = formTemplateId,
    formVersion = formVersion,
    answersJson = formData,
    numberTexts = numberTexts?.let(::decodeNumberTexts).orEmpty(),
    showErrors = showErrors,
)

/** Box texts by key. Text that is not a JSON object of strings gives what can be read. */
private fun decodeNumberTexts(text: String): Map<String, String> = try {
    val json = JSONObject(text)
    json
        .keys()
        .asSequence()
        .mapNotNull { key ->
            (json.opt(key) as? String)?.let { key to it }
        }.toMap()
} catch (_: JSONException) {
    emptyMap()
}
