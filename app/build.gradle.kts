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
