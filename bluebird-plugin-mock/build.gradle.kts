plugins {
    id("com.android.library")
    id("kotlin-android")
    id("org.jetbrains.dokka")
    id("com.diffplug.spotless")
}

val archivesBaseName: String by project

android {
    namespace = "org.calypsonet.keyple.plugin.bluebird.mock"
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    buildFeatures {
        viewBinding = true
    }

    defaultConfig {
        minSdk = 21
        // targetSdk retiré car déprécié pour les bibliothèques

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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

    testOptions {
        unitTests.apply {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
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

    kotlinOptions {
        jvmTarget = javaTargetLevel
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
        getByName("debug").java.srcDirs("src/debug/kotlin")
        getByName("test").java.srcDirs("src/test/kotlin")
        getByName("androidTest").java.srcDirs("src/androidTest/kotlin")
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${property("kotlinVersion")}")

    // bluebird libs
    compileOnly(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    //keyple
    implementation("org.eclipse.keyple:keyple-common-java-api:2.0.1")
    implementation("org.eclipse.keyple:keyple-plugin-java-api:2.3.1")
    implementation("org.eclipse.keyple:keyple-util-java-lib:2.4.0")

    //android
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.appcompat:appcompat:1.1.0")

    //Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    //logging
    implementation("org.slf4j:slf4j-api:1.7.32")
    implementation("com.jakewharton.timber:timber:4.7.1")
    implementation("com.arcao:slf4j-timber:3.1@aar")

    /** Test **/
    testImplementation("androidx.test:core-ktx:1.3.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.9")
    testImplementation("org.robolectric:robolectric:4.3.1")

    androidTestImplementation("androidx.test.ext:junit:1.1.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.2.0")
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