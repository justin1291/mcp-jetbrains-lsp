plugins {
    id("java")
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.6.0"
    kotlin("plugin.serialization") version "2.1.0"
    kotlin("plugin.power-assert") version "2.1.0"
}

group = "dev.mcp.extensions"
version = "1.0.3"

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
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.JUnit5)
        plugin("com.intellij.mcpServer", "1.0.30")
        
        // Language support
        bundledPlugin("com.intellij.java")
        plugin("PythonCore:251.23774.460")
    }


    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
            untilBuild = "251.*"  // Exclude 2025.2 EAP (252.*) and later
        }

        changeNotes = """
            v1.0.3:
            - Improve plugin compatibility
            
            v1.0.0:
            - Initial release
            - Java/Kotlin support
            - Alpha Python support 
        """.trimIndent()
    }

    pluginVerification {
        ides {
            ide("IC-2024.3")
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
