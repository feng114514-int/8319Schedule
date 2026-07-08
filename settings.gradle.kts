pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        // 阿里云镜像(本地加速; 放最后, 避免 CI 上镜像同步不完整干扰 plugin 解析)
        maven { url = uri("https://maven.aliyun.com/repository/google/") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin/") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Miuix 组件库仓库
        maven { url = uri("https://jitpack.io") }
        // Sonatype OSSRH 仓库
        maven { url = uri("https://s01.oss.sonatype.org/content/repositories/releases/") }
        // OSSS仓库
        maven { url = uri("https://oss.sonatype.org/content/repositories/releases/") }
        // 阿里云镜像(本地加速; 放最后, 避免 CI 干扰)
        maven { url = uri("https://maven.aliyun.com/repository/google/") }
        maven { url = uri("https://maven.aliyun.com/repository/public/") }
        maven { url = uri("https://maven.aliyun.com/repository/central/") }
    }
}

rootProject.name = "8319 Schedule"
include(":app")
