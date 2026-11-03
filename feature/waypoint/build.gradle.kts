plugins {
    alias(libs.plugins.meret.android.feature)
}

android {
    namespace = "et.alpha.meret.feature.waypoint"
}

dependencies {
    // The capture sheet reads the phone's position and the recording, and saves through
    // SaveWaypointUseCase, all bound in :core:data.
    implementation(project(":core:data"))

    // The process-death test runs the sheet on a real Room file, closed and reopened. Test
    // only: the sheet itself never sees :core:database.
    testImplementation(project(":core:database"))
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
