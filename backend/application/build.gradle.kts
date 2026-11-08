description = "Application layer: orchestration of use cases (ports in, ports out), framework-free and coroutine-based."

plugins {
    // Exposes application-layer contract specs (ImagePortContractSpec) as test
    // fixtures so every adapter module runs the SAME assertions against its own
    // implementation — the "adapter swap test" proof for sc-157.
    `java-test-fixtures`
}

dependencies {
    implementation(project(":domain"))

    // Use cases and out-ports are suspend-based; consumers see those types.
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.datetime)

    // This module's own tests run the contract spec against the in-memory fake.
    testImplementation(testFixtures(project(":application")))

    // The contract specs in src/testFixtures use Kotest (versions from the catalog).
    testFixturesImplementation(libs.kotest.runner.junit5)
    testFixturesImplementation(libs.kotest.assertions.core)
    // Fixtures also build domain aggregates and application results.
    testFixturesImplementation(project(":domain"))
    testFixturesImplementation(libs.kotlinx.datetime)
}
