plugins {
    alias(libs.plugins.meret.android.feature)
}

android {
    namespace = "et.alpha.meret.feature.map"
}

dependencies {
    // OfflineMap replaces the placeholder basemap in 6f (map screen controls).
    implementation(project(":core:maps"))
}
