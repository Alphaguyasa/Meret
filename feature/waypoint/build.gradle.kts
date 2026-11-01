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
}
