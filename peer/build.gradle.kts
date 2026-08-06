plugins {
    alias(libs.plugins.meret.android.library)
    alias(libs.plugins.meret.hilt)
}

android {
    namespace = "et.alpha.meret.peer"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))
}