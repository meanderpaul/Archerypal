import java.util.Properties



plugins {

    id("com.android.application")

    id("org.jetbrains.kotlin.android")

    id("org.jetbrains.kotlin.plugin.serialization")

    id("org.jetbrains.kotlin.plugin.compose")

}



val keystorePropertiesFile = rootProject.file("keystore.properties")

val keystoreProperties = Properties()

if (keystorePropertiesFile.exists()) {

    keystoreProperties.load(keystorePropertiesFile.inputStream())

}



android {

    namespace = "com.archerypal.app"

    compileSdk = 35



    defaultConfig {

        applicationId = "com.archerypal.app"

        minSdk = 26

        targetSdk = 35

        versionCode = 3

        versionName = "1.2.0"



        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {

            useSupportLibrary = true

        }

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

            excludes += "META-INF/io.netty.versions.properties"

            excludes += "META-INF/INDEX.LIST"

            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"

            excludes += "META-INF/native-image/io.netty/netty-codec-native-quic/jni-config.json"

            excludes += "META-INF/native-image/io.netty/netty-codec-native-quic/reflect-config.json"

            excludes += "META-INF/native-image/io.netty/netty-codec-native-quic/resource-config.json"

            excludes += "META-INF/native-image/io.netty/netty-codec-native-quic/native-image.properties"

            excludes += "META-INF/license/*"

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



    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")



    implementation("com.google.android.gms:play-services-nearby:19.3.0")

    implementation("com.android.billingclient:billing-ktx:8.3.0")

    implementation("com.google.android.gms:play-services-ads:24.5.0")



    implementation("androidx.camera:camera-camera2:1.4.1")

    implementation("androidx.camera:camera-lifecycle:1.4.1")

    implementation("androidx.camera:camera-view:1.4.1")

    implementation("com.google.mlkit:barcode-scanning:17.3.0")



    implementation("com.google.zxing:core:3.5.3")



    implementation("io.libp2p:jvm-libp2p:1.3.4-RELEASE")
    implementation("com.google.guava:guava:33.3.1-android")



    debugImplementation("androidx.compose.ui:ui-tooling")

    debugImplementation("androidx.compose.ui:ui-test-manifest")

}

