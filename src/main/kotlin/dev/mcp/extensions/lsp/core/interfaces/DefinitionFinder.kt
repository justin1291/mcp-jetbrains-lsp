package dev.mcp.extensions.lsp.core.interfaces

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import dev.mcp.extensions.lsp.core.models.DefinitionLocation

/**
 * Interface for finding symbol definitions.
 * Implementations should handle language-specific definition resolution.
 */
interface DefinitionFinder {
    /**
     * Find definition by position in a file.
     * 
     * @param psiFile The PSI file to search in
     * @param position Character offset in the file
     * @return List of possible definition locations
     */
    fun findDefinitionByPosition(psiFile: PsiFile, position: Int): List<DefinitionLocation>
    
    /**
     * Find definition by symbol name across the project.
     * 
     * @param project The project to search in
     * @param symbolName Name of the symbol to find
     * @return List of possible definition locations
     */
    fun findDefinitionByName(project: Project, symbolName: String): List<DefinitionLocation>
    
    /**
     * Create a DefinitionLocation from a PSI element.
     * 
     * @param element The PSI element to convert
     * @param searchTerm Optional search term for confidence calculation
     * @return DefinitionLocation representing the element
     */
    fun createLocation(element: PsiElement, searchTerm: String? = null): DefinitionLocation
    
    /**
     * Check if this finder supports the given file.
     * 
     * @param psiFile The PSI file to check
     * @return true if this finder can handle the file
     */
    fun supportsFile(psiFile: PsiFile): Boolean
    
    /**
     * Check if this finder supports the given element type.
     * 
     * @param element The PSI element to check
     * @return true if this finder can handle the element
     */
    fun supportsElement(element: PsiElement): Boolean
    
    /**
     * Get the supported language name for logging/debugging.
     * 
     * @return Language name or description
     */
    fun getSupportedLanguage(): String
}
