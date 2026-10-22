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

    testImplementation(libs.kotlinx.serialization.json)
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
