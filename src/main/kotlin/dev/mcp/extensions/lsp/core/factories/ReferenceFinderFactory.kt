package dev.mcp.extensions.lsp.core.factories

import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import dev.mcp.extensions.lsp.core.interfaces.ReferenceFinder
import dev.mcp.extensions.lsp.core.utils.DynamicServiceLoader

/**
 * Factory for creating language-specific reference finders.
 * Uses dynamic service loading to avoid compile-time dependencies on optional language support.
 */
object ReferenceFinderFactory {
    private val logger = Logger.getInstance(ReferenceFinderFactory::class.java)

    private const val JAVA_REFERENCE_FINDER = "dev.mcp.extensions.lsp.languages.java.JavaReferenceFinder"
    private const val PYTHON_REFERENCE_FINDER = "dev.mcp.extensions.lsp.languages.python.PythonReferenceFinder"

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
            isJavaOrKotlin(language) -> {
                logger.debug("Looking for Java/Kotlin reference finder service")
                DynamicServiceLoader.loadReferenceFinder(JAVA_REFERENCE_FINDER)
            }

            isPython(language) -> {
                logger.debug("Looking for Python reference finder service")
                DynamicServiceLoader.loadReferenceFinder(PYTHON_REFERENCE_FINDER)
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
