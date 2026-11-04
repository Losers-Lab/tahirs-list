description = "Hexagonal domain core: pure Kotlin, no framework dependencies."

dependencies {
    // Civil dates (certification expiry) are exposed from domain types, so this
    // is an `api` dependency. Instants/Clock come from kotlin.time (stdlib).
    api(libs.kotlinx.datetime)
}
