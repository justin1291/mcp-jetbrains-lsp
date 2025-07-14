package dev.mcp.extensions.lsp.core.factories

import com.intellij.lang.Language
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import dev.mcp.extensions.lsp.core.interfaces.ReferenceFinder
import dev.mcp.extensions.lsp.languages.java.JavaReferenceFinder

/**
 * Factory for creating language-specific reference finders.
 * Uses IntelliJ's service mechanism to load only available implementations.
 */
object ReferenceFinderFactory {
    private val logger = Logger.getInstance(ReferenceFinderFactory::class.java)
    
    /**
     * Get the appropriate reference finder for a given element.
     * 
     * @param element The PSI element to get a finder for
     * @return ReferenceFinder implementation for the element's language
     * @throws UnsupportedOperationException if the language is not supported
     */
    fun getFinder(element: PsiElement): ReferenceFinder {
        val language = element.language
        val languageId = language.id
        val languageName = language.displayName
        
        logger.info("Determining reference finder for language: $languageId ($languageName)")
        logger.debug("Element type: ${element.javaClass.simpleName}")
        
        // Try to get language-specific service
        val finder = when {
            isJavaOrKotlin(language) -> {
                logger.debug("Looking for Java/Kotlin reference finder service")
                try {
                    service<JavaReferenceFinder>()
                } catch (e: Exception) {
                    logger.debug("Java reference finder service not available: ${e.message}")
                    null
                }
            }
            isPython(language) -> {
                logger.debug("Looking for Python reference finder service")
                try {
                    service<dev.mcp.extensions.lsp.languages.python.PythonReferenceFinder>()
                } catch (e: Exception) {
                    logger.debug("Python reference finder service not available: ${e.message}")
                    null
                }
            }
            else -> null
        }
        
        if (finder != null) {
            logger.info("Found reference finder for $languageName")
            return finder
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
            else -> {
                "Language not supported: $languageName (id: $languageId)"
            }
        }
        
        logger.warn("No reference finder available: $errorMessage")
        throw UnsupportedOperationException(errorMessage)
    }
    
    private fun isJavaOrKotlin(language: Language): Boolean {
        val id = language.id
        return id == "JAVA" || id == "kotlin" || id == "Kotlin"
    }
    
    private fun isPython(language: Language): Boolean {
        val id = language.id
        return id == "Python" || id == "PythonCore"
    }
}
