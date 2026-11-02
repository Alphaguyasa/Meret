package et.alpha.meret.core.data.waypoint

import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.data.tracking.RoomNewTrackStore
import et.alpha.meret.core.model.form.BundledFormTemplates
import et.alpha.meret.core.model.form.FormSchemaParser
import et.alpha.meret.core.model.form.FormSchemaResult
import et.alpha.meret.domain.waypoint.WaypointForm
import et.alpha.meret.domain.waypoint.WaypointFormSource
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Until sync (Phase 9) brings each project's own templates, every waypoint gets the farm visit
 * that ships with the app ([BundledFormTemplates.farmVisit]). It is read and parsed once, off
 * the main thread, and kept.
 */
@Singleton
public class BundledWaypointFormSource
    @Inject
    internal constructor(
        private val dispatchers: DispatcherProvider,
    ) : WaypointFormSource {
        @Volatile
        private var cached: WaypointForm? = null

        override suspend fun currentForm(): WaypointForm? = cached ?: withContext(dispatchers.io) {
            val template = BundledFormTemplates.farmVisit(RoomNewTrackStore.LOCAL_PROJECT_ID)
            when (val parsed = FormSchemaParser.parse(template.schemaJson)) {
                is FormSchemaResult.Valid -> {
                    WaypointForm(
                        templateId = template.id,
                        version = template.version,
                        name = template.name,
                        schema = parsed.schema,
                    ).also { cached = it }
                }

                // A bundled template that does not parse is a build mistake its tests catch;
                // in the field, a waypoint without a form beats no waypoint.
                is FormSchemaResult.Invalid -> {
                    null
                }
            }
        }
    }
