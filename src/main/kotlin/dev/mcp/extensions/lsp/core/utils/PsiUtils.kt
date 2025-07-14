package dev.mcp.extensions.lsp.core.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import java.io.File

/**
 * Utility functions for PSI operations.
 */
object PsiUtils {
    
    /**
     * Get a PSI file from a project and relative path.
     * 
     * @param project The project
     * @param relativePath Path relative to project base
     * @return PsiFile if found, null otherwise
     */
    fun getPsiFile(project: Project, relativePath: String): PsiFile? {
        val basePath = project.basePath ?: return null
        val file = File(basePath, relativePath)
        
        if (!file.exists()) {
            return null
        }
        
        val virtualFile = VfsUtil.findFileByIoFile(file, true) ?: return null
        return PsiManager.getInstance(project).findFile(virtualFile)
    }
    
    /**
     * Get relative path from absolute path.
     * 
     * @param project The project
     * @param absolutePath Absolute file path
     * @return Relative path from project base
     */
    fun getRelativePath(project: Project, absolutePath: String): String {
        val basePath = project.basePath ?: return absolutePath
        return absolutePath.removePrefix(basePath).removePrefix("/")
    }
    
    /**
     * Check if a file exists in the project.
     * 
     * @param project The project
     * @param relativePath Path relative to project base
     * @return true if file exists
     */
    fun fileExists(project: Project, relativePath: String): Boolean {
        val basePath = project.basePath ?: return false
        val file = File(basePath, relativePath)
        return file.exists()
    }
    
    /**
     * Get all project files (excluding libraries and external files).
     * This is useful for searching across the entire project.
     * 
     * @param project The project
     * @return List of PSI files in the project
     */
    fun getAllProjectFiles(project: Project): List<PsiFile> {
        val result = mutableListOf<PsiFile>()
        val psiManager = PsiManager.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)
        
        val allFileNames = FilenameIndex.getAllFilenames(project)
        
        for (fileName in allFileNames) {
            val virtualFiles = FilenameIndex.getVirtualFilesByName(fileName, scope)
            
            for (virtualFile in virtualFiles) {
                val psiFile = psiManager.findFile(virtualFile) ?: continue
                
                if (isProjectFile(project, virtualFile)) {
                    result.add(psiFile)
                }
            }
        }
        
        return result
    }
    
    /**
     * Check if a virtual file belongs to the project (not a library).
     */
    private fun isProjectFile(project: Project, virtualFile: VirtualFile): Boolean {
        val basePath = project.basePath ?: return false
        return virtualFile.path.startsWith(basePath)
    }
}
