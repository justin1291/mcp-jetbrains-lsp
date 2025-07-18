package dev.mcp.extensions.lsp.core.utils

import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.Logger

/**
 * Centralized language detection and classification utilities.
 * Consolidates all language identification logic to avoid duplication across factories.
 */
object LanguageUtils {
    private val logger = Logger.getInstance(LanguageUtils::class.java)

    /**
     * Check if a language is a JVM language (Java, Kotlin, Scala, Groovy).
     * Handles various language ID formats that IntelliJ uses.
     */
    fun isJvmLanguage(language: Language): Boolean {
        return isJvmLanguage(language.id)
    }

    /**
     * Check if a language ID represents a JVM language.
     * Enhanced to handle various language ID formats that IntelliJ uses.
     */
    fun isJvmLanguage(languageId: String): Boolean {
        val normalizedId = languageId.lowercase()
        
        return normalizedId in setOf(
            "java",                   // Standard Java
            "kotlin",                 // Standard Kotlin  
            "scala",                  // Standard Scala
            "groovy"                  // Standard Groovy
        ) || languageId in setOf(
            "JAVA",                   // Uppercase Java variant
            "Kotlin",                 // Capitalized Kotlin
            "Scala",                  // Capitalized Scala  
            "Groovy"                  // Capitalized Groovy
        ) ||
        // Additional checks for common variations and substrings
        normalizedId.contains("java") ||
        normalizedId.contains("kotlin") ||
        normalizedId.contains("scala") ||
        normalizedId.contains("groovy")
    }

    /**
     * Check if a language is specifically Java or Kotlin (not Scala/Groovy).
     */
    fun isJavaOrKotlin(language: Language): Boolean {
        return isJavaOrKotlin(language.id)
    }

    /**
     * Check if a language ID represents Java or Kotlin specifically.
     */
    fun isJavaOrKotlin(languageId: String): Boolean {
        val normalizedId = languageId.lowercase()
        return normalizedId in setOf("java", "kotlin") ||
               languageId in setOf("JAVA", "Kotlin") ||
               normalizedId.contains("java") ||
               normalizedId.contains("kotlin")
    }

    /**
     * Check if a language is Python.
     */
    fun isPython(language: Language): Boolean {
        return isPython(language.id)
    }

    /**
     * Check if a language ID represents Python.
     */
    fun isPython(languageId: String): Boolean {
        val normalizedId = languageId.lowercase()
        return normalizedId in setOf("python", "pythoncore") ||
               languageId in setOf("Python", "PythonCore") ||
               normalizedId.contains("python")
    }

    /**
     * Check if a language is JavaScript or TypeScript.
     */
    fun isJavaScriptOrTypeScript(language: Language): Boolean {
        return isJavaScriptOrTypeScript(language.id)
    }

    /**
     * Check if a language ID represents JavaScript, TypeScript, or related variants.
     */
    fun isJavaScriptOrTypeScript(languageId: String): Boolean {
        val normalizedId = languageId.lowercase()
        
        return normalizedId in setOf(
            "javascript", "js",
            "typescript", "ts", 
            "jsx", "tsx",
            "ecmascript",
            "node.js", "nodejs",
            "ecmascript 6"
        ) || languageId in setOf(
            "JavaScript", "TypeScript", "JSX", "TSX",
            "TypeScript JSX",     // Support for .tsx files
            "JavaScript JSX",     // Support for .jsx files  
            "ECMAScript 6"
        ) ||
        normalizedId.contains("javascript") ||
        normalizedId.contains("typescript") ||
        normalizedId.contains("ecmascript")
    }

    /**
     * Get a user-friendly name for a language.
     */
    fun getLanguageDisplayName(language: Language): String {
        return when {
            isJvmLanguage(language) -> {
                when (language.id.lowercase()) {
                    "java" -> "Java"
                    "kotlin" -> "Kotlin"
                    "scala" -> "Scala"
                    "groovy" -> "Groovy"
                    else -> "JVM Language (${language.displayName})"
                }
            }
            isPython(language) -> "Python"
            isJavaScriptOrTypeScript(language) -> {
                when (language.id.lowercase()) {
                    "javascript", "js" -> "JavaScript"
                    "typescript", "ts" -> "TypeScript"
                    "jsx" -> "JavaScript (JSX)"
                    "tsx" -> "TypeScript (TSX)"
                    else -> "JavaScript/TypeScript (${language.displayName})"
                }
            }
            else -> language.displayName
        }
    }

    /**
     * Get appropriate error message for unsupported language.
     */
    fun getUnsupportedLanguageMessage(language: Language): String {
        return when {
            isPython(language) -> {
                "Python support is not available in this IDE. " +
                        "Python is supported in PyCharm or IntelliJ IDEA Ultimate with the Python plugin installed."
            }

            isJvmLanguage(language) -> {
                "JVM language support should be available but the service failed to load. " +
                        "Please restart the IDE or reinstall the plugin."
            }

            isJavaScriptOrTypeScript(language) -> {
                "JavaScript/TypeScript support is not available in this IDE. " +
                        "JavaScript/TypeScript is supported in WebStorm or IntelliJ IDEA Ultimate with the JavaScript plugin installed."
            }

            else -> {
                "Language not supported: ${language.displayName} (id: ${language.id})"
            }
        }
    }

    /**
     * Get all supported language categories.
     */
    fun getSupportedLanguageCategories(): List<String> {
        return listOf("JVM", "Python", "JavaScript/TypeScript")
    }

    /**
     * Check if any language is supported.
     */
    fun isLanguageSupported(language: Language): Boolean {
        return isJvmLanguage(language) || 
               isPython(language) || 
               isJavaScriptOrTypeScript(language)
    }

    /**
     * Get the language category for a given language.
     */
    fun getLanguageCategory(language: Language): String? {
        return when {
            isJvmLanguage(language) -> "JVM"
            isPython(language) -> "Python"
            isJavaScriptOrTypeScript(language) -> "JavaScript/TypeScript"
            else -> null
        }
    }

    /**
     * Debug helper: Log language detection details.
     */
    fun debugLanguageDetection(language: Language) {
        val languageId = language.id
        val languageName = language.displayName
        
        logger.info("Language detection for: '$languageId' (display: '$languageName')")
        logger.info("  - Is JVM: ${isJvmLanguage(language)}")
        logger.info("  - Is Python: ${isPython(language)}")
        logger.info("  - Is JavaScript/TypeScript: ${isJavaScriptOrTypeScript(language)}")
        logger.info("  - Category: ${getLanguageCategory(language) ?: "Unsupported"}")
    }
}
