import java.util.Properties

plugins {
    alias(libs.plugins.meret.android.application.compose)
    alias(libs.plugins.meret.hilt)
}

// Supabase credentials live in local.properties, which is gitignored. They reach the
// code only through BuildConfig — never as a literal in a source file.
val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

/**
 * Per-ABI release APKs, for sideloading: a phone downloads only its own MapLibre native
 * libraries. On by default when an `assemble...Release` task is asked for, because AGP has no
 * per-build-type splits and the debug APK must keep x86 for emulators. `-Pmeret.abiSplits=false`
 * builds the single all-ABI release APK, which `release-size.yml` measures as the baseline.
 * App Bundles ignore splits: Play serves each phone its own ABI anyway.
 */
val releaseAbiSplits: Boolean =
    providers.gradleProperty("meret.abiSplits").orNull?.toBoolean()
        ?: gradle.startParameter.taskNames.any { task ->
            task.contains("assemble", ignoreCase = true) &&
                task.contains("release", ignoreCase = true)
        }

fun secret(
    key: String,
    fallback: String = "",
): String = (localProperties.getProperty(key) ?: System.getenv(key) ?: fallback)

android {
    namespace = "et.alpha.meret"

    defaultConfig {
        applicationId = "et.alpha.meret"
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "SUPABASE_URL", "\"${secret("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secret("SUPABASE_ANON_KEY")}\"")

        // We ship English and Amharic. Anything else would ship untranslated.
        resourceConfigurations += setOf("en", "am")
    }

    buildFeatures {
        buildConfig = true
    }

    // Field phones are 32- or 64-bit ARM. x86 and x86_64 are emulators, so no split APK is
    // made for them. The universal APK is the fallback for a phone whose ABI nobody knows.
    splits.abi.isEnable = releaseAbiSplits
    splits.abi.reset()
    splits.abi.include("armeabi-v7a", "arm64-v8a")
    splits.abi.isUniversalApk = true

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))

    implementation(project(":feature:map"))
    implementation(project(":feature:tracking"))
    implementation(project(":feature:waypoint"))
    implementation(project(":feature:measure"))
    implementation(project(":feature:tiles"))
    implementation(project(":feature:sync"))
    implementation(project(":feature:export"))
    implementation(project(":feature:settings"))

    implementation(project(":sync"))
    implementation(project(":peer"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // ManifestPermissionsTest reads the merged manifest through Robolectric.
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
