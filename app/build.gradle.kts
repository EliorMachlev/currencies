@file:Suppress("UnstableApiUsage")

import java.util.Properties
import java.io.FileInputStream
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    // Pinned to the latest stable 2.3.x — 2.4.0 flakes in CI's plugin repos.
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
    id("com.google.devtools.ksp") version "2.3.9"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

base {
    archivesName.set("de.salomax.currencies-v12300")
}

android {
    namespace = "de.salomax.currencies"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "de.salomax.currencies"
        minSdk = 26
        targetSdk = 37
        // SemVer
        versionName = "1.23.0"
        versionCode = 12300
    }

    signingConfigs {
        create("release") {
            if (getSecret("KEYSTORE_FILE") != null) {
                storeFile = File(getSecret("KEYSTORE_FILE")!!)
                storePassword = getSecret("KEYSTORE_PASSWORD")
                keyAlias = getSecret("KEYSTORE_KEY_ALIAS")
                keyPassword = getSecret("KEYSTORE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = " [DEBUG]"
        }
    }

    flavorDimensions.add("version")
    productFlavors {
        create("play") {
            dimension = "version"
        }
        create("fdroid") {
            dimension = "version"
        }
    }

    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_21)
        targetCompatibility(JavaVersion.VERSION_21)
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    lint {
        disable.add("MissingTranslation")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    // kotlin
    implementation("androidx.core:core-ktx:1.19.0")
    // support libs
    val appCompatVersion = "1.7.1"
    implementation("androidx.appcompat:appcompat:$appCompatVersion")
    implementation("androidx.appcompat:appcompat-resources:$appCompatVersion")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    val livecycleVersion = "2.11.0"
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$livecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$livecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$livecycleVersion")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("androidx.window:window:1.5.1")
    implementation("com.google.android.material:material:1.14.0")
    // downloader
    val fuelVersion = "2.3.1"
    implementation("com.github.kittinunf.fuel:fuel:$fuelVersion")
    implementation("com.github.kittinunf.fuel:fuel-android:$fuelVersion")
    implementation("com.github.kittinunf.fuel:fuel-coroutines:$fuelVersion")
    implementation("com.github.kittinunf.fuel:fuel-moshi:$fuelVersion")
    val moshiVersion = "1.15.2"
    implementation("com.squareup.moshi:moshi-kotlin:$moshiVersion")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:$moshiVersion")
    // math (v5 releases use incompatible license to fdroid: noinspection GradleDependency)
    implementation("org.mariuszgromada.math:MathParser.org-mXparser:4.4.3")
    // compose (needed to host the Vico chart via ComposeView)
    val composeBomVersion = "2024.12.01"
    implementation(platform("androidx.compose:compose-bom:$composeBomVersion"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.runtime:runtime-livedata")
    // charts
    val vicoVersion = "3.2.3"
    implementation("com.patrykandpatrick.vico:compose:$vicoVersion")
    // crypto: BouncyCastle provides pure-Java Argon2id, used by BackupManager
    // for password-based backup encryption (quantum-resistant KDF).
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    // test
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.23.0")
    // core-testing provides InstantTaskExecutorRule so LiveData setValue can
    // run on the JVM test thread without hitting the main-thread assertion.
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    // fuzzing
    val junitVersion = "6.1.1"
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:$junitVersion")
    testImplementation("com.code-intelligence:jazzer-junit:0.30.0")
}

fun getSecret(key: String): String? {
    val secretsFile: File = rootProject.file("secrets.properties")
    return if (secretsFile.exists()) {
        val props = Properties()
        props.load(FileInputStream(secretsFile))
        props.getProperty(key)
    } else {
        null
    }
}

// versionCode <-> versionName /////////////////////////////////////////////////////////////////////

/**
 * Checks if versionCode and versionName match.
 * Needed because of F-Droid: both have to be hard-coded and can't be assigned dynamically.
 * So at least check during build for them to match.
 */
tasks.register("checkVersion") {
    doLast {
        val versionCode: Int? = android.defaultConfig.versionCode
        val correctVersionCode: Int = generateVersionCode(android.defaultConfig.versionName!!)
        if (versionCode != correctVersionCode) throw GradleException(
            "versionCode and versionName don't match: versionCode should be $correctVersionCode. Is $versionCode."
        )
    }
}
tasks.findByName("assemble")!!.dependsOn(tasks.findByName("checkVersion")!!)

/**
 * Checks if a fastlane changelog for the current version is present.
 */
tasks.register("checkFastlaneChangelog") {
    doLast {
        val versionCode: Int? = android.defaultConfig.versionCode
        val changelogFile: File =
            file("$rootDir/fastlane/metadata/android/en-US/changelogs/${versionCode}.txt")
        if (!changelogFile.exists())
            throw GradleException(
                "Fastlane changelog missing: expecting file '$changelogFile'"
            )
    }
}
tasks.findByName("build")!!.dependsOn(tasks.findByName("checkFastlaneChangelog")!!)

/**
 * Generates a versionCode based on the given semVer String.
 *
 * @param semVer e.g. 1.3.1
 * @return e.g. 10301 (-> 1 03 01)
 */
fun generateVersionCode(semVer: String): Int {
    return semVer.split('.')
        .map { Integer.parseInt(it) }
        .reduce { sum, value -> sum * 100 + value }
}
