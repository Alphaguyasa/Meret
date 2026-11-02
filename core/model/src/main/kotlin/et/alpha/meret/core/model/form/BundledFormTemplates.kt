package et.alpha.meret.core.model.form

import et.alpha.meret.core.model.FormTemplate

/**
 * Templates that ship inside the app, so a form can be filled in before sync (Phase 9) has
 * delivered any project's own. Their ids are fixed and never reused by the server: the
 * server's are UUIDs, these are not.
 */
public object BundledFormTemplates {
    /** A twelve-field farm visit: every field type except GPS point, and two `visible_if`. */
    public const val FARM_VISIT_ID: String = "bundled:farm_visit"
    public const val FARM_VISIT_VERSION: Int = 1
    private const val FARM_VISIT_PATH = "/forms/example_farm_visit.json"

    /**
     * The farm visit template under [projectId]. Reads a bundled resource: call it off the main
     * thread.
     */
    public fun farmVisit(projectId: String): FormTemplate = FormTemplate(
        id = FARM_VISIT_ID,
        projectId = projectId,
        version = FARM_VISIT_VERSION,
        name = "Farm visit",
        schemaJson = readResource(FARM_VISIT_PATH),
        updatedAt = 0L,
    )

    private fun readResource(path: String): String {
        val stream =
            checkNotNull(BundledFormTemplates::class.java.getResourceAsStream(path)) {
                "bundled form $path is missing from the build"
            }
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
