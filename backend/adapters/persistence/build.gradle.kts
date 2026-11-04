description = "Persistence adapter: PostgreSQL/PostGIS via Exposed 1.6 + HikariCP; Flyway migration executor behind a Kotlin facade."

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))

    implementation(platform(libs.exposed.bom))
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.hikaricp)

    // Flyway stays the migration *executor* (no maintained Kotlin-native
    // equivalent); invoked from Kotlin behind a thin facade. SQL migrations
    // under src/main/resources/db/migration are unchanged.
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)

    // Test harness: Testcontainers PostGIS proves the TDD harness.
    testImplementation(libs.postgresql)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.slf4j.simple)
}
