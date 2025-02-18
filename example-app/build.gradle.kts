plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-parcelize")
    id("com.diffplug.spotless")
}

android {
    namespace = "org.calypsonet.keyple.plugin.bluebird.example"
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    buildFeatures {
        viewBinding = true
    }

    defaultConfig {
        applicationId = "org.calypsonet.keyple.plugin.bluebird.example"
        minSdk = 21
        targetSdk = 33
        versionName = project.version.toString()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
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
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources {
            excludes.add("META-INF/NOTICE.md")
        }
    }

    lint {
        abortOnError = false
    }

    kotlinOptions {
        jvmTarget = javaTargetLevel
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
        getByName("test").java.srcDirs("src/test/kotlin")
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_configuration:2.0.4")

    //Keyple Common
    implementation(project(path = ":bluebird-plugin"))

    // Begin Keyple configuration (generated by 'https://keyple.org/components/overview/configuration-wizard/')
    implementation("org.eclipse.keypop:keypop-reader-java-api:2.0.1")
    implementation("org.eclipse.keypop:keypop-calypso-card-java-api:2.1.0")
    implementation("org.eclipse.keypop:keypop-calypso-crypto-legacysam-java-api:0.7.0")
    implementation("org.eclipse.keyple:keyple-common-java-api:2.0.1")
    implementation("org.eclipse.keyple:keyple-util-java-lib:2.4.0")
    implementation("org.eclipse.keyple:keyple-service-java-lib:3.3.4")
    implementation("org.eclipse.keyple:keyple-card-calypso-java-lib:3.1.6")
    implementation("org.eclipse.keyple:keyple-card-calypso-crypto-legacysam-java-lib:0.9.0")
    // End Keyple configuration

    /*
    Android components
    */
    implementation("androidx.appcompat:appcompat:1.2.0")
    implementation("com.google.android.material:material:1.3.0")
    implementation("androidx.constraintlayout:constraintlayout:1.1.3")
    implementation("androidx.activity:activity-ktx:1.2.1")
    implementation("androidx.fragment:fragment-ktx:1.3.1")

    /*
    Log
    */
    implementation("org.slf4j:slf4j-api:1.7.32")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("com.arcao:slf4j-timber:3.1@aar")

    /*
    Kotlin
    */
    implementation("androidx.core:core-ktx:1.3.2")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:${property("kotlinVersion")}")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${property("kotlinVersion")}")

    /*
    Coroutines
    */
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation("androidx.multidex:multidex:2.0.1")

    testImplementation("junit:junit:4.12")
    androidTestImplementation("androidx.test.ext:junit:1.1.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.2.0")
}