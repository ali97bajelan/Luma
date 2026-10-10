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

rootProject.name = "Luma"
include(":core:domain")
if (!providers.gradleProperty("domainOnly").map(String::toBoolean).getOrElse(false)) {
    include(":app")
}
