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
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (!providers.gradleProperty("serverOnly").map(String::toBoolean).getOrElse(false)) google()
        mavenCentral()
    }
}

rootProject.name = "FailureLudo"
// Build the backend without an Android SDK: -PserverOnly=true
if (!providers.gradleProperty("serverOnly").map(String::toBoolean).getOrElse(false)) include(":app")
include(":game-engine")
include(":online-server")
include(":online-protocol")
