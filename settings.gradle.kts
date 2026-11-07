pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
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
    include(":app", ":core:data", ":integration:usage", ":integration:health")
}
