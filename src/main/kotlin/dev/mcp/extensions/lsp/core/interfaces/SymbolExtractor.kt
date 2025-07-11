package dev.mcp.extensions.lsp.core.interfaces

import com.intellij.psi.PsiFile
import dev.mcp.extensions.lsp.core.models.GetSymbolsArgs
import dev.mcp.extensions.lsp.core.models.SymbolInfo

/**
 * Interface for extracting symbols from PSI files.
 * Implementations should handle language-specific symbol extraction logic.
 */
interface SymbolExtractor {
    /**
     * Extract symbols from a PSI file in a flat list.
     * 
     * @param psiFile The PSI file to extract symbols from
     * @param args Arguments controlling extraction behavior
     * @return List of extracted symbols
     */
    fun extractSymbolsFlat(psiFile: PsiFile, args: GetSymbolsArgs): List<SymbolInfo>
    
    /**
     * Extract symbols from a PSI file in a hierarchical structure.
     * 
     * @param psiFile The PSI file to extract symbols from
     * @param args Arguments controlling extraction behavior
     * @return List of extracted symbols with nested children
     */
    fun extractSymbolsHierarchical(psiFile: PsiFile, args: GetSymbolsArgs): List<SymbolInfo>
    
    /**
     * Check if this extractor supports the given file.
     * 
     * @param psiFile The PSI file to check
     * @return true if this extractor can handle the file
     */
    fun supportsFile(psiFile: PsiFile): Boolean
    
    /**
     * Get the supported language name for logging/debugging.
     * 
     * @return Language name or description
     */
    fun getSupportedLanguage(): String
}
