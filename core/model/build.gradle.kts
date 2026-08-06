plugins {
    alias(libs.plugins.meret.jvm.library)
    alias(libs.plugins.meret.explicit.api)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
