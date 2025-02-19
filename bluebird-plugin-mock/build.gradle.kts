plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-parcelize")
    id("org.jetbrains.dokka")
    id("com.diffplug.spotless")
}

val archivesBaseName: String by project

android {
    namespace = "org.calypsonet.keyple.plugin.bluebird.mock"
    compileSdk = 35

    buildFeatures {
        viewBinding = true
    }

    defaultConfig {
        minSdk = 21
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    val javaSourceLevel: String by project
    val javaTargetLevel: String by project
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(javaSourceLevel)
        targetCompatibility = JavaVersion.toVersion(javaTargetLevel)
    }

    kotlinOptions {
        jvmTarget = javaTargetLevel
    }

    lint {
        abortOnError = false
    }

    // generate output aar with a qualified name : with version number
    libraryVariants.all {
        outputs.forEach { output ->
            if (output is com.android.build.gradle.internal.api.BaseVariantOutputImpl) {
                output.outputFileName =
                    "$archivesBaseName-${project.version}-mock.${output.outputFile.extension}".replace("-SNAPSHOT", "")
            }
        }
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
        getByName("debug").java.srcDirs("src/debug/kotlin")
        getByName("test").java.srcDirs("src/test/kotlin")
        getByName("androidTest").java.srcDirs("src/androidTest/kotlin")
    }
}

dependencies {
    // Kotlin
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // Bluebird libs
    compileOnly(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    // Keyple
    implementation("org.eclipse.keyple:keyple-common-java-api:2.0.1")
    implementation("org.eclipse.keyple:keyple-plugin-java-api:2.3.1")
    implementation("org.eclipse.keyple:keyple-util-java-lib:2.4.0")

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")
}

tasks {
    dokkaHtml.configure {
        dokkaSourceSets {
            named("main") {
                noAndroidSdkLink.set(false)
                includeNonPublic.set(false)
                includes.from(files("src/main/kdoc/overview.md"))
            }
        }
    }
}

apply(plugin = "org.eclipse.keyple")