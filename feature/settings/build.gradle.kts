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
    // rememberLauncherForActivityResult and LocalActivity, for the permission dialogs.
    implementation(libs.androidx.activity.compose)
    // Diagnostics: the recording's state and health, and the app's version code.
    implementation(project(":core:data"))
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
