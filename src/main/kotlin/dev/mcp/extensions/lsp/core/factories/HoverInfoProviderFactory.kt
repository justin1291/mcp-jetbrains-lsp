package dev.mcp.extensions.lsp.core.factories

import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import dev.mcp.extensions.lsp.core.interfaces.HoverInfoProvider
import dev.mcp.extensions.lsp.core.utils.DynamicServiceLoader
import dev.mcp.extensions.lsp.core.utils.LanguageUtils

/**
 * Factory for creating language-specific hover info providers.
 * Uses dynamic service loading to avoid compile-time dependencies on optional language support.
 */
object HoverInfoProviderFactory {
    private val logger = Logger.getInstance(HoverInfoProviderFactory::class.java)

    // Service class names - no direct imports needed
    private const val JVM_HOVER_INFO_PROVIDER = "dev.mcp.extensions.lsp.languages.jvm.JvmHoverInfoProvider"
    private const val PYTHON_HOVER_INFO_PROVIDER = "dev.mcp.extensions.lsp.languages.python.PythonHoverInfoProvider"
    private const val JAVASCRIPT_HOVER_INFO_PROVIDER =
        "dev.mcp.extensions.lsp.languages.javascript.JavaScriptHoverInfoProvider"

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

        // Try to get language-specific service using dynamic loading
        val provider = when {
            LanguageUtils.isJvmLanguage(language) -> {
                logger.info("Using JVM implementation for $languageId")
                DynamicServiceLoader.loadHoverInfoProvider(JVM_HOVER_INFO_PROVIDER)
            }

            LanguageUtils.isPython(language) -> {
                logger.debug("Looking for Python hover info provider service")
                DynamicServiceLoader.loadHoverInfoProvider(PYTHON_HOVER_INFO_PROVIDER)
            }

            LanguageUtils.isJavaScriptOrTypeScript(language) -> {
                logger.debug("Looking for JavaScript/TypeScript hover info provider service")
                DynamicServiceLoader.loadHoverInfoProvider(JAVASCRIPT_HOVER_INFO_PROVIDER)
            }

            else -> null
        }

        if (provider != null) {
            logger.info("Found hover info provider for $languageName")
            return provider
        }

        // Use centralized error message generation
        val errorMessage = LanguageUtils.getUnsupportedLanguageMessage(language)

        logger.warn("No hover info provider available: $errorMessage")
        throw UnsupportedOperationException(errorMessage)
    }
}
