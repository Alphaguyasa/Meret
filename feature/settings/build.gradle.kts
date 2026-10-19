plugins {
    alias(libs.plugins.meret.android.feature)
}

android {
    namespace = "et.alpha.meret.feature.settings"
}

dependencies {
    // Permission onboarding: skipped steps live in DataStore, the OEM screen in DeviceCompat.
    implementation(project(":core:datastore"))
    implementation(project(":core:location"))

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
