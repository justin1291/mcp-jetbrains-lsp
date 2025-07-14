package dev.mcp.extensions.lsp.core.factories

import com.intellij.lang.Language
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import dev.mcp.extensions.lsp.core.interfaces.DefinitionFinder
import dev.mcp.extensions.lsp.languages.java.JavaDefinitionFinder

/**
 * Factory for creating language-specific definition finders.
 * Uses IntelliJ's service mechanism to load only available implementations.
 */
object DefinitionFinderFactory {
    private val logger = Logger.getInstance(DefinitionFinderFactory::class.java)
    
    /**
     * Get the appropriate definition finder for a given file.
     * 
     * @param psiFile The PSI file to get a finder for
     * @return DefinitionFinder implementation for the file's language
     * @throws UnsupportedOperationException if the language is not supported
     */
    fun getFinder(psiFile: PsiFile): DefinitionFinder {
        val language = psiFile.language
        return getFinderForLanguage(language)
    }
    
    /**
     * Get the appropriate definition finder for a given element.
     * 
     * @param element The PSI element to get a finder for
     * @return DefinitionFinder implementation for the element's language
     * @throws UnsupportedOperationException if the language is not supported
     */
    fun getFinder(element: PsiElement): DefinitionFinder {
        val language = element.language
        return getFinderForLanguage(language)
    }
    
    private fun getFinderForLanguage(language: Language): DefinitionFinder {
        val languageId = language.id
        val languageName = language.displayName
        
        logger.info("Determining definition finder for language: $languageId ($languageName)")
        
        // Try to get language-specific service
        val finder = when {
            isJavaOrKotlin(language) -> {
                logger.debug("Looking for Java/Kotlin definition finder service")
                try {
                    service<JavaDefinitionFinder>()
                } catch (e: Exception) {
                    logger.debug("Java definition finder service not available: ${e.message}")
                    null
                }
            }
            isPython(language) -> {
                logger.debug("Looking for Python definition finder service")
                try {
                    service<dev.mcp.extensions.lsp.languages.python.PythonDefinitionFinder>()
                } catch (e: Exception) {
                    logger.debug("Python definition finder service not available: ${e.message}")
                    null
                }
            }
            else -> null
        }
        
        if (finder != null) {
            logger.info("Found definition finder for $languageName")
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
        
        logger.warn("No definition finder available: $errorMessage")
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
