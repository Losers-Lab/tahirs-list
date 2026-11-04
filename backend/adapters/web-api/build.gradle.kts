description = "Web adapter: Ktor route modules, DTOs, status pages, JWT authentication and rate limiting."

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))

    implementation(platform(libs.ktor.bom))
    implementation(platform(libs.koin.bom))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.request.validation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // Koin resolves route dependencies from the composition root.
    implementation(libs.koin.core)

    // RS256 verification (Nimbus stays behind the Ktor AuthenticationProvider).
    implementation(libs.nimbus.jose.jwt)

    // In-process token-bucket rate limiting (parity with the reviewed sc-136 semantics).
    implementation(libs.bucket4j)
    implementation(libs.kotlin.logging.jvm)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
}
