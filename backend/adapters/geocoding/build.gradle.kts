description = "Geocoding adapter: Photon (photon.komoot.io) implementing the GeocoderPort (Ktor client + kotlinx.serialization)."

dependencies {
    implementation(project(":application"))
    implementation(project(":domain")) // direct: PhotonGeocoder maps responses to domain LatLng

    implementation(platform(libs.ktor.bom))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
}
