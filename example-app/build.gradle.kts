///////////////////////////////////////////////////////////////////////////////
//  GRADLE CONFIGURATION
///////////////////////////////////////////////////////////////////////////////

plugins {
  id("com.android.application")
  id("kotlin-android")
  id("kotlin-parcelize")
  id("com.diffplug.spotless")
  id("org.jetbrains.dokka")
  signing
  `maven-publish`
}

///////////////////////////////////////////////////////////////////////////////
//  APP CONFIGURATION
///////////////////////////////////////////////////////////////////////////////

dependencies {
  // Bluebird Keyple plugin
  implementation(project(path = ":plugin"))
  // Bluebird specific components
  implementation(
      fileTree(
          mapOf(
              "dir" to "libs",
              "include" to listOf("*.jar", "*.aar"),
              "exclude" to listOf("*-mock.jar"))))

  // Kotlin
  implementation(kotlin("stdlib-jdk8"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

  // Begin Keyple configuration (inspired by reference project)
  implementation(platform("org.eclipse.keyple:keyple-java-bom:2025.12.12"))
  implementation("org.eclipse.keypop:keypop-reader-java-api")
  implementation("org.eclipse.keypop:keypop-calypso-card-java-api")
  implementation("org.eclipse.keypop:keypop-calypso-crypto-legacysam-java-api")
  implementation("org.eclipse.keypop:keypop-storagecard-java-api:1.1.0-SNAPSHOT") {
    isChanging = true
  }
  implementation("org.eclipse.keyple:keyple-common-java-api")
  implementation("org.eclipse.keyple:keyple-util-java-lib")
  implementation("org.eclipse.keyple:keyple-service-java-lib")
  implementation("org.eclipse.keyple:keyple-card-calypso-java-lib")
  implementation("org.eclipse.keyple:keyple-plugin-storagecard-java-api")
  implementation("org.eclipse.keyple:keyple-card-calypso-crypto-legacysam-java-lib")

  // End Keyple configuration

  // Android components
  implementation("androidx.appcompat:appcompat:1.7.1")
  implementation("androidx.cardview:cardview:1.0.0")
  implementation("androidx.recyclerview:recyclerview:1.4.0")
  implementation("androidx.constraintlayout:constraintlayout:2.2.1")
  implementation("androidx.activity:activity-ktx:1.8.1")
  implementation("androidx.fragment:fragment-ktx:1.6.2")
  implementation("androidx.multidex:multidex:2.0.1")

  // Logging
  implementation("com.jakewharton.timber:timber:5.0.1")
  implementation("org.slf4j:slf4j-api:1.7.32")
  implementation("uk.uuid.slf4j:slf4j-android:1.7.32-0")
}

///////////////////////////////////////////////////////////////////////////////
//  STANDARD CONFIGURATION FOR ANDROID APPLICATION KOTLIN-BASED PROJECTS
///////////////////////////////////////////////////////////////////////////////

if (project.hasProperty("releaseTag")) {
  project.version = project.property("releaseTag") as String
  println("Release mode: version set to ${project.version}")
} else {
  println("Development mode: version is ${project.version}")
}

val title: String by project
val javaSourceLevel: String by project
val javaTargetLevel: String by project
val generatedOverviewFile = layout.buildDirectory.file("tmp/overview-dokka.md")

android {
  namespace = project.findProperty("androidAppNamespace") as String
  compileSdk = (project.findProperty("androidCompileSdk") as String).toInt()
  defaultConfig {
    applicationId = project.findProperty("androidAppId") as String
    minSdk = (project.findProperty("androidMinSdk") as String).toInt()
    targetSdk = (project.findProperty("androidCompileSdk") as String).toInt()
    versionCode = (project.findProperty("androidAppVersionCode") as String).toInt()
    versionName = project.findProperty("androidAppVersionName") as String
  }
  buildFeatures {
    buildConfig = true
    viewBinding = true
  }
  buildTypes {
    getByName("release") {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.toVersion(javaSourceLevel)
    targetCompatibility = JavaVersion.toVersion(javaTargetLevel)
  }
  kotlinOptions { jvmTarget = javaTargetLevel }
  sourceSets {
    getByName("main").java.srcDirs("src/main/kotlin")
    getByName("debug").java.srcDirs("src/debug/kotlin")
  }
  packagingOptions {
    resources {
      excludes.add("META-INF/NOTICE.md")
      excludes.add("META-INF/LICENSE.md")
      excludes.add("META-INF/LICENSE-notice.md")
    }
  }
  applicationVariants.all {
    outputs.all {
      val outputImpl = this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
      val variantName = name
      val versionName = project.version.toString()
      val newName = "${rootProject.name}-$versionName-$variantName.apk"
      outputImpl.outputFileName = newName
    }
  }
  publishing {
    singleVariant("release") {
      // No withSourcesJar() and withJavadocJar(), as we'll configure them manually during release
    }
  }
  lint { abortOnError = false }
}

fun copyLicenseFiles() {
  val metaInfDir = File(layout.buildDirectory.get().asFile, "resources/main/META-INF")
  val licenseFile = File(project.rootDir, "LICENSE")
  val noticeFile = File(project.rootDir, "NOTICE.md")
  metaInfDir.mkdirs()
  licenseFile.copyTo(File(metaInfDir, "LICENSE"), overwrite = true)
  noticeFile.copyTo(File(metaInfDir, "NOTICE.md"), overwrite = true)
}

tasks.withType<AbstractArchiveTask>().configureEach { archiveBaseName.set(rootProject.name) }

tasks {
  spotless {
    kotlin {
      target("src/**/*.kt")
      licenseHeaderFile("${project.rootDir}/LICENSE_HEADER")
      ktfmt()
    }
    kotlinGradle {
      target("**/*.kts")
      ktfmt()
    }
  }
  register("generateDokkaOverview") {
    outputs.file(generatedOverviewFile)
    doLast {
      val file = generatedOverviewFile.get().asFile
      file.parentFile.mkdirs()
      file.writeText(
          buildString {
            appendLine("# Module $title")
            appendLine()
            appendLine(
                file("src/main/kdoc/overview.md")
                    .takeIf { it.exists() }
                    ?.readText()
                    .orEmpty()
                    .trim())
            appendLine()
            appendLine("<br>")
            appendLine()
            appendLine("> ${project.findProperty("javadoc.copyright") as String}")
          })
    }
  }
  dokkaHtml.configure {
    dependsOn("generateDokkaOverview")
    dokkaSourceSets {
      named("main") {
        noAndroidSdkLink.set(false)
        includeNonPublic.set(false)
        includes.from(files(generatedOverviewFile))
        moduleName.set(title)
      }
    }
    doFirst { println("Generating Dokka HTML for ${project.name} version ${project.version}") }
  }
  withType<Jar>().configureEach {
    if (archiveClassifier.get() == "sources") {
      doFirst { copyLicenseFiles() }
      manifest {
        attributes(
            mapOf(
                "Implementation-Title" to "$title Sources",
                "Implementation-Version" to project.version))
      }
    }
  }
  register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(android.sourceSets.getByName("main").java.srcDirs)
    from(layout.buildDirectory.dir("resources/main"))
    doFirst { copyLicenseFiles() }
    manifest {
      attributes(
          mapOf(
              "Implementation-Title" to "$title Documentation",
              "Implementation-Version" to project.version))
    }
  }
  register<Jar>("javadocJar") {
    dependsOn(dokkaHtml)
    archiveClassifier.set("javadoc")
    from(dokkaHtml.flatMap { it.outputDirectory })
    from(layout.buildDirectory.dir("resources/main"))
    doFirst { copyLicenseFiles() }
    manifest {
      attributes(
          mapOf(
              "Implementation-Title" to "$title Documentation",
              "Implementation-Version" to project.version))
    }
  }
  register("copyLicenseFiles") { doLast { copyLicenseFiles() } }
}

afterEvaluate {
  tasks.named("assembleRelease") { dependsOn("copyLicenseFiles") }
  publishing {
    publications {
      create<MavenPublication>("mavenJava") {
        from(components["release"])
        artifactId = rootProject.name
        artifact(tasks["sourcesJar"])
        artifact(tasks["javadocJar"])
        pom {
          name.set(project.findProperty("title") as String)
          description.set(project.findProperty("description") as String)
          url.set(project.findProperty("project.url") as String)
          licenses {
            license {
              name.set(project.findProperty("license.name") as String)
              url.set(project.findProperty("license.url") as String)
              distribution.set(project.findProperty("license.distribution") as String)
            }
          }
          developers {
            developer {
              name.set(project.findProperty("developer.name") as String)
              email.set(project.findProperty("developer.email") as String)
            }
          }
          organization {
            name.set(project.findProperty("organization.name") as String)
            url.set(project.findProperty("organization.url") as String)
          }
          scm {
            connection.set(project.findProperty("scm.connection") as String)
            developerConnection.set(project.findProperty("scm.developerConnection") as String)
            url.set(project.findProperty("scm.url") as String)
          }
          ciManagement {
            system.set(project.findProperty("ci.system") as String)
            url.set(project.findProperty("ci.url") as String)
          }
          properties.set(
              mapOf(
                  "project.build.sourceEncoding" to "UTF-8",
                  "maven.compiler.source" to javaSourceLevel,
                  "maven.compiler.target" to javaTargetLevel))
        }
      }
    }
    repositories {
      maven {
        if (project.hasProperty("sonatypeURL")) {
          url = uri(project.property("sonatypeURL") as String)
          credentials {
            username = project.property("sonatypeUsername") as String
            password = project.property("sonatypePassword") as String
          }
        }
      }
    }
  }
  signing {
    if (project.hasProperty("releaseTag")) {
      useGpgCmd()
      sign(publishing.publications["mavenJava"])
    }
  }
}
