plugins {
    alias(libs.plugins.meret.android.library)
    alias(libs.plugins.meret.hilt)
    alias(libs.plugins.meret.explicit.api)
}

android {
    namespace = "et.alpha.meret.core.datastore"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))

    api(libs.androidx.datastore.preferences)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
