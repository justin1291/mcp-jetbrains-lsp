package dev.mcp.extensions.lsp.tools

import com.intellij.openapi.application.ReadAction
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File

@Serializable
data class FindReferencesArgs(
    val symbolName: String?,
    val filePath: String? = null,
    val position: Int? = null,
    val includeDeclaration: Boolean = false
)

@Serializable
data class ReferenceInfo(
    val filePath: String,
    val startOffset: Int,
    val endOffset: Int,
    val lineNumber: Int,
    val usageType: String,
    val elementText: String? = null,
    val preview: String? = null,
    val containingMethod: String? = null,
    val containingClass: String? = null
)

class FindSymbolReferencesTool : AbstractMcpTool<FindReferencesArgs>(FindReferencesArgs.serializer()) {
    override val name: String = "find_symbol_references"
    override val description: String = """
        Find all places where a symbol is used or referenced (find usages).
        
        Use this tool when you need to:
        - Understand the impact of changing a method, class, or field
        - Find all callers of a specific method
        - See where a variable is read or written
        - Track down all usages before refactoring
        - Analyze dependencies and coupling
        
        The tool categorizes each usage by type:
        - method_call: Method is being invoked
        - read: Field/variable is being read
        - write: Field/variable is being assigned
        - type_reference: Used as a type (e.g., in declarations)
        - constructor_call: Constructor is being invoked
        - declaration: The original declaration
        - override: Method override
        
        Parameters:
        - symbolName: The name of the symbol to find references for
        - filePath: (Optional) File containing the symbol
        - position: (Optional) Character offset of the symbol
        - includeDeclaration: Whether to include the original declaration in results
        
        Returns all locations where the symbol is referenced, with context and usage type.
        This is equivalent to "Find Usages" or Shift+F12 in an IDE.
    """.trimIndent()

    override fun handle(project: Project, args: FindReferencesArgs): Response {
        return ReadAction.compute<Response, Exception> {
            try {
                val element = findTargetElement(project, args) 
                    ?: return@compute Response(Json.encodeToString(emptyList<ReferenceInfo>()))
                
                val references = mutableListOf<ReferenceInfo>()
                val scope = GlobalSearchScope.projectScope(project)
                
                // Find all references to the element
                val query = ReferencesSearch.search(element, scope)
                
                query.forEach { reference ->
                    val refElement = reference.element
                    val location = createReferenceInfo(refElement, element)
                    references.add(location)
                }
                
                // Special handling for constructors - find new expressions
                if (element is PsiMethod && element.isConstructor) {
                    findConstructorCalls(element, scope).forEach { newExpr ->
                        references.add(createReferenceInfo(newExpr, element))
                    }
                }
                
                // Find overrides if it's a method
                if (element is PsiMethod) {
                    findOverrides(element, scope).forEach { override ->
                        references.add(createReferenceInfo(override, element, "override"))
                    }
                }
                
                // Optionally include the declaration itself
                if (args.includeDeclaration) {
                    references.add(0, createReferenceInfo(element, element, "declaration"))
                }
                
                Response(Json.encodeToString(references))
            } catch (e: Exception) {
                Response(null, "Error finding symbol references: ${e.message}")
            }
        }
    }

    private fun findTargetElement(project: Project, args: FindReferencesArgs): PsiElement? {
        // If position is provided, find element at that position
        if (args.filePath != null && args.position != null) {
            val file = File(project.basePath, args.filePath)
            if (!file.exists()) return null
            
            val psiFile = PsiManager.getInstance(project).findFile(
                VfsUtil.findFileByIoFile(file, true) ?: return null
            ) ?: return null
            
            val element = psiFile.findElementAt(args.position) ?: return null
            
            // Try to resolve reference at this position
            val reference = element.parent?.reference ?: element.reference
            if (reference != null) {
                return reference.resolve()
            }
            
            // Try to find a named element at this position
            return PsiTreeUtil.getParentOfType(element, PsiNamedElement::class.java)
        }
        
        // Otherwise, search by name
        if (args.symbolName == null) return null
        
        val scope = GlobalSearchScope.projectScope(project)
        val cache = PsiShortNamesCache.getInstance(project)
        
        // Try to find as a class first
        JavaPsiFacade.getInstance(project).findClass(args.symbolName, scope)?.let { return it }
        
        // Try methods
        cache.getMethodsByName(args.symbolName, scope).firstOrNull()?.let { return it }
        
        // Try fields using cache
        cache.getFieldsByName(args.symbolName, scope).firstOrNull()?.let { return it }
        
        // Fallback: manually search through all Java files in project (for test scenarios)
        return findElementByManualSearch(project, args.symbolName, scope)
    }
    
    private fun findElementByManualSearch(project: Project, symbolName: String, scope: GlobalSearchScope): PsiElement? {
        val psiManager = PsiManager.getInstance(project)
        val fileIndex = ProjectFileIndex.getInstance(project)
        
        var foundElement: PsiElement? = null
        var javaFileCount = 0
        
        println("DEBUG: Starting manual search for '$symbolName'")
        
        // Search through all Java files in the scope
        fileIndex.iterateContent { virtualFile ->
            if (foundElement != null) return@iterateContent false // Stop if found
            
            if (virtualFile.extension == "java" && scope.contains(virtualFile)) {
                javaFileCount++
                println("DEBUG: Searching file: ${virtualFile.path}")
                val psiFile = psiManager.findFile(virtualFile) as? PsiJavaFile
                psiFile?.classes?.forEach { psiClass ->
                    println("DEBUG: Searching class: ${psiClass.qualifiedName}")
                    println("DEBUG: Class has ${psiClass.fields.size} fields: ${psiClass.fields.map { it.name }}")
                    
                    // Search for fields
                    psiClass.fields.find { it.name == symbolName }?.let { 
                        println("DEBUG: Found field: ${it.name}")
                        foundElement = it
                        return@forEach
                    }
                    // Search for methods
                    psiClass.methods.find { it.name == symbolName }?.let { 
                        foundElement = it
                        return@forEach
                    }
                    // Search for inner classes
                    psiClass.innerClasses.find { it.name == symbolName }?.let { 
                        foundElement = it
                        return@forEach
                    }
                }
            }
            true // Continue iteration
        }
        
        println("DEBUG: Manual search complete. Searched $javaFileCount Java files. Found: $foundElement")
        return foundElement
    }

    private fun createReferenceInfo(element: PsiElement, target: PsiElement, overrideType: String? = null): ReferenceInfo {
        val containingFile = element.containingFile
        val virtualFile = containingFile.virtualFile
        val project = element.project
        val basePath = project.basePath ?: ""
        val relativePath = virtualFile.path.removePrefix(basePath).removePrefix("/")
        
        val textRange = element.textRange
        val document = PsiDocumentManager.getInstance(project).getDocument(containingFile)
        val lineNumber = document?.getLineNumber(textRange.startOffset) ?: 0
        
        // Get the line containing the reference for preview
        val lineStartOffset = document?.getLineStartOffset(lineNumber) ?: 0
        val lineEndOffset = document?.getLineEndOffset(lineNumber) ?: textRange.endOffset
        val lineText = document?.text?.substring(lineStartOffset, lineEndOffset)?.trim() ?: element.text
        
        // Find containing method and class
        val containingMethod = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
        val containingClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
        
        return ReferenceInfo(
            filePath = relativePath,
            startOffset = textRange.startOffset,
            endOffset = textRange.endOffset,
            lineNumber = lineNumber + 1, // Convert to 1-based
            usageType = overrideType ?: determineUsageType(element, target),
            elementText = element.text?.trim(),
            preview = lineText,
            containingMethod = containingMethod?.name,
            containingClass = containingClass?.qualifiedName
        )
    }

    private fun determineUsageType(element: PsiElement, target: PsiElement): String {
        val parent = element.parent
        
        return when {
            element == target -> "declaration"
            parent is PsiMethodCallExpression -> "method_call"
            parent is PsiNewExpression && target is PsiMethod && target.isConstructor -> "constructor_call"
            parent is PsiReferenceExpression && parent.parent is PsiAssignmentExpression -> {
                val assignment = parent.parent as PsiAssignmentExpression
                if (assignment.lExpression == parent) "write" else "read"
            }
            parent is PsiReferenceExpression -> "read"
            parent is PsiImportStatement -> "import"
            parent is PsiTypeElement -> "type_reference"
            parent is PsiNewExpression -> "constructor_call"
            element is PsiMethod && target is PsiMethod && isOverride(element, target) -> "override"
            else -> "reference"
        }
    }

    private fun findConstructorCalls(constructor: PsiMethod, scope: GlobalSearchScope): List<PsiNewExpression> {
        val containingClass = constructor.containingClass ?: return emptyList()
        val result = mutableListOf<PsiNewExpression>()
        
        ReferencesSearch.search(containingClass, scope).forEach { ref ->
            val element = ref.element
            val parent = element.parent
            if (parent is PsiNewExpression) {
                result.add(parent)
            }
        }
        
        return result
    }

    private fun findOverrides(method: PsiMethod, scope: GlobalSearchScope): List<PsiMethod> {
        val result = mutableListOf<PsiMethod>()
        val containingClass = method.containingClass ?: return result
        
        // Find all subclasses
        val facade = JavaPsiFacade.getInstance(method.project)
        val allClasses = PsiShortNamesCache.getInstance(method.project)
            .getClassesByName("*", scope)
            .toList()
        
        for (psiClass in allClasses) {
            if (psiClass.isInheritor(containingClass, true)) {
                val overrideMethod = psiClass.findMethodBySignature(method, false)
                if (overrideMethod != null && overrideMethod != method) {
                    result.add(overrideMethod)
                }
            }
        }
        
        return result
    }

    private fun isOverride(method: PsiMethod, baseMethod: PsiMethod): Boolean {
        val methodClass = method.containingClass ?: return false
        val baseClass = baseMethod.containingClass ?: return false
        
        return methodClass != baseClass && 
               methodClass.isInheritor(baseClass, true) &&
               method.findSuperMethods().contains(baseMethod)
    }
}
