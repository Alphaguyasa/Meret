plugins {
    alias(libs.plugins.meret.android.library)
    alias(libs.plugins.meret.hilt)
    alias(libs.plugins.meret.room)
    alias(libs.plugins.meret.explicit.api)
}

android {
    namespace = "et.alpha.meret.core.database"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))

    // DAO correctness runs on the JVM under Robolectric so it is covered by the CI
    // `test` task. The timing bars in BUILD_PLAN phase 3 are device measurements and
    // live in androidTest instead — a JVM-measured insert says nothing about a Tecno.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
