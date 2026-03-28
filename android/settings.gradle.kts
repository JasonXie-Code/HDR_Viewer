pluginManagement {
    repositories {
        // 与 dependencyResolutionManagement 一致：先 Central / 镜像，再 Google，减少仅存在于 Central 的库误走 dl.google.com
        mavenCentral()
        gradlePluginPortal()
        google()
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("${rootDir}/../misc/local-repo") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("${rootDir}/../misc/local-repo") }
        // Coil 等 JVM 库在 Maven Central；若 google() 在前会先请求 dl.google.com，易超时且路径不对
        mavenCentral()
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
    }
}

rootProject.name = "HDRViewer"
include(":app")
