plugins {
    java
}

dependencies {
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
    implementation(project(":packages:shared-dto"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.4")
}
