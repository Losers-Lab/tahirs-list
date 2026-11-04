description = "Verification adapter: hosted image-recognition model for certification photos (Ktor client + kotlinx.serialization)."

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))

    implementation(platform(libs.ktor.bom))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
}
