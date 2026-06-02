import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
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
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
    }
}

