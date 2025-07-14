package dev.mcp.extensions.lsp.core.factories

import com.intellij.lang.Language
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiFile
import dev.mcp.extensions.lsp.core.interfaces.SymbolExtractor
import dev.mcp.extensions.lsp.languages.java.JavaSymbolExtractor
import dev.mcp.extensions.lsp.languages.python.PythonSymbolExtractor

/**
 * Factory for creating language-specific symbol extractors.
 * Uses IntelliJ's service mechanism to load only available implementations.
 */
object SymbolExtractorFactory {
    private val logger = Logger.getInstance(SymbolExtractorFactory::class.java)
    
    /**
     * Get the appropriate symbol extractor for a given file.
     * 
     * @param psiFile The PSI file to get an extractor for
     * @return SymbolExtractor implementation for the file's language
     * @throws UnsupportedOperationException if the language is not supported
     */
    fun getExtractor(psiFile: PsiFile): SymbolExtractor {
        val language = psiFile.language
        val languageId = language.id
        val languageName = language.displayName
        
        logger.info("Determining symbol extractor for language: $languageId ($languageName)")
        logger.debug("File: ${psiFile.virtualFile?.path}")
        
        // Try to get language-specific service
        val extractor = when {
            isJavaOrKotlin(language) -> {
                logger.debug("Looking for Java/Kotlin symbol extractor service")
                try {
                    service<JavaSymbolExtractor>()
                } catch (e: Exception) {
                    logger.debug("Java symbol extractor service not available: ${e.message}")
                    null
                }
            }
            isPython(language) -> {
                logger.debug("Looking for Python symbol extractor service")
                try {
                    service<PythonSymbolExtractor>()
                } catch (e: Exception) {
                    logger.debug("Python symbol extractor service not available: ${e.message}")
                    null
                }
            }
            else -> null
        }
        
        if (extractor != null) {
            logger.info("Found symbol extractor for $languageName")
            return extractor
        }
        
        // Provide helpful error message based on language
        val errorMessage = when {
            isPython(language) -> {
                "Python support is not available in this IDE. " +
                "Python is supported in PyCharm or IntelliJ IDEA Ultimate with the Python plugin installed."
            }
            isJavaOrKotlin(language) -> {
                "Java/Kotlin support should be available but the service failed to load. " +
                "Please restart the IDE or reinstall the plugin."
            }
            isJavaScriptOrTypeScript(language) -> {
                "JavaScript/TypeScript support is not yet implemented."
            }
            else -> {
                "Language not supported: $languageName (id: $languageId)"
            }
        }
        
        logger.warn("No symbol extractor available: $errorMessage")
        throw UnsupportedOperationException(errorMessage)
    }
    
    /**
     * Check if a language is Java or Kotlin.
     */
    private fun isJavaOrKotlin(language: Language): Boolean {
        val id = language.id
        return id == "JAVA" || id == "kotlin" || id == "Kotlin"
    }
    
    /**
     * Check if a language is Python.
     */
    private fun isPython(language: Language): Boolean {
        val id = language.id
        return id == "Python" || id == "PythonCore"
    }
    
    /**
     * Check if a language is JavaScript or TypeScript.
     */
    private fun isJavaScriptOrTypeScript(language: Language): Boolean {
        val id = language.id
        return id == "JavaScript" || id == "TypeScript" || 
               id == "JSX" || id == "TSX" ||
               id == "ECMAScript 6"
    }
    
    /**
     * Get list of supported languages based on available services.
     */
    fun getSupportedLanguages(): List<String> {
        val languages = mutableListOf<String>()
        
        // Check if Java service is available
        try {
            service<JavaSymbolExtractor>()
            languages.add("Java")
            languages.add("Kotlin")
        } catch (e: Exception) {
            // Java not available
        }
        
        // Check if Python service is available
        try {
            service<PythonSymbolExtractor>()
            languages.add("Python")
        } catch (e: Exception) {
            // Python not available
        }
        
        return languages
    }
    
    /**
     * Check if a language is supported.
     */
    fun isLanguageSupported(language: Language): Boolean {
        return when {
            isJavaOrKotlin(language) -> {
                try {
                    service<JavaSymbolExtractor>()
                    true
                } catch (e: Exception) {
                    false
                }
            }
            isPython(language) -> {
                try {
                    service<PythonSymbolExtractor>()
                    true
                } catch (e: Exception) {
                    false
                }
            }
            else -> false
        }
    }
}
