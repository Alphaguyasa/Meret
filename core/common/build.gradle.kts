plugins {
    alias(libs.plugins.meret.jvm.library)
    alias(libs.plugins.meret.explicit.api)
    alias(libs.plugins.kover)
}

dependencies {
    api(project(":core:model"))
}

// BUILD_PLAN phase 2 sets the bar at 85% line coverage for this module. Enforced rather
// than reported: a coverage number nobody checks is a number that only goes down.
kover {
    reports {
        verify {
            rule {
                minBound(85)
            }
        }
    }
}
