import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

/**
 * CLAUDE.md: explicit API mode is on for `:core:*` and `:domain`. These modules are a
 * published surface for the rest of the app — visibility and return types are stated,
 * not inferred. Test source sets are exempt; the Kotlin plugin handles that.
 */
class ExplicitApiConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        extensions.configure<KotlinProjectExtension> { explicitApi() }
    }
}
