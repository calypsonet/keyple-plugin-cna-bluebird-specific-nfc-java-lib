rootProject.name = "keyple-plugin-cna-bluebird-specific-nfc-java-lib"

include(":plugin")

include(":plugin-mock")

include(":example-app")

pluginManagement {
  repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    mavenLocal()
    maven(url = "https://central.sonatype.com/repository/maven-snapshots")
  }
}
