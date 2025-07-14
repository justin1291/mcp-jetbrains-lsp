package dev.mcp.extensions.lsp.core.factories

import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiFile
import dev.mcp.extensions.lsp.core.interfaces.SymbolExtractor
import dev.mcp.extensions.lsp.core.utils.DynamicServiceLoader

/**
 * Factory for creating language-specific symbol extractors.
 * Uses dynamic service loading to avoid compile-time dependencies on optional language support.
 */
object SymbolExtractorFactory {
    private val logger = Logger.getInstance(SymbolExtractorFactory::class.java)

    // Service class names - no direct imports needed
    private const val JAVA_SYMBOL_EXTRACTOR = "dev.mcp.extensions.lsp.languages.java.JavaSymbolExtractor"
    private const val PYTHON_SYMBOL_EXTRACTOR = "dev.mcp.extensions.lsp.languages.python.PythonSymbolExtractor"

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

        // Try to get language-specific service using dynamic loading
        val extractor = when {
            isJavaOrKotlin(language) -> {
                logger.debug("Looking for Java/Kotlin symbol extractor service")
                DynamicServiceLoader.loadSymbolExtractor(JAVA_SYMBOL_EXTRACTOR)
            }

            isPython(language) -> {
                logger.debug("Looking for Python symbol extractor service")
                DynamicServiceLoader.loadSymbolExtractor(PYTHON_SYMBOL_EXTRACTOR)
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
        // without the python plugin "Text" will be the detected language
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
        if (DynamicServiceLoader.isSymbolExtractorAvailable(JAVA_SYMBOL_EXTRACTOR)) {
            languages.add("Java")
            languages.add("Kotlin")
        }

        // Check if Python service is available
        if (DynamicServiceLoader.isSymbolExtractorAvailable(PYTHON_SYMBOL_EXTRACTOR)) {
            languages.add("Python")
        }

        return languages
    }
}
