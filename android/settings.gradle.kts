pluginManagement {
    repositories {
        google()
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

rootProject.name = "FeedflowAndroid"

include(":app")
include(":core:model")
include(":core:data")
include(":core:database")
include(":core:network")
include(":core:security")
include(":core:ui")
