package et.alpha.meret.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import java.time.Duration

/**
 * Single source of truth for the SDK and JVM levels. Changing a number here changes it
 * for every module, which is the whole point of the convention plugins.
 */
internal object BuildConfig {
    const val COMPILE_SDK = 37
    const val MIN_SDK = 26
    const val TARGET_SDK = 36
    val JAVA_VERSION = JavaVersion.VERSION_17
    val JVM_TARGET = JvmTarget.JVM_17

    /**
     * A hung test task fails after this long instead of holding the CI job until the job's
     * own timeout, spending Actions minutes (PR #41's run hung for 38 minutes).
     */
    val TEST_TIMEOUT: Duration = Duration.ofMinutes(10)
}

/** Shared `android { }` configuration for both application and library modules. */
internal fun Project.configureKotlinAndroid(extension: CommonExtension) {
    // AGP 9 dropped the Action-taking overloads for compileOptions/lint/testOptions/
    // packaging on CommonExtension, and CommonExtension is no longer generic. Only the
    // property getters remain, so configure through those rather than with a lambda.
    extension.compileSdk = BuildConfig.COMPILE_SDK
    extension.defaultConfig.minSdk = BuildConfig.MIN_SDK

    extension.compileOptions.sourceCompatibility = BuildConfig.JAVA_VERSION
    extension.compileOptions.targetCompatibility = BuildConfig.JAVA_VERSION

    // A missing Amharic string is a shipping bug, not a suggestion. CLAUDE.md
    // invariant 6: values-am/ must never be allowed to drift.
    extension.lint.error.add("MissingTranslation")
    extension.lint.abortOnError = true

    extension.testOptions.unitTests.isIncludeAndroidResources = true
    extension.testOptions.unitTests.isReturnDefaultValues = true
    configureTestTimeout()

    extension.packaging.resources.excludes.addAll(
        setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/LICENSE.md",
            "/META-INF/LICENSE-notice.md",
        ),
    )

    // AGP 9 has built-in Kotlin support: `com.android.application`/`com.android.library`
    // register the standard KotlinAndroidProjectExtension themselves. Applying
    // `org.jetbrains.kotlin.android` on top of that fails with a duplicate-extension
    // error, so we configure the extension AGP created rather than adding our own.
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget = BuildConfig.JVM_TARGET
            freeCompilerArgs.addAll(COMMON_COMPILER_ARGS)
        }
    }
}

/** Shared configuration for pure-Kotlin (no Android) modules. */
internal fun Project.configureKotlinJvm() {
    configureTestTimeout()

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = BuildConfig.JAVA_VERSION
        targetCompatibility = BuildConfig.JAVA_VERSION
    }

    extensions.configure<KotlinJvmProjectExtension> {
        compilerOptions {
            jvmTarget = BuildConfig.JVM_TARGET
            freeCompilerArgs.addAll(COMMON_COMPILER_ARGS)
        }
    }
}

private fun Project.configureTestTimeout() {
    tasks.withType<Test>().configureEach { timeout.set(BuildConfig.TEST_TIMEOUT) }
}

private val COMMON_COMPILER_ARGS = listOf(
    "-opt-in=kotlin.RequiresOptIn",
)
