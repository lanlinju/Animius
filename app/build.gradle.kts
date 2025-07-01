import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
}

android {
    namespace = "com.lanlinju.animius"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lanlinju.animius"
        minSdk = 26
        targetSdk = 35
        versionCode = 34
        versionName = "1.3.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        val dandanplayAppId = System.getenv("DANDANPLAY_APP_ID") ?: ""
        val dandanplayAppSecret = System.getenv("DANDANPLAY_APP_SECRET") ?: ""
        buildConfigField("String", "DANDANPLAY_APP_ID", "\"$dandanplayAppId\"")
        buildConfigField("String", "DANDANPLAY_APP_SECRET", "\"$dandanplayAppSecret\"")
    }

    signingConfigs {
        kotlin.runCatching { System.getenv("KEY_STORE_PASSWORD") }.getOrNull()?.let {
            create("release") {
                storeFile = file("../keystore.jks")
                storePassword = it
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isShrinkResources = true
            isMinifyEnabled = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "${rootProject.name}-v$versionName-$name.apk"
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
        }
    }
}

dependencies {
    implementation(project(":video-player"))
    implementation(project(":download"))
    implementation(project(":danmaku"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.foundation.layout.android)

    // icons
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.material)

    // navigation component
    implementation(libs.androidx.navigation.compose)

    // jsoup
    implementation(libs.jsoup)

    // hilt
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.android)

    // hilt navigation
    implementation(libs.androidx.hilt.navigation.compose)

    // coil
    implementation(libs.coil.compose)

    // room
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)

    // paging compose
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.paging.compose.android)

    // splash screen
    implementation(libs.androidx.splashscreen)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // ktor
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.client.mock)
    implementation(libs.ktor.serialization.kotlinx.json)

    // serialization
    implementation(libs.kotlinx.serialization.json)

    // Slf4j
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.simple)

    // test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
