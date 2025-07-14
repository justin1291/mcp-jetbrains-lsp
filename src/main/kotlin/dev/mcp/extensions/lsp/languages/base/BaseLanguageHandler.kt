package dev.mcp.extensions.lsp.languages.base

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*

/**
 * Base class containing common functionality for language handlers.
 */
abstract class BaseLanguageHandler {
    protected val logger: Logger = Logger.getInstance(this::class.java)
    
    /**
     * Extract modifiers from a modifier list.
     */
    protected fun extractModifiers(modifierList: PsiModifierList?): List<String> {
        if (modifierList == null) return emptyList()
        
        return listOf(
            PsiModifier.PUBLIC,
            PsiModifier.PRIVATE,
            PsiModifier.PROTECTED,
            PsiModifier.STATIC,
            PsiModifier.FINAL,
            PsiModifier.ABSTRACT,
            PsiModifier.SYNCHRONIZED,
            PsiModifier.VOLATILE,
            PsiModifier.TRANSIENT,
            PsiModifier.NATIVE,
            PsiModifier.STRICTFP
        ).filter { modifierList.hasModifierProperty(it) }
    }
    
    /**
     * Get visibility modifier as a string.
     */
    protected fun getVisibility(modifierList: PsiModifierList?): String {
        return when {
            modifierList?.hasModifierProperty(PsiModifier.PUBLIC) == true -> "public"
            modifierList?.hasModifierProperty(PsiModifier.PRIVATE) == true -> "private"
            modifierList?.hasModifierProperty(PsiModifier.PROTECTED) == true -> "protected"
            else -> "package-private"
        }
    }
    
    /**
     * Check if a virtual file is in test code.
     */
    protected fun isInTestCode(virtualFile: VirtualFile): Boolean {
        val path = virtualFile.path
        return path.contains("/test/") || path.contains("/tests/") ||
                path.contains("Test.") || path.contains("Tests.") ||
                path.contains("Spec.") || path.contains("_test.") ||
                path.contains("_spec.")
    }
    
    /**
     * Check if a virtual file is in library code.
     */
    protected fun isInLibraryCode(virtualFile: VirtualFile, project: Project): Boolean {
        val fileIndex = ProjectRootManager.getInstance(project).fileIndex
        return fileIndex.isInLibrary(virtualFile)
    }
    
    /**
     * Get line number from offset in a PSI file.
     */
    protected fun getLineNumber(element: PsiElement): Int {
        return com.intellij.openapi.application.ReadAction.compute<Int, Exception> {
            val document = PsiDocumentManager.getInstance(element.project)
                .getDocument(element.containingFile)
            document?.getLineNumber(element.textRange?.startOffset ?: 0) ?: 0
        }
    }
    
    /**
     * Get relative path from project base.
     */
    protected fun getRelativePath(element: PsiElement): String {
        val virtualFile = element.containingFile.virtualFile
        val project = element.project
        val basePath = project.basePath ?: ""
        return virtualFile.path.removePrefix(basePath).removePrefix("/")
    }
    
    /**
     * Check if element has annotation.
     */
    protected fun hasAnnotation(element: PsiModifierListOwner, annotationFqn: String): Boolean {
        return element.hasAnnotation(annotationFqn)
    }
    
    /**
     * Get annotations as string list.
     */
    protected fun getAnnotations(element: PsiModifierListOwner): List<String> {
        return element.annotations.map { annotation ->
            val name = annotation.qualifiedName?.substringAfterLast('.') ?: annotation.text
            if (name.startsWith("@")) name else "@$name"
        }
    }
    
    /**
     * Measure operation performance.
     */
    protected fun <T> measureOperation(operationName: String, block: () -> T): T {
        val start = System.currentTimeMillis()
        return try {
            block().also {
                val duration = System.currentTimeMillis() - start
                logger.debug("Operation '$operationName' completed in ${duration}ms")
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            logger.error("Operation '$operationName' failed after ${duration}ms", e)
            throw e
        }
    }
}
