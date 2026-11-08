description = "SC-211 certifier library core: polite HTTP (rate limit, retries, robots, cache), snapshots, shared vocabulary. No Tahir's List domain dependencies."

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)

    implementation(platform(libs.ktor.bom))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
}
