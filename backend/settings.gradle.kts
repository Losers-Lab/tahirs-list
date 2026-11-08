rootProject.name = "tahirs-list-backend"

include(":domain")
include(":application")

// Hexagonal adapters, nested physically under adapters/ but registered with
// top-level project names so the module graph matches the ratified architecture:
//   :domain -> :application -> adapters -> :bootstrap
include(":persistence", ":storage-s3", ":verification-ai", ":verification-committee", ":web-api", ":mcp")
include(":geocoding")
include(":bootstrap")

// SC-211: standalone certifier libraries. Each owns its own domain model and
// depends only on :certifier-core (transport/politeness) — never on :domain or
// :application, so they can be extracted into independent repositories later.
include(":certifier-core", ":certifier-hms", ":certifier-hfsaa", ":certifier-hma")
// App-side translation boundary: library models -> Tahir's List ingestion model.
include(":ingestion")

project(":certifier-core").projectDir = file("libraries/certifier-core")
project(":certifier-hms").projectDir = file("libraries/certifier-hms")
project(":certifier-hfsaa").projectDir = file("libraries/certifier-hfsaa")
project(":certifier-hma").projectDir = file("libraries/certifier-hma")
project(":ingestion").projectDir = file("ingestion")

project(":persistence").projectDir = file("adapters/persistence")
project(":geocoding").projectDir = file("adapters/geocoding")
project(":storage-s3").projectDir = file("adapters/storage-s3")
project(":verification-ai").projectDir = file("adapters/verification-ai")
project(":verification-committee").projectDir = file("adapters/verification-committee")
project(":web-api").projectDir = file("adapters/web-api")
project(":mcp").projectDir = file("adapters/mcp")
