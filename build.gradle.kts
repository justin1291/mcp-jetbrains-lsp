@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import java.util.*

plugins {
    id("java")
    kotlin("jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.7.1"
    kotlin("plugin.serialization") version "2.2.0"
    kotlin("plugin.power-assert") version "2.1.0"
}

group = "dev.mcp.extensions"
version = "1.1.1"

// Load local properties if they exist
val localProperties = Properties()
val localPropertiesFile = file(".local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use {
        localProperties.load(it)
    }
    logger.lifecycle("Loaded local properties from .local.properties")
}

// Helper function to get property from multiple sources
fun getPropertyOrDefault(key: String, default: String? = null): String? {
    return System.getenv("GRADLE_${key.uppercase().replace(".", "_")}")
        ?: System.getProperty(key)
        ?: localProperties.getProperty(key)
        ?: project.findProperty(key) as String?
        ?: default
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        val localIdePath = getPropertyOrDefault("localIdePath")

        when {
            localIdePath != null && file(localIdePath).exists() -> {
                logger.lifecycle("Using local IDE: $localIdePath")
                bundledPlugin("JavaScript")
                local(localIdePath)
            }

            else -> {
                intellijIdeaUltimate("2025.1")
                bundledPlugin("JavaScript")
            }
        }

        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Plugin.Java)
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.JUnit5)
        plugin("com.intellij.mcpServer", "1.0.30")

        // Language support
        bundledPlugin("com.intellij.java")
        plugin("PythonCore:251.23774.460")
        testPlugin("PythonCore:251.23774.460")
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
            v1.1.0: Add javascript support
        """.trimIndent()
    }

    pluginVerification {
        ides {
            ide("IC-2025.1")
        }
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    test {
        useJUnitPlatform()
        jvmArgs("-Xmx2048m")
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
        "kotlin.test.assertNull",
        "com.intellij.testFramework.assertTrue",
        "com.intellij.testFramework.assertFalse",
        "com.intellij.testFramework.assertEquals",
        "com.intellij.testFramework.assertNotNull",
        "com.intellij.testFramework.assertNull",
        "com.intellij.testFramework.assertNotEmpty"
    )
}
