import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

// This machine's own settings (dev.env, never committed): where debug builds send their log.
val devEnv: Map<String, String> = rootProject.file("dev.env").takeIf { it.exists() }?.readLines()
    ?.mapNotNull { line -> line.trim().takeIf { it.isNotEmpty() && !it.startsWith("#") && "=" in it } }
    ?.associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
    ?: emptyMap()

android {
    namespace = "io.github.korppujauho.mobilets6"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.korppujauho.mobilets6"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("String", "DEV_LOG_URL", "\"${devEnv["DEV_LOG_URL"].orEmpty()}\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "DEV_LOG_URL", "\"\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":tsproto"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    // Pictures: avatars, the server's icons (mostly SVG on TeamSpeak 6), pictures in the chat (also animated)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)
    implementation(libs.coil.network)

    // Screenshots of the screens, made on the build machine (scripts/dev.sh screenshots)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

roborazzi {
    outputDir.set(rootProject.file("docs/screenshots"))
}

tasks.withType<Test>().configureEach {
    // Robolectric reaches into Java internals that Java 21 keeps closed by default
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
}
