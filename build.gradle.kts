plugins {
    java
    id("org.springframework.boot") version "3.2.5" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
    id("io.freefair.lombok") version "8.6" apply false
}

allprojects {
    group = "com.careerpilot"
    version = "1.0.0"

    // Optional: keep build output outside a synced folder (e.g. OneDrive locks files under build/).
    System.getenv("CAREERPILOT_BUILD_ROOT")?.let { root ->
        layout.buildDirectory.set(file("$root/${project.path.replace(':', '_')}"))
    }

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }
}
