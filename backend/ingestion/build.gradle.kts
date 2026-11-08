description = "SC-211 ingestion: explicit translators from each certifier library's own model into Tahir's List's source-listing model."

dependencies {
    api(project(":certifier-core"))
    implementation(project(":certifier-hms"))
    implementation(project(":certifier-hfsaa"))
    implementation(project(":certifier-hma"))

    // The pipeline builds application-layer candidates and calls the geocoder
    // port; the application layer never depends on a certifier library.
    implementation(project(":application"))
    implementation(project(":domain"))
}

/**
 * Ad-hoc live probe (SC-211). One polite pass over the three Tier-1 directories;
 * never part of `build` or `test`.
 */
tasks.register<JavaExec>("liveProbe") {
    group = "verification"
    description = "Hits HMS, HFSAA and HMA once each and prints record/field/metro counts."
    mainClass.set("com.tahirslist.ingestion.LiveProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}
