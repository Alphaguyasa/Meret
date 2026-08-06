plugins {
    alias(libs.plugins.meret.jvm.library)
    alias(libs.plugins.meret.explicit.api)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}

// BUILD_PLAN phase 2 sets the bar at 85% line coverage for this module.
kover {
    reports {
        verify {
            rule {
                minBound(85)
            }
        }
    }
}
