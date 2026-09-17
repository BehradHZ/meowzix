pluginManagement {
    repositories {
        maven("https://redirector.gvt1.com/edgedl/android/maven2") {
            name = "GoogleMavenRedirector"
            content {
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.android(\\..*)?")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        google {
            content {
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.android(\\..*)?")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://redirector.gvt1.com/edgedl/android/maven2") {
            name = "GoogleMavenRedirector"
            content {
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.android(\\..*)?")
                includeGroupByRegex("com\\.google\\.android(\\..*)?")
                includeGroupByRegex("com\\.google\\.dagger(\\..*)?")
            }
        }
        google {
            content {
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.android(\\..*)?")
                includeGroupByRegex("com\\.google\\.android(\\..*)?")
                includeGroupByRegex("com\\.google\\.dagger(\\..*)?")
            }
        }
    }
}

rootProject.name = "Meowzix"
include(":app")
