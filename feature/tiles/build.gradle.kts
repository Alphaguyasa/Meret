plugins {
    alias(libs.plugins.meret.android.feature)
}

android {
    namespace = "et.alpha.meret.feature.tiles"
}

dependencies {
    // The installed packs, and the import that writes them.
    implementation(project(":core:data"))
    // rememberLauncherForActivityResult, for the document picker.
    implementation(libs.androidx.activity.compose)
}
