package dev.mcp.extensions.lsp.core.factories

import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import dev.mcp.extensions.lsp.core.interfaces.HoverInfoProvider
import dev.mcp.extensions.lsp.core.utils.DynamicServiceLoader

/**
 * Factory for creating language-specific hover info providers.
 * Uses dynamic service loading to avoid compile-time dependencies on optional language support.
 */
object HoverInfoProviderFactory {
    private val logger = Logger.getInstance(HoverInfoProviderFactory::class.java)

    // Service class names - no direct imports needed
    private const val JAVA_HOVER_INFO_PROVIDER = "dev.mcp.extensions.lsp.languages.java.JavaHoverInfoProvider"
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
            isJavaOrKotlin(language) -> {
                logger.debug("Looking for Java/Kotlin hover info provider service")
                DynamicServiceLoader.loadHoverInfoProvider(JAVA_HOVER_INFO_PROVIDER)
            }

            isPython(language) -> {
                logger.debug("Looking for Python hover info provider service")
                DynamicServiceLoader.loadHoverInfoProvider(PYTHON_HOVER_INFO_PROVIDER)
            }

            isJavaScriptOrTypeScript(language) -> {
                logger.debug("Looking for JavaScript/TypeScript hover info provider service")
                DynamicServiceLoader.loadHoverInfoProvider(JAVASCRIPT_HOVER_INFO_PROVIDER)
            }

            else -> null
        }

        if (provider != null) {
            logger.info("Found hover info provider for $languageName")
            return provider
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
                "JavaScript/TypeScript support is not available in this IDE. " +
                        "JavaScript/TypeScript is supported in WebStorm or IntelliJ IDEA Ultimate with the JavaScript plugin installed."
            }

            else -> {
                "Language not supported: $languageName (id: $languageId)"
            }
        }

        logger.warn("No hover info provider available: $errorMessage")
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

    private fun isJavaScriptOrTypeScript(language: Language): Boolean {
        val id = language.id
        return id == "JavaScript" || id == "TypeScript" ||
                id == "JSX" || id == "TSX" ||
                id == "ECMAScript 6"
    }
}
