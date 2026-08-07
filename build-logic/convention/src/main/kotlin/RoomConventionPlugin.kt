import com.android.build.api.dsl.LibraryExtension
import com.google.devtools.ksp.gradle.KspExtension
import et.alpha.meret.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Room, with the schema exporter pointed at a directory we commit.
 *
 * The exported JSON is the only record of what shipped in a given database version, and
 * `MigrationTestHelper` reads it from `androidTest` assets to build an old database and
 * migrate it forward. Both of those stop working the moment the directory is untracked
 * or unregistered, so this plugin does both and no module has to remember to.
 */
class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")

        val schemaDirectory = layout.projectDirectory.dir("schemas")

        extensions.configure<KspExtension> {
            arg("room.schemaLocation", schemaDirectory.asFile.absolutePath)
            // KSP2 generates Kotlin sources; the Java path is legacy and produces
            // platform types at the DAO boundary that explicit API mode then rejects.
            arg("room.generateKotlin", "true")
        }

        extensions.configure<LibraryExtension> {
            // MigrationTestHelper resolves schemas by reading them out of the test APK's
            // assets, not off the build machine's filesystem.
            sourceSets.getByName("androidTest").assets.srcDir(schemaDirectory)
        }

        dependencies {
            add("implementation", libs.findLibrary("androidx-room-runtime").get())
            add("implementation", libs.findLibrary("androidx-room-ktx").get())
            add("ksp", libs.findLibrary("androidx-room-compiler").get())
            add("androidTestImplementation", libs.findLibrary("androidx-room-testing").get())
        }
    }
}
