package dev.mcp.extensions.lsp.core.factories

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import dev.mcp.extensions.lsp.core.interfaces.ReferenceFinder
import dev.mcp.extensions.lsp.core.utils.DynamicServiceLoader
import dev.mcp.extensions.lsp.core.utils.LanguageUtils

/**
 * Factory for creating language-specific reference finders.
 * Uses dynamic service loading to avoid compile-time dependencies on optional language support.
 */
object ReferenceFinderFactory {
    private val logger = Logger.getInstance(ReferenceFinderFactory::class.java)

    // **UPDATED**: Use improved JVM reference finder
    private const val JVM_REFERENCE_FINDER = "dev.mcp.extensions.lsp.languages.jvm.JvmReferenceFinderImproved"
    private const val PYTHON_REFERENCE_FINDER = "dev.mcp.extensions.lsp.languages.python.PythonReferenceFinder"
    private const val JAVASCRIPT_REFERENCE_FINDER =
        "dev.mcp.extensions.lsp.languages.javascript.JavaScriptReferenceFinder"

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

        // Try to get language-specific service using dynamic loading
        val finder = when {
            LanguageUtils.isJvmLanguage(language) -> {
                logger.info("Using JVM implementation for $languageId")
                DynamicServiceLoader.loadReferenceFinder(JVM_REFERENCE_FINDER)
            }

            LanguageUtils.isPython(language) -> {
                logger.debug("Looking for Python reference finder service")
                DynamicServiceLoader.loadReferenceFinder(PYTHON_REFERENCE_FINDER)
            }

            LanguageUtils.isJavaScriptOrTypeScript(language) -> {
                logger.debug("Looking for JavaScript/TypeScript reference finder service")
                DynamicServiceLoader.loadReferenceFinder(JAVASCRIPT_REFERENCE_FINDER)
            }

            else -> null
        }

        if (finder != null) {
            logger.info("Found reference finder for $languageName")
            return finder
        }

        // Use centralized error message generation
        val errorMessage = LanguageUtils.getUnsupportedLanguageMessage(language)

        logger.warn("No reference finder available: $errorMessage")
        throw UnsupportedOperationException(errorMessage)
    }
}
