rootProject.name = "careerpilot-os"

// Include shared package subprojects (Java side)
include("packages:shared-dto")
include("packages:shared-events")
include("packages:connector-sdk")

// Include applications
include("apps:backend")

// Rename Gradle paths to map to their actual directories cleanly
project(":packages:shared-dto").projectDir = file("packages/shared-dto/java")
project(":packages:shared-events").projectDir = file("packages/shared-events/java")
project(":packages:connector-sdk").projectDir = file("packages/connector-sdk/java")
project(":apps:backend").projectDir = file("apps/backend")
