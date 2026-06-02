import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

kotlin {
    // Root project is the plugin packaging module; code lives in :core.
    sourceSets {
        main {
            kotlin.setSrcDirs(emptyList<String>())
        }
        test {
            kotlin.setSrcDirs(listOf("src/test/kotlin"))
            resources.setSrcDirs(listOf("src/test/resources", "src/test/testData"))
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(project(":core"))
    implementation(project(":lang-java"))
    implementation(project(":lang-ts"))

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.3")  // 升级到更新版本
        bundledPlugin("com.intellij.java")
        bundledPlugin("JavaScript")
        testFramework(TestFrameworkType.Platform)
    }
}
