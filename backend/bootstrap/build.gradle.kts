description = "Composition root: Ktor server entrypoint, Koin modules, configuration and infrastructure adapters."

plugins {
    application
}

application {
    mainClass.set("com.tahirslist.bootstrap.ApplicationKt")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":web-api"))
    implementation(project(":persistence"))
    implementation(project(":storage-s3"))
    implementation(project(":verification-ai"))
    implementation(project(":geocoding"))
    implementation(project(":ingestion"))
    implementation(project(":mcp"))

    implementation(platform(libs.ktor.bom))
    implementation(platform(libs.koin.bom))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.koin.core)
    implementation(libs.koin.ktor)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.kotlinx.coroutines.core)

    // Compile-time types leaked by adapter APIs wired here (Gradle `implementation`
    // is not transitive): Exposed Database, HikariCP, Flyway result, S3 client, Ktor client.
    implementation(platform(libs.exposed.bom))
    implementation(libs.exposed.jdbc)
    implementation(libs.hikaricp)
    implementation(libs.flyway.core)
    implementation(platform(libs.aws.kotlin.bom))
    implementation(libs.aws.kotlin.s3)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    // Argon2id (BouncyCastle behind PasswordHasher) + RS256 JWT (Nimbus behind TokenIssuer).
    implementation(libs.bouncycastle)
    implementation(libs.nimbus.jose.jwt)

    // JDBC driver + logging at runtime.
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.logback.classic)

    // Full-stack endpoint tests: in-process Ktor app + real PostGIS container.
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.koin.test)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.postgresql)
}

/**
 * SC-211 ingestion CLIs. Both run in the container image via the scripts, not
 * on the host (no host toolchain).
 */
tasks.register<JavaExec>("ingestSample") {
    group = "ingestion"
    description = "Fetch the Tier-1 directories and write a reviewed run directory."
    mainClass.set("com.tahirslist.bootstrap.ingestion.SampleIngestMainKt")
    classpath = sourceSets["main"].runtimeClasspath
    (project.findProperty("ingestArgs") as String?)
        ?.split(' ')
        ?.filter { it.isNotBlank() }
        ?.let { args(it) }
}

tasks.register<JavaExec>("loadIngestBatch") {
    group = "ingestion"
    description = "Preview (and with --execute apply) a reviewed ingestion run."
    mainClass.set("com.tahirslist.bootstrap.ingestion.LoadIngestBatchMainKt")
    classpath = sourceSets["main"].runtimeClasspath
    // JavaExec defaults stdin to an empty stream; forward the container's stdin
    // so the `--interactive` decisions can actually be answered.
    standardInput = System.`in`
    (project.findProperty("ingestArgs") as String?)
        ?.split(' ')
        ?.filter { it.isNotBlank() }
        ?.let { args(it) }
}

/**
 * SC-231 UC-03: the cover-photo pass. Separate from the load on purpose — a
 * photo source outage must not fail an ingestion batch.
 */
tasks.register<JavaExec>("attachCoverPhotos") {
    group = "ingestion"
    description = "Preview (and with --execute fetch) cover photos for listings."
    mainClass.set("com.tahirslist.bootstrap.photo.AttachCoverPhotosMainKt")
    classpath = sourceSets["main"].runtimeClasspath
    (project.findProperty("ingestArgs") as String?)
        ?.split(' ')
        ?.filter { it.isNotBlank() }
        ?.let { args(it) }
}

/**
 * SC-231 D-12: the one-off purge of rows the admission gate would never admit.
 * Dry run by default; `--execute` refuses without a backup marker.
 */
tasks.register<JavaExec>("purgeNonRestaurants") {
    group = "ingestion"
    description = "List (and with --execute --backup=<dump> remove) non-restaurant rows."
    mainClass.set("com.tahirslist.bootstrap.photo.PurgeNonRestaurantsMainKt")
    classpath = sourceSets["main"].runtimeClasspath
    (project.findProperty("ingestArgs") as String?)
        ?.split(' ')
        ?.filter { it.isNotBlank() }
        ?.let { args(it) }
}
