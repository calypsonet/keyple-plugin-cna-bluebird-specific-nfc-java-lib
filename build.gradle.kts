plugins {
    id("com.diffplug.spotless") version "6.25.0"
    id("org.sonarqube") version "4.4.1.3373"
    id("org.jetbrains.dokka") version "1.9.20"
}

buildscript {
    val kotlinVersion: String by project

    dependencies {
        // JAXB dependencies pour le build
        classpath("javax.xml.bind:jaxb-api:2.3.1")
        classpath("com.sun.xml.bind:jaxb-core:2.3.0.1")
        classpath("com.sun.xml.bind:jaxb-impl:2.3.1")

        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("com.android.tools.build:gradle:8.2.2")
        classpath("org.eclipse.keyple:keyple-gradle:0.2.+") { isChanging = true }
    }

    repositories {
        mavenLocal()
        maven(url = "https://repo.eclipse.org/service/local/repositories/maven_central/content")
        mavenCentral()
        google()
    }
}

allprojects {
    group = "org.calypsonet.keyple"

    // JAXB dependencies pour tous les sous-projets
    configurations.all {
        resolutionStrategy.dependencySubstitution {
            substitute(module("javax.xml.bind:jaxb-api"))
                .using(module("javax.xml.bind:jaxb-api:2.3.1"))
            substitute(module("com.sun.xml.bind:jaxb-core"))
                .using(module("com.sun.xml.bind:jaxb-core:2.3.0.1"))
            substitute(module("com.sun.xml.bind:jaxb-impl"))
                .using(module("com.sun.xml.bind:jaxb-impl:2.3.1"))
        }
    }

    repositories {
        mavenLocal()
        maven(url = "https://repo.eclipse.org/service/local/repositories/maven_central/content")
        mavenCentral()
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
        maven(url = "https://s01.oss.sonatype.org/content/repositories/snapshots")
        google()
    }
}

tasks {
    spotless {
        kotlin {
            target("**/*.kt")
            ktfmt()
            licenseHeaderFile("${project.rootDir}/LICENSE_HEADER")
        }
        java {
            target("**/src/**/*.java")
            licenseHeaderFile("${project.rootDir}/LICENSE_HEADER")
            importOrder("java", "javax", "org", "com", "")
            removeUnusedImports()
            googleJavaFormat()
        }
    }
    sonarqube {
        properties {
            property("sonar.projectKey", "eclipse_" + project.name)
            property("sonar.organization", "eclipse")
            property("sonar.host.url", "https://sonarcloud.io")
            property("sonar.login", System.getenv("SONAR_LOGIN"))
            property("sonar.gradle.skipCompile", "true")
            System.getenv("BRANCH_NAME")?.let {
                if (it != "main") {
                    property("sonar.branch.name", it)
                }
            }
        }
    }
}