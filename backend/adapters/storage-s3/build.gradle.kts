description = "S3-compatible object storage adapter (MinIO local / Cloudflare R2 future) using the AWS SDK for Kotlin."

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))

    // AWS SDK for Kotlin (coroutines) — S3 client pointed at a configurable
    // endpoint. MinIO now, R2 later by config change; no AWS-only features.
    implementation(platform(libs.aws.kotlin.bom))
    implementation(libs.aws.kotlin.s3)

    // Contract spec shared from the application layer + Testcontainers MinIO for
    // the adapter-swap test.
    testImplementation(testFixtures(project(":application")))
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.minio)
    testRuntimeOnly(libs.slf4j.simple)
}
