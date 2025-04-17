plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
}
android {
    compileSdk = 35
    namespace = "com.android.cling"
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources {
            excludes += "/META-INF/beans.xml"
        }
    }
}

dependencies {
    // Cling library 只能使用2.1.1 ，当2.1.2时无法搜索到设备
    api("org.fourthline.cling:cling-core:2.1.1")
    api("org.fourthline.cling:cling-support:2.1.1")
    // Jetty library
    api("org.eclipse.jetty:jetty-server:8.1.22.v20160922")
    api("org.eclipse.jetty:jetty-servlet:8.1.22.v20160922")
    api("org.eclipse.jetty:jetty-client:8.1.22.v20160922")
    api("org.slf4j:slf4j-simple:1.7.25")
//    // Servlet
    api("javax.servlet:javax.servlet-api:3.1.0")
    implementation(libs.androidx.lifecycle.livedata.core.ktx)
}