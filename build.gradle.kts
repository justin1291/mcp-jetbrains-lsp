plugins {
    id("java")
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.6.0"
    kotlin("plugin.serialization") version "2.1.0"
}

group = "dev.mcp.extensions"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Configure IntelliJ Platform Gradle Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        create("IC", "2024.3")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.JUnit5)
        plugin("com.intellij.mcpServer", "1.0.30")
        bundledPlugin("com.intellij.java")
    }

    // Add Kotlin serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Test dependencies
    testImplementation(kotlin("test"))
    // NOTE: JUnit4 is required due to IntelliJ Platform's JUnit5TestSessionListener implementation
    // This is a known issue where IntelliJ's JUnit5 support depends on junit.framework.TestCase
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "243"
        }

        changeNotes = """
            Initial release of MCP Language Service Extension:
            - Extract symbols from files
            - Find symbol definitions
            - Find symbol references
            - Get hover information
        """.trimIndent()
    }

    pluginVerification {
        ides {
            ide("IC-2024.3")
            ide("IC-2025.1")
        }
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

test {
        // Use JUnit Platform (JUnit 5)
        useJUnitPlatform()

        // Increase memory for tests
        jvmArgs("-Xmx2048m")

        // Show test output
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
