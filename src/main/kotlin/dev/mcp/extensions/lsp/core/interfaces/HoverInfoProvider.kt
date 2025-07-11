package dev.mcp.extensions.lsp.core.interfaces

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import dev.mcp.extensions.lsp.core.models.HoverInfo

/**
 * Interface for providing hover information about symbols.
 * Implementations should handle language-specific type information and documentation.
 */
interface HoverInfoProvider {
    /**
     * Get hover information for an element.
     * 
     * @param element The element to get hover info for
     * @return HoverInfo containing type, documentation, and other metadata
     */
    fun getHoverInfo(element: PsiElement): HoverInfo
    
    /**
     * Find element at position and get hover info.
     * 
     * @param psiFile The file to search in
     * @param position Character offset in the file
     * @return HoverInfo for element at position, or null if not found
     */
    fun getHoverInfoAtPosition(psiFile: PsiFile, position: Int): HoverInfo?
    
    /**
     * Check if this provider supports the given element type.
     * 
     * @param element The PSI element to check
     * @return true if this provider can handle the element
     */
    fun supportsElement(element: PsiElement): Boolean
    
    /**
     * Get the supported language name for logging/debugging.
     * 
     * @return Language name or description
     */
    fun getSupportedLanguage(): String
}
