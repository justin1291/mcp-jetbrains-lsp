plugins {
    id("java")
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.6.0"
    kotlin("plugin.serialization") version "2.1.0"
    kotlin("plugin.power-assert") version "2.1.0"
}

group = "dev.mcp.extensions"
version = "1.0.2"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Configure IntelliJ Platform Gradle Plugin
dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        
        // Core requirements
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.JUnit5)
        plugin("com.intellij.mcpServer", "1.0.30")
        
        // Language support
        bundledPlugin("com.intellij.java")

        // "Pythonid" for ultimate, "PythonCore" for community. Find compatible
        // ultimate versions at https://plugins.jetbrains.com/plugin/631
        // community versions at https://plugins.jetbrains.com/plugin/7322
        plugin("PythonCore:251.26927.53")
        
        // Also add Python plugin for tests
        testPlugin("PythonCore:251.26927.53")
    }

    // IMPORTANT: Use compileOnly for kotlinx-serialization to avoid class loader conflicts
    // The MCP Server plugin already provides this dependency at runtime
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    
    // For tests, we need the actual implementation
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Test dependencies
    testImplementation(kotlin("test"))
    // NOTE: JUnit4 is required due to IntelliJ Platform's JUnit5TestSessionListener implementation
    // This is a known issue where IntelliJ's JUnit5 support depends on junit.framework.TestCase
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
            untilBuild = "251.*"  // Target 2025.1 specifically
        }

        changeNotes = """
            v1.0.2:
            - Target 2025.1+
            
            v1.0.0:
            - Initial release
            - Java/Kotlin support
            - Alpha Python support 
        """.trimIndent()
    }

    pluginVerification {
        ides {
            // Verify against 2025.1 versions only
            ide("IC-2025.1")  // IntelliJ Community 2025.1
            ide("IU-2025.1")  // IntelliJ Ultimate 2025.1 (with optional Python plugin)
            ide("PC-2025.1")  // PyCharm Community 2025.1 (Python support)
            ide("PY-2025.1")  // PyCharm Professional 2025.1 (Python support)
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
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

// Configure Power Assert
powerAssert {
    functions = listOf(
        "kotlin.assert",
        "kotlin.test.assertTrue", 
        "kotlin.test.assertFalse",
        "kotlin.test.assertEquals",
        "kotlin.test.assertNotNull",
        "kotlin.test.assertNull"
    )
}
