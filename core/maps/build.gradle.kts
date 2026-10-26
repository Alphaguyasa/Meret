plugins {
    alias(libs.plugins.meret.android.library.compose)
    alias(libs.plugins.meret.hilt)
    alias(libs.plugins.meret.explicit.api)
}

android {
    namespace = "et.alpha.meret.core.maps"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    // TileJSON for the loopback tile server. The runtime library only; no codegen plugin.
    implementation(libs.kotlinx.serialization.json)
    // The map view. Kept to `implementation`: no MapLibre type is in this module's API.
    implementation(libs.maplibre.android)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // MbtilesReaderTest opens real SQLite packs, which needs Android's SQLiteDatabase.
    testImplementation(libs.robolectric)
}

// MeretStyleTest checks tools/style/, which lives outside this module. Declared as an input
// so editing the style re-runs the test instead of hitting the build cache.
val mapStyleDir: Directory = rootProject.layout.projectDirectory.dir("tools/style")
tasks.withType<Test>().configureEach {
    inputs
        .dir(mapStyleDir)
        .withPropertyName("mapStyle")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("meret.styleDir", mapStyleDir.asFile.absolutePath)
}

// The style template ships in this module's assets as map/style.json
// (StyleProvider.TEMPLATE_ASSET). tools/style/ stays its only source.
abstract class BundleMapStyle : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val style: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val target = outputDir.file("map/style.json").get().asFile
        target.parentFile.mkdirs()
        style.get().asFile.copyTo(target, overwrite = true)
    }
}

androidComponents {
    onVariants { variant ->
        val taskName = "bundle${variant.name.replaceFirstChar { it.uppercase() }}MapStyle"
        val bundle =
            tasks.register<BundleMapStyle>(taskName) {
                style.set(mapStyleDir.file("meret-style.json"))
            }
        variant.sources.assets?.addGeneratedSourceDirectory(bundle, BundleMapStyle::outputDir)
    }
}
