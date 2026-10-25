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
        google()
        mavenCentral()
    }
}

rootProject.name = "PowerAi"
include(":app")
include(":core:model-contract")
include(":core:domain")
include(":core:data")
include(":engine:native")
include(":engine:ai")
include(":feature:search-chat")
include(":feature:hybrid")
include(":feature:knowledge-detail")
include(":feature:pdf")
include(":feature:database")
include(":feature:settings")
include(":benchmark")
