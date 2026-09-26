import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Configuration is read from environment variables first, then from the
 * git-ignored `local.properties` / `keystore.properties` files. Nothing secret
 * is committed to the repository.
 */
fun loadProperties(fileName: String): Properties = Properties().apply {
    val file = rootProject.file(fileName)
    if (file.exists()) file.inputStream().use { load(it) }
}

val localProperties = loadProperties("local.properties")
val keystoreProperties = loadProperties("keystore.properties")

fun config(name: String, source: Properties = localProperties): String =
    (System.getenv(name) ?: source.getProperty(name) ?: "").trim()

val supabaseUrl = config("SUPABASE_URL")
val supabaseAnonKey = config("SUPABASE_ANON_KEY")
// Public website that forwards email confirmation / reset links into the app.
val authWebUrl = config("AUTH_WEB_URL").ifEmpty { "https://wheredidiputit-ochre.vercel.app" }

// AdMob. Release builds show ads only when real IDs are provided. Debug builds
// always use Google's official test IDs so real ads are never served or
// clicked during development (which AdMob treats as invalid traffic).
val admobAppId = config("ADMOB_APP_ID")
val admobInterstitialId = config("ADMOB_INTERSTITIAL_ID")
val releaseAdsConfigured = admobAppId.isNotEmpty() && admobInterstitialId.isNotEmpty()
val googleTestAppId = "ca-app-pub-3940256099942544~3347511713"
val googleTestInterstitialId = "ca-app-pub-3940256099942544/1033173712"

val releaseStoreFile = config("WDIPI_KEYSTORE_FILE", keystoreProperties)
val hasReleaseSigning = releaseStoreFile.isNotEmpty() && rootProject.file(releaseStoreFile).exists()

android {
    namespace = "com.wheredidiputit"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wheredidiputit.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "AUTH_WEB_URL", "\"$authWebUrl\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = config("WDIPI_KEYSTORE_PASSWORD", keystoreProperties)
                keyAlias = config("WDIPI_KEY_ALIAS", keystoreProperties)
                keyPassword = config("WDIPI_KEY_PASSWORD", keystoreProperties)
            }
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = googleTestAppId
            buildConfigField("boolean", "ADS_ENABLED", "true")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$googleTestInterstitialId\"")
        }
        release {
            // Without real IDs the SDK is never initialised and no ad is requested.
            manifestPlaceholders["admobAppId"] = if (releaseAdsConfigured) admobAppId else googleTestAppId
            buildConfigField("boolean", "ADS_ENABLED", releaseAdsConfigured.toString())
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$admobInterstitialId\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/9/previous-compilation-data.bin"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // Per-app language (English / Türkçe) on every supported Android version.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.exifinterface)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.coil.compose)

    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.functions)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
