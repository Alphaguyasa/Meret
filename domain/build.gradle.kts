plugins {
    alias(libs.plugins.meret.jvm.library)
    alias(libs.plugins.meret.explicit.api)
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
}