rootProject.name = "keyple-plugin-cna-bluebird-specific-nfc-java-lib"
include(":bluebird-plugin")
include(":bluebird-plugin-mock")
include(":example-app")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        mavenCentral()
        google()
        maven(url = "https://central.sonatype.com/repository/maven-snapshots")
    }
}

