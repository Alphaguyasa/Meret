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
}
