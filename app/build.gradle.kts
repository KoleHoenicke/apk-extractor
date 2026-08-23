plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val uploadStorePath = providers.environmentVariable("APK_EXTRACTOR_UPLOAD_STORE_FILE").orNull
val uploadStorePassword = providers.environmentVariable("APK_EXTRACTOR_UPLOAD_STORE_PASSWORD").orNull
val uploadKeyAlias = providers.environmentVariable("APK_EXTRACTOR_UPLOAD_KEY_ALIAS").orNull
val uploadKeyPassword = providers.environmentVariable("APK_EXTRACTOR_UPLOAD_KEY_PASSWORD").orNull

android {
    namespace = "com.kolehoenicke.apkextractor"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kolehoenicke.apkextractor"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("main").assets.directories.add(rootProject.file("licenses").absolutePath)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (
                uploadStorePath != null &&
                uploadStorePassword != null &&
                uploadKeyAlias != null &&
                uploadKeyPassword != null
            ) {
                signingConfig = signingConfigs.create("playUpload") {
                    storeFile = file(uploadStorePath)
                    storePassword = uploadStorePassword
                    keyAlias = uploadKeyAlias
                    keyPassword = uploadKeyPassword
                }
            }
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3:1.5.0-alpha26")
    implementation("androidx.compose.material3.adaptive:adaptive:1.3.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
