description = "MCP driving adapter: stateless Streamable HTTP endpoint exposing search_restaurants and get_restaurant to AI agents."

dependencies {
    implementation(project(":application"))
    implementation(project(":domain"))

    implementation(platform(libs.ktor.bom))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.mcp.sdk.server)

    implementation(libs.bucket4j)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.logging.jvm)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.client.cio)
    testImplementation(testFixtures(project(":application")))
}
