package dev.mcp.extensions.lsp.core.factories

import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import dev.mcp.extensions.lsp.core.interfaces.DefinitionFinder
import dev.mcp.extensions.lsp.languages.java.JavaDefinitionFinder

/**
 * Factory for creating language-specific definition finders.
 */
object DefinitionFinderFactory {
    private val logger = Logger.getInstance(DefinitionFinderFactory::class.java)
    
    // Cache finders to avoid creating new instances repeatedly
    private val finderCache = mutableMapOf<String, DefinitionFinder>()
    
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
        
        return when {
            SymbolExtractorFactory.isLanguageSupported(language) -> {
                when {
                    isJavaOrKotlin(language) -> {
                        logger.debug("Using Java/Kotlin definition finder")
                        finderCache.getOrPut("java") { JavaDefinitionFinder() }
                    }
                    else -> {
                        throw UnsupportedOperationException("Finder not implemented for: $languageName")
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
