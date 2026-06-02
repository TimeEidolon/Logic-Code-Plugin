import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // Single source of truth: repository skills/typescript → classpath ts/*.md
    from(rootProject.layout.projectDirectory.dir("skills/typescript")) {
        into("ts")
        rename { original ->
            when (original) {
                "SKILL.md" -> "TS_PROJECT_DECOMPOSER_SKILL.md"
                "reference.md" -> "TS_STACK_REFERENCE.md"
                else -> original
            }
        }
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
            kotlin.setSrcDirs(emptyList<String>())
            resources.setSrcDirs(emptyList<String>())
        }
    }
}

dependencies {
    implementation(project(":core"))

    intellijPlatform {
        intellijIdea("2025.3")
        // JS/TS plugin id used for optional enablement; compile against it when available.
        bundledPlugin("JavaScript")
        testFramework(TestFrameworkType.Platform)
    }
}

