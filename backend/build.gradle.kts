import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Version-catalog accessors are only generated for the root script scope; capture
// the shared test dependencies here so `subprojects {}` can reference them.
val testKotestRunner = libs.kotest.runner.junit5
val testKotestAssertions = libs.kotest.assertions.core
val testMockk = libs.mockk
val testCoroutinesTest = libs.kotlinx.coroutines.test

allprojects {
    group = "com.tahirslist"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    // kotlinx.serialization compiler plugin: compile-time generated serializers,
    // no reflection (the Kotlin-first replacement for Jackson).
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // Docker Desktop 4.52+ ships Engine 29.x, which rejects docker-java's
        // default negotiated API version (1.32) with HTTP 400. Pin a modern API
        // version the engine accepts so Testcontainers can connect in this
        // environment. See testcontainers-java #11212 / #11235.
        systemProperty("api.version", "1.44")
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
            showStackTraces = true
        }
    }

    dependencies {
        "testImplementation"(kotlin("test"))
        "testImplementation"(testKotestRunner)
        "testImplementation"(testKotestAssertions)
        "testImplementation"(testMockk)
        "testImplementation"(testCoroutinesTest)
    }
}
