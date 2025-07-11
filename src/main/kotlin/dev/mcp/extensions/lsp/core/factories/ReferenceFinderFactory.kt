package dev.mcp.extensions.lsp.core.factories

import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import dev.mcp.extensions.lsp.core.interfaces.ReferenceFinder
import dev.mcp.extensions.lsp.languages.java.JavaReferenceFinder

/**
 * Factory for creating language-specific reference finders.
 */
object ReferenceFinderFactory {
    private val logger = Logger.getInstance(ReferenceFinderFactory::class.java)
    
    // Cache finders to avoid creating new instances repeatedly
    private val finderCache = mutableMapOf<String, ReferenceFinder>()
    
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
        
        return when {
            SymbolExtractorFactory.isLanguageSupported(language) -> {
                when {
                    isJavaOrKotlin(language) -> {
                        logger.debug("Using Java/Kotlin reference finder")
                        finderCache.getOrPut("java") { JavaReferenceFinder() }
                    }
                    else -> {
                        throw UnsupportedOperationException("Reference finder not implemented for: $languageName")
                    }
                }
            }
            else -> {
                logger.warn("Unsupported language: $languageId")
                throw UnsupportedOperationException("Language not supported: $languageName (id: $languageId)")
            }
        }
    }
    
    private fun isJavaOrKotlin(language: Language): Boolean {
        val id = language.id
        return id == "JAVA" || id == "kotlin" || id == "Kotlin"
    }
}
