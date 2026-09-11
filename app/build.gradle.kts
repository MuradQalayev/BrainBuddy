import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)

    id("com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val mistralApiKey: String = localProperties.getProperty("MISTRAL_API_KEY", "")
val mapsApiKey: String = localProperties.getProperty("MAPS_API_KEY", "")
// Opt-in title transport only; empty preserves the existing app path.
val backendTitleUrl = localProperties.getProperty("BRAINBUDDY_TITLE_BACKEND_URL", "")
require(backendTitleUrl.none { it == '"' || it == '\\' || it == '\n' || it == '\r' })
// Opt-in main-assistant transport (Fase 2). Empty preserves MistralAiClient + local tools
// unchanged — see docs/ai-porting-plan.md and BackendAssistantClient.kt.
val backendAssistantUrl = localProperties.getProperty("BRAINBUDDY_ASSISTANT_BACKEND_URL", "")
require(backendAssistantUrl.none { it == '"' || it == '\\' || it == '\n' || it == '\r' })

android {
    namespace = "com.muradgalayev.brainbuddy"
    compileSdk {
        version = release(version = 36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.muradgalayev.brainbuddy"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "MISTRAL_API_KEY", "\"$mistralApiKey\"")
        buildConfigField("String", "BRAINBUDDY_TITLE_BACKEND_URL", "\"$backendTitleUrl\"")
        buildConfigField("String", "BRAINBUDDY_ASSISTANT_BACKEND_URL", "\"$backendAssistantUrl\"")
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // android.util.Log is a stub that throws in JVM unit tests. returning defaults instead lets us
            // test code that logs on its error paths, which is most of the error-mapping logic worth testing
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // core android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // compose ui
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.material3)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // room
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    // navigation
    implementation(libs.navigation.compose)

    // datastore
    implementation(libs.datastore.preferences)

    // viewmodel for compose
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // calendar
    implementation(libs.calendar.compose)

    // supabase
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.functions)
    implementation(libs.image.cropper)
    implementation(libs.ktor.client.android)
    implementation(libs.kotlinx.serialization.json)

    // google identity, OAuth for the calendar scope, decoupled from Supabase auth
    implementation(libs.play.services.auth)

    // workmanager + hilt-work, for background calendar sync
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // coil, remote image loading for avatars
    implementation(libs.coil.compose)

    // google maps compose
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    // health connect, optional and read-only
    implementation(libs.androidx.health.connect)
    // firebase cloud messaging
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("androidx.core:core-ktx:1.13.1")
    // testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
