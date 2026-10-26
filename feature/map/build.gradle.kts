plugins {
    alias(libs.plugins.meret.android.feature)
}

android {
    namespace = "et.alpha.meret.feature.map"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:maps"))
}
