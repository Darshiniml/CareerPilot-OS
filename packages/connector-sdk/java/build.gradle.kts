plugins {
    java
    id("io.freefair.lombok")
}

dependencies {
    implementation(project(":packages:shared-dto:java"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.4")
}
