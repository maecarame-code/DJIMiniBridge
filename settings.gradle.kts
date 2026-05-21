pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()

        maven {
            url = uri("https://developer.dji.com/maven")
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()

        maven {
            url = uri("https://developer.dji.com/maven")
        }
    }
}

rootProject.name = "DJIMiniBridge"

include(":app")