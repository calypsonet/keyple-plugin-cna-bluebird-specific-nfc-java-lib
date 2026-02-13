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
    maven {
      name = "GitHubPrivateArtifacts"
      url = uri("https://maven.pkg.github.com/calypsonet/private-java-packages")
      credentials {
        username = providers.gradleProperty("gpr.user").orNull ?: "github-actions"
        password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GPR_KEY")
      }
    }
  }
}
