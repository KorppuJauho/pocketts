import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The TeamSpeak connection itself: plain Kotlin on the JVM, no Android, so it's tested on the build machine.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    useJUnitPlatform()
    environment("TS_TEST_SERVER", System.getenv("TS_TEST_SERVER") ?: "")
    environment("TS_TEST_FT_PORT", System.getenv("TS_TEST_FT_PORT") ?: "")
    environment("TS_LOAD_CLIENTS", System.getenv("TS_LOAD_CLIENTS") ?: "")
    environment("TS_LOAD_SECONDS", System.getenv("TS_LOAD_SECONDS") ?: "")
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
