plugins {
    alias(libs.plugins.meret.android.feature)
}

android {
    namespace = "et.alpha.meret.feature.tracking"
}

dependencies {
    // TrackingService drives TrackingController (ARCHITECTURE.md ADR-009).
    implementation(project(":core:data"))
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
