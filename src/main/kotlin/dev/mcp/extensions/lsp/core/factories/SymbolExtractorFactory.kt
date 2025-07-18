package dev.mcp.extensions.lsp.core.factories

import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiFile
import dev.mcp.extensions.lsp.core.interfaces.SymbolExtractor
import dev.mcp.extensions.lsp.core.utils.DynamicServiceLoader
import dev.mcp.extensions.lsp.core.utils.LanguageUtils
import java.util.concurrent.ConcurrentHashMap

/**
 * Factory for creating language-specific symbol extractors.
 * Uses dynamic service loading to avoid compile-time dependencies on optional language support.
 */
object SymbolExtractorFactory {
    private val logger = Logger.getInstance(SymbolExtractorFactory::class.java)

    // Service class names - no direct imports needed
    private const val JVM_SYMBOL_EXTRACTOR = "dev.mcp.extensions.lsp.languages.jvm.JvmSymbolExtractor"
    private const val PYTHON_SYMBOL_EXTRACTOR = "dev.mcp.extensions.lsp.languages.python.PythonSymbolExtractor"
    private const val JAVASCRIPT_SYMBOL_EXTRACTOR =
        "dev.mcp.extensions.lsp.languages.javascript.JavaScriptSymbolExtractor"

    // Thread-safe cache for symbol types
    private val frameworkSymbolCache = ConcurrentHashMap<String, Set<String>>()
    private var allSymbolsCache: Set<String>? = null

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
            LanguageUtils.isJvmLanguage(language) -> {
                logger.info("Using JVM implementation for $languageId")
                DynamicServiceLoader.loadSymbolExtractor(JVM_SYMBOL_EXTRACTOR)
            }

            LanguageUtils.isPython(language) -> {
                logger.debug("Looking for Python symbol extractor service")
                DynamicServiceLoader.loadSymbolExtractor(PYTHON_SYMBOL_EXTRACTOR)
            }

            LanguageUtils.isJavaScriptOrTypeScript(language) -> {
                logger.debug("Looking for JavaScript/TypeScript symbol extractor service")
                DynamicServiceLoader.loadSymbolExtractor(JAVASCRIPT_SYMBOL_EXTRACTOR)
            }

            else -> {
                logger.debug("No language match found for '$languageId'")
                null
            }
        }

        if (extractor != null) {
            logger.info("Found symbol extractor for $languageName: ${extractor.javaClass.name}")
            // Additional check if the extractor supports the specific file
            if (extractor.supportsFile(psiFile)) {
                logger.debug("Extractor confirms support for file")
                return extractor
            } else {
                logger.warn("Extractor does not support this specific file")
            }
        }

        // Use centralized error message generation
        val errorMessage = LanguageUtils.getUnsupportedLanguageMessage(language)

        logger.warn("No symbol extractor available: $errorMessage")
        throw UnsupportedOperationException(errorMessage)
    }

    /**
     * Get all supported symbol types from available extractors.
     * This aggregates symbol types from all language implementations.
     *
     * @return Set of unique symbol type names
     */
    fun getSupportedSymbolTypes(): Set<String> {
        // Return cached result if available
        allSymbolsCache?.let { return it }

        val symbolTypes = mutableSetOf<String>()

        // Core LSP types that are always available
        val coreLspTypes = setOf(
            "string", "number",
            "boolean", "array", "object", "key", "null"
        )
        symbolTypes.addAll(coreLspTypes)

        // Collect types from each available language implementation with all framework types included
        listOf(JVM_SYMBOL_EXTRACTOR, JAVASCRIPT_SYMBOL_EXTRACTOR, PYTHON_SYMBOL_EXTRACTOR).forEach { className ->
            try {
                val extractor = DynamicServiceLoader.loadSymbolExtractor(className)
                if (extractor != null) {
                    // For description, include all framework types
                    val includeFrameworkTypes = true
                    symbolTypes.addAll(extractor.getSupportedSymbolTypes(includeFrameworkTypes))

                    logger.debug(
                        "Added symbol types from ${extractor.getSupportedLanguage()}: " +
                                extractor.getSupportedSymbolTypes(includeFrameworkTypes)
                    )
                }
            } catch (e: Exception) {
                logger.debug("Could not load symbol types from $className: ${e.message}")
            }
        }

        // Cache the result for future calls
        allSymbolsCache = symbolTypes

        return symbolTypes
    }

    /**
     * Get supported symbol types based on frameworks in use.
     * Uses a cache for better performance.
     *
     * @param frameworks Set of frameworks detected in the project (e.g., "react", "vue")
     * @return Set of symbol types relevant for the project
     */
    fun getSupportedSymbolTypes(frameworks: Set<String>): Set<String> {
        // Fast path: if no frameworks specified, use all symbols
        if (frameworks.isEmpty()) {
            return getSupportedSymbolTypes()
        }

        // Use framework-specific key for cache
        val frameworkKey = frameworks.sorted().joinToString(",")

        // Check cache first - ConcurrentHashMap handles thread safety
        return frameworkSymbolCache.computeIfAbsent(frameworkKey) { _ ->
            val symbolTypes = mutableSetOf<String>()

            // Core LSP types that are always available
            val coreLspTypes = setOf(
                "string", "number",
                "boolean", "array", "object", "key", "null"
            )
            symbolTypes.addAll(coreLspTypes)

            // Collect types from each available language implementation
            listOf(JVM_SYMBOL_EXTRACTOR, JAVASCRIPT_SYMBOL_EXTRACTOR, PYTHON_SYMBOL_EXTRACTOR).forEach { className ->
                try {
                    val extractor = DynamicServiceLoader.loadSymbolExtractor(className)
                    if (extractor != null) {
                        // We'll determine if we need framework types based on presence of those frameworks
                        val includeFrameworkTypes = frameworks.isNotEmpty()
                        symbolTypes.addAll(extractor.getSupportedSymbolTypes(includeFrameworkTypes))
                    }
                } catch (e: Exception) {
                    logger.debug("Could not load symbol types from $className: ${e.message}")
                }
            }

            symbolTypes
        }
    }

    /**
     * Clear the symbol type cache. Use this when plugin settings change or when
     * the plugin is reloaded.
     */
    fun clearCache() {
        frameworkSymbolCache.clear()
        allSymbolsCache = null
        logger.debug("Symbol type cache cleared")
    }
    
    /**
     * Get all supported languages from available extractors.
     */
    fun getSupportedLanguages(): List<String> {
        val languages = mutableListOf<String>()
        
        // Try to load each extractor and get its supported language
        listOf(JVM_SYMBOL_EXTRACTOR, PYTHON_SYMBOL_EXTRACTOR, JAVASCRIPT_SYMBOL_EXTRACTOR).forEach { className ->
            try {
                val extractor = DynamicServiceLoader.loadSymbolExtractor(className)
                if (extractor != null) {
                    languages.add(extractor.getSupportedLanguage())
                }
            } catch (e: Exception) {
                logger.debug("Could not load extractor $className: ${e.message}")
            }
        }
        
        return languages.distinct()
    }
}
