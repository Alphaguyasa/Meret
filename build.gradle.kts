// Top-level build file. Plugins are declared here (and applied by the convention
// plugins in build-logic/) so that every module resolves them from one classpath and
// no module ever names a version inline.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover) apply false
}

allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    // Rule configuration lives in .editorconfig, which is what ktlint reads natively and
    // what the IDE picks up too. No `filter { }` here: the closure captures the Project
    // and breaks the configuration cache.
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.8.0")
        android.set(true)
        ignoreFailures.set(false)
    }

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        baseline = rootProject.file("config/detekt/baseline.xml").takeIf { it.exists() }
        parallel = true
    }

    tasks.withType<Test>().configureEach {
        // Gradle 9 fails a Test task that finds a test source set but no tests in it.
        // Most modules in the graph are still empty by design — BUILD_PLAN phases 2
        // onward fill them — and an empty module should not break `./gradlew test`.
        failOnNoDiscoveredTests = false
        testLogging {
            events("failed", "skipped")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "17"
        reports {
            html.required.set(true)
            xml.required.set(false)
            txt.required.set(false)
            sarif.required.set(false)
            md.required.set(false)
        }
    }
}
