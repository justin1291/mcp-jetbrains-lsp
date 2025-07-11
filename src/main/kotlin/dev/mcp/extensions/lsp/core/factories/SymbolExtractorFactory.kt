package dev.mcp.extensions.lsp.core.factories

import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiFile
import dev.mcp.extensions.lsp.core.interfaces.SymbolExtractor
import dev.mcp.extensions.lsp.languages.java.JavaSymbolExtractor

/**
 * Factory for creating language-specific symbol extractors.
 */
object SymbolExtractorFactory {
    private val logger = Logger.getInstance(SymbolExtractorFactory::class.java)
    private val extractorCache = mutableMapOf<String, SymbolExtractor>()
    
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
        
        return when {
            isJavaOrKotlin(language) -> {
                logger.debug("Using Java/Kotlin symbol extractor")
                extractorCache.getOrPut("java") { JavaSymbolExtractor() }
            }
            isPython(language) -> {
                logger.debug("Python language detected")
                throw UnsupportedOperationException("Python support not yet implemented")
            }
            isJavaScriptOrTypeScript(language) -> {
                logger.debug("JavaScript/TypeScript language detected")
                throw UnsupportedOperationException("JavaScript/TypeScript support not yet implemented")
            }
            else -> {
                logger.warn("Unsupported language: $languageId")
                throw UnsupportedOperationException("Language not supported: $languageName (id: $languageId)")
            }
        }
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
     * Get list of supported languages.
     */
    fun getSupportedLanguages(): List<String> {
        return listOf(
            "Java",
            "Kotlin"
        )
    }
    
    /**
     * Check if a language is supported.
     */
    fun isLanguageSupported(language: Language): Boolean {
        return isJavaOrKotlin(language)
    }
}
