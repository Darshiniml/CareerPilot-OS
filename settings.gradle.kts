rootProject.name = "careerpilot-os"

// Include shared package subprojects (Java side)
include("packages:shared-dto:java")
include("packages:shared-events:java")
include("packages:connector-sdk:java")

// Include applications
include("apps:backend")

// Rename Gradle paths to map to their actual directories cleanly
project(":packages:shared-dto:java").projectDir = file("packages/shared-dto/java")
project(":packages:shared-events:java").projectDir = file("packages/shared-events/java")
project(":packages:connector-sdk:java").projectDir = file("packages/connector-sdk/java")
project(":apps:backend").projectDir = file("apps/backend")
