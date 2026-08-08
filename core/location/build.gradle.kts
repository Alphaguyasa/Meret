plugins {
    alias(libs.plugins.meret.android.library)
    alias(libs.plugins.meret.hilt)
    alias(libs.plugins.meret.explicit.api)
}

android {
    namespace = "et.alpha.meret.core.location"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))

    // Optional at runtime by design: PlatformLocationEngine must work on devices where Play
    // Services is absent, stripped or stale. Present at compile time, never assumed present
    // at run time — LocationEngineSelector decides.
    implementation(libs.play.services.location)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
