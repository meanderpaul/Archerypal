import java.util.Properties



plugins {

    id("com.android.application")

    id("org.jetbrains.kotlin.android")

    id("org.jetbrains.kotlin.plugin.serialization")

    id("org.jetbrains.kotlin.plugin.compose")

}

val hasGoogleServices = file("google-services.json").exists()

if (hasGoogleServices) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}



val keystorePropertiesFile = rootProject.file("keystore.properties")

val keystoreProperties = Properties()

if (keystorePropertiesFile.exists()) {

    keystoreProperties.load(keystorePropertiesFile.inputStream())

}

// RevenueCat public SDK keys from local.properties (never commit real keys).
// Prefer split keys so debug stays on Test Store while release uses Play:
//   revenuecat.api.key.test=test_…
//   revenuecat.api.key.play=goog_…
// Fallback: revenuecat.api.key (used for both if split keys omitted).
val revenueCatLocalProps = rootProject.file("local.properties").takeIf { it.exists() }?.let { propsFile ->
    Properties().apply { propsFile.inputStream().use { load(it) } }
}
val revenueCatKeyFallback =
    revenueCatLocalProps?.getProperty("revenuecat.api.key").orEmpty().trim()
val revenueCatKeyTest =
    revenueCatLocalProps?.getProperty("revenuecat.api.key.test").orEmpty().trim()
        .ifEmpty { revenueCatKeyFallback }
val revenueCatKeyPlay =
    revenueCatLocalProps?.getProperty("revenuecat.api.key.play").orEmpty().trim()
        .ifEmpty { revenueCatKeyFallback }

// Shared with Workers APP_CLIENT_KEY (wrangler secret). Debug default matches .dev.vars.example.
val appClientKey =
    revenueCatLocalProps?.getProperty("archerypal.app.client.key").orEmpty().trim()
        .ifEmpty { "dev-app-client-key-change-me" }



android {

    namespace = "com.archerypal.app"

    compileSdk = 36



    defaultConfig {

        applicationId = "com.archerypal.app"

        minSdk = 26

        targetSdk = 36

        versionCode = 17

        versionName = "1.3.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {

            useSupportLibrary = true

        }

        // Cloudflare Workers API (all builds).
        buildConfigField("String", "MATCH_SERVER_URL", "\"https://archerypal-api.pauljreedii.workers.dev\"")
        // Web OAuth client ID — used for Google Sign-In ID tokens verified by the server.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"917453199570-0bj3rf1fbcqoor2l2skdus0uv4rvfg2q.apps.googleusercontent.com\"")
        buildConfigField("boolean", "HAS_GOOGLE_SERVICES", hasGoogleServices.toString())
        // defaultConfig / debug → Test Store; release overrides with Play key.
        buildConfigField(
            "String",
            "REVENUECAT_API_KEY",
            "\"${revenueCatKeyTest.replace("\"", "\\\"")}\""
        )
        buildConfigField(
            "String",
            "APP_CLIENT_KEY",
            "\"${appClientKey.replace("\"", "\\\"")}\""
        )
    }



    signingConfigs {

        create("release") {

            if (keystorePropertiesFile.exists()) {

                keyAlias = keystoreProperties["keyAlias"] as String

                keyPassword = keystoreProperties["keyPassword"] as String

                storeFile = file(keystoreProperties["storeFile"] as String)

                storePassword = keystoreProperties["storePassword"] as String

            }

        }

    }



    buildTypes {

        release {

            isMinifyEnabled = true

            isShrinkResources = true

            proguardFiles(

                getDefaultProguardFile("proguard-android-optimize.txt"),

                "proguard-rules.pro"

            )

            signingConfig = if (keystorePropertiesFile.exists()) {

                signingConfigs.getByName("release")

            } else {

                signingConfigs.getByName("debug")

            }

            // Play Store SDK key when revenuecat.api.key.play is set; else shared fallback.
            buildConfigField(
                "String",
                "REVENUECAT_API_KEY",
                "\"${revenueCatKeyPlay.replace("\"", "\\\"")}\""
            )

        }

        debug {

            applicationIdSuffix = ".debug"

            versionNameSuffix = "-debug"

        }

    }



    compileOptions {

        sourceCompatibility = JavaVersion.VERSION_17

        targetCompatibility = JavaVersion.VERSION_17

    }



    kotlinOptions {

        jvmTarget = "17"

    }



    buildFeatures {

        compose = true

        buildConfig = true

    }



    packaging {

        resources {

            excludes += "/META-INF/{AL2.0,LGPL2.1}"

        }

    }



    bundle {

        language {

            enableSplit = false

        }

    }

}



dependencies {

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")

    implementation(composeBom)

    androidTestImplementation(composeBom)



    implementation("androidx.core:core-ktx:1.15.0")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")

    implementation("androidx.compose.ui:ui-graphics")

    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.compose.material3:material3")

    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.8.5")



    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")



    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")



    implementation("com.google.android.gms:play-services-nearby:19.3.0")

    implementation("com.google.android.gms:play-services-location:21.3.0")

    implementation("com.android.billingclient:billing-ktx:8.3.0")
    implementation("com.revenuecat.purchases:purchases:10.20.0")
    implementation("com.revenuecat.purchases:purchases-ui:10.20.0")

    implementation("com.google.android.gms:play-services-ads:24.5.0")

    implementation(platform("com.google.firebase:firebase-bom:34.16.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-crashlytics")



    implementation("androidx.camera:camera-camera2:1.4.1")

    implementation("androidx.camera:camera-lifecycle:1.4.1")

    implementation("androidx.camera:camera-view:1.4.1")

    implementation("com.google.mlkit:barcode-scanning:17.3.0")



    implementation("com.google.zxing:core:3.5.3")

    implementation("com.google.guava:guava:33.3.1-android")

    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")

    debugImplementation("androidx.compose.ui:ui-test-manifest")

}

