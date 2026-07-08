pluginManagement {
    repositories {
        // 阿里云镜像(仅本地加速; CI 上用官方仓库, 避免镜像同步不完整导致解析失败)
        if (System.getenv("CI") == null) {
            maven { url = uri("https://maven.aliyun.com/repository/google/") }
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin/") }
        }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 阿里云镜像(仅本地加速; CI 上用官方仓库)
        if (System.getenv("CI") == null) {
            maven { url = uri("https://maven.aliyun.com/repository/google/") }
            maven { url = uri("https://maven.aliyun.com/repository/public/") }
            maven { url = uri("https://maven.aliyun.com/repository/central/") }
        }
        google()
        mavenCentral()
        // Miuix 组件库仓库
        maven { url = uri("https://jitpack.io") }
        // Sonatype OSSRH 仓库
        maven { url = uri("https://s01.oss.sonatype.org/content/repositories/releases/") }
        // OSSS仓库
        maven { url = uri("https://oss.sonatype.org/content/repositories/releases/") }
    }
}

rootProject.name = "8319 Schedule"
include(":app")
