package dev.mcp.extensions.lsp.core.interfaces

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import dev.mcp.extensions.lsp.core.models.FindReferencesArgs
import dev.mcp.extensions.lsp.core.models.ReferenceInfo
import dev.mcp.extensions.lsp.core.models.GroupedReferencesResult

/**
 * Interface for finding symbol references/usages.
 * Implementations should handle language-specific reference searching.
 */
interface ReferenceFinder {
    /**
     * Find all references to the given element.
     * 
     * @param project The project to search in
     * @param element The element to find references for
     * @param args Arguments controlling search behavior
     * @return List of found references
     */
    fun findReferences(project: Project, element: PsiElement, args: FindReferencesArgs): List<ReferenceInfo>
    
    /**
     * Find target element based on file path and position or symbol name.
     * 
     * @param project The project to search in
     * @param args Arguments specifying what to search for
     * @return The target element if found, null otherwise
     */
    fun findTargetElement(project: Project, args: FindReferencesArgs): PsiElement?
    
    /**
     * Find target element by position in file.
     * 
     * @param psiFile The file to search in
     * @param position The position in the file
     * @return The target element if found, null otherwise
     */
    fun findTargetElementByPosition(psiFile: com.intellij.psi.PsiFile, position: Int): PsiElement? {
        val element = psiFile.findElementAt(position) ?: return null
        // Try to resolve reference first
        val reference = element.parent?.reference ?: element.reference
        return reference?.resolve() ?: element
    }
    
    /**
     * Create grouped result with insights from references.
     * 
     * @param references List of found references
     * @param element The element that was searched for
     * @return Grouped result with usage statistics and insights
     */
    fun createGroupedResult(references: List<ReferenceInfo>, element: PsiElement): GroupedReferencesResult
    
    /**
     * Create reference info from a PSI element.
     * 
     * @param element The element containing the reference
     * @param target The target element being referenced
     * @param overrideType Optional override for usage type
     * @return ReferenceInfo for the element
     */
    fun createReferenceInfo(element: PsiElement, target: PsiElement, overrideType: String? = null): ReferenceInfo
    
    /**
     * Check if this finder supports the given element type.
     * 
     * @param element The PSI element to check
     * @return true if this finder can handle the element
     */
    fun supportsElement(element: PsiElement): Boolean
    
    /**
     * Check if this finder supports the given file type.
     * 
     * @param psiFile The PSI file to check
     * @return true if this finder can handle the file
     */
    fun supportsFile(psiFile: com.intellij.psi.PsiFile): Boolean {
        return supportsElement(psiFile)
    }
    
    /**
     * Get the supported language name for logging/debugging.
     * 
     * @return Language name or description
     */
    fun getSupportedLanguage(): String
}
