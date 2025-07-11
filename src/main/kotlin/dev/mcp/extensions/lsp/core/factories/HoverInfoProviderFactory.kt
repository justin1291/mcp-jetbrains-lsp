package dev.mcp.extensions.lsp.core.factories

import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import dev.mcp.extensions.lsp.core.interfaces.HoverInfoProvider
import dev.mcp.extensions.lsp.languages.java.JavaHoverInfoProvider

/**
 * Factory for creating language-specific hover info providers.
 */
object HoverInfoProviderFactory {
    private val logger = Logger.getInstance(HoverInfoProviderFactory::class.java)
    
    // Cache providers to avoid creating new instances repeatedly
    private val providerCache = mutableMapOf<String, HoverInfoProvider>()
    
    /**
     * Get the appropriate hover info provider for a given file.
     * 
     * @param psiFile The PSI file to get a provider for
     * @return HoverInfoProvider implementation for the file's language
     * @throws UnsupportedOperationException if the language is not supported
     */
    fun getProvider(psiFile: PsiFile): HoverInfoProvider {
        val language = psiFile.language
        return getProviderForLanguage(language)
    }
    
    /**
     * Get the appropriate hover info provider for a given element.
     * 
     * @param element The PSI element to get a provider for
     * @return HoverInfoProvider implementation for the element's language
     * @throws UnsupportedOperationException if the language is not supported
     */
    fun getProvider(element: PsiElement): HoverInfoProvider {
        val language = element.language
        return getProviderForLanguage(language)
    }
    
    private fun getProviderForLanguage(language: Language): HoverInfoProvider {
        val languageId = language.id
        val languageName = language.displayName
        
        logger.info("Determining hover info provider for language: $languageId ($languageName)")
        
        return when {
            SymbolExtractorFactory.isLanguageSupported(language) -> {
                when {
                    isJavaOrKotlin(language) -> {
                        logger.debug("Using Java/Kotlin hover info provider")
                        providerCache.getOrPut("java") { JavaHoverInfoProvider() }
                    }
                    else -> {
                        throw UnsupportedOperationException("Hover provider not implemented for: $languageName")
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
