plugins {
    id("com.diffplug.spotless") version "6.25.0"
    id("org.jetbrains.dokka") version "1.9.20"
}

buildscript {
    dependencies {
        classpath("javax.xml.bind:jaxb-api:2.3.1")
        classpath("com.sun.xml.bind:jaxb-core:2.3.0.1")
        classpath("com.sun.xml.bind:jaxb-impl:2.3.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.22")
        classpath("com.android.tools.build:gradle:8.8.1")
        classpath("org.eclipse.keyple:keyple-gradle:0.2.+") { isChanging = true }
    }

    repositories {
        mavenLocal()
        mavenCentral()
        google()
    }
}

allprojects {
    group = "org.calypsonet.keyple"
    repositories {
        mavenLocal()
        mavenCentral()
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
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
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
    targetCompatibility = JavaVersion.VERSION_1_8.toString()
}