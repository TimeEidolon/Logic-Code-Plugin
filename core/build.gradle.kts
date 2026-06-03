import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // Single source of truth: repository root /java → classpath java/*.md
    from(rootProject.layout.projectDirectory.dir("java")) {
        into("java")
    }
}

// This is a library module. Disable IDE runner/sandbox tasks here to avoid multiple sandboxes.
tasks.matching { t ->
    val n = t.name
    n == "runIde" ||
        n.startsWith("runIde") ||
        n.contains("Sandbox", ignoreCase = true) ||
        n.startsWith("prepareSandbox") ||
        n.startsWith("prepareTestSandbox") ||
        n.startsWith("cleanSandbox")
}.configureEach {
    enabled = false
}

kotlin {
    sourceSets {
        main {
            kotlin.srcDirs("src/main/kotlin")
            resources.srcDirs("src/main/resources")
        }
        test {
            // Keep tests at root module to avoid duplicating test framework setup.
            kotlin.setSrcDirs(emptyList<String>())
            resources.setSrcDirs(emptyList<String>())
        }
    }
}

dependencies {
    implementation("org.webjars.npm:mermaid:11.15.0")
    implementation("org.webjars:webjars-locator-core:0.59")

    // local-pkg:1.1.2 requires pkg-types:[2.3.0,3.0.0-0), which is not published on Maven Central yet.
    constraints {
        implementation("org.webjars.npm:local-pkg:1.1.1") {
            because("Maven Central only has pkg-types up to 2.1.0 (webjars sync gap)")
        }
    }

    intellijPlatform {
        intellijIdea("2025.3")
        testFramework(TestFrameworkType.Platform)
    }
}

