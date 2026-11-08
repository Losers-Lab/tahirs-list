description = "SC-211 certifier library: Halal Monitoring Services (HMS) certified-listing directory. Own domain model; no Tahir's List dependencies."

dependencies {
    api(project(":certifier-core"))
    implementation(libs.jsoup)
}
