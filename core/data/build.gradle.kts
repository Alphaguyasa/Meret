plugins {
    alias(libs.plugins.meret.android.library)
    alias(libs.plugins.meret.hilt)
    alias(libs.plugins.meret.explicit.api)
}

android {
    namespace = "et.alpha.meret.core.data"
}

// Repositories own the Room-vs-remote decision and the outbox write. They are the only
// thing allowed to see both :core:database and :core:network.
dependencies {
    api(project(":core:model"))
    api(project(":domain"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:location"))
    // The pack import: MbtilesPackInstaller copies and checks, the repository writes the row.
    implementation(project(":core:maps"))
    implementation(project(":core:network"))

    // TrackRecorder runs against a real in-memory Room database under Robolectric: the
    // batching is only worth testing against the transaction it batches into.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.runtime)
}
