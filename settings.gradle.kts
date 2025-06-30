pluginManagement {
    repositories {
//        maven("https://maven.aliyun.com/repository/public")
//        maven("https://jitpack.io")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version ("1.0.0")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/public")
        maven("https://jitpack.io")
        google()
        mavenCentral()
    }
}

rootProject.name = "Animius"
include(":app")
include(":video-player")
include(":download")
include(":danmaku")
