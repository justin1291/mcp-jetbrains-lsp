package dev.mcp.extensions.lsp.tools

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool
import java.io.File

@Serializable
data class FindDefinitionArgs(
    val symbolName: String?,
    val filePath: String? = null,
    val position: Int? = null
)

@Serializable
data class DefinitionLocation(
    val name: String,
    val filePath: String,
    val startOffset: Int,
    val endOffset: Int,
    val lineNumber: Int,
    val type: String,
    val signature: String? = null,
    val containingClass: String? = null,
    val modifiers: List<String> = emptyList(),
    val isAbstract: Boolean = false
)

class FindSymbolDefinitionTool : AbstractMcpTool<FindDefinitionArgs>(FindDefinitionArgs.serializer()) {
    override val name: String = "find_symbol_definition"
    override val description: String = """
        Navigate to where a symbol is defined (go-to-definition).
        
        Use this tool when you need to:
        - Find where a class, method, or field is declared
        - Jump to the source code of a symbol you're using
        - Understand the implementation details of a method
        - Locate the original declaration of any symbol
        
        You can search in two ways:
        1. By name: Just provide the symbol name (e.g., "ExpenseService")
        2. By position: Provide the file path and character position of a symbol reference
        
        Parameters:
        - symbolName: The name of the symbol to find
        - filePath: (Optional) File containing the symbol reference
        - position: (Optional) Character offset of the symbol reference
        
        Returns the exact location where the symbol is defined, including file path and line number.
        This is equivalent to Ctrl+Click or F12 in an IDE.
    """.trimIndent()

    override fun handle(project: Project, args: FindDefinitionArgs): Response {
        return ReadAction.compute<Response, Exception> {
            try {
                // If position is provided, use it to find the exact element
                if (args.filePath != null && args.position != null) {
                    findDefinitionByPosition(project, args.filePath, args.position)
                } else if (args.symbolName != null) {
                    // Otherwise, search by name
                    findDefinitionByName(project, args.symbolName)
                } else {
                    Response(null, "Either symbolName or filePath+position must be provided")
                }
            } catch (e: Exception) {
                Response(null, "Error finding symbol definition: ${e.message}")
            }
        }
    }

    private fun findDefinitionByPosition(project: Project, filePath: String, position: Int): Response {
        val file = File(project.basePath, filePath)
        if (!file.exists()) {
            return Response(null, "File not found: $filePath")
        }

        val psiFile = PsiManager.getInstance(project).findFile(
            VfsUtil.findFileByIoFile(file, true) ?: return Response(null, "Cannot find file in VFS")
        ) ?: return Response(null, "Cannot parse file")

        val element = psiFile.findElementAt(position) ?: return Response(Json.encodeToString(emptyList<DefinitionLocation>()))

        // Find the reference at this position
        val reference = element.parent?.reference ?: element.reference
        if (reference != null) {
            val resolved = reference.resolve()
            if (resolved != null) {
                return Response(Json.encodeToString(listOf(createLocation(resolved))))
            }
        }

        // Try to find a named element at this position
        val namedElement = PsiTreeUtil.getParentOfType(element, PsiNamedElement::class.java)
        if (namedElement != null) {
            return Response(Json.encodeToString(listOf(createLocation(namedElement))))
        }

        return Response(Json.encodeToString(emptyList<DefinitionLocation>()))
    }

    private fun findDefinitionByName(project: Project, symbolName: String): Response {
        val scope = GlobalSearchScope.projectScope(project)
        val definitions = mutableListOf<DefinitionLocation>()
        val cache = PsiShortNamesCache.getInstance(project)

        // Check if it's a qualified name (e.g., "ClassName.methodName")
        val parts = symbolName.split(".")
        if (parts.size == 2) {
            // Search for method in specific class
            val className = parts[0]
            val methodName = parts[1]
            val classes = JavaPsiFacade.getInstance(project).findClasses(className, scope)
            for (psiClass in classes) {
                val methods = psiClass.findMethodsByName(methodName, true)
                methods.forEach { definitions.add(createLocation(it)) }
            }
        } else {
            // Search for classes
            val psiClasses = JavaPsiFacade.getInstance(project).findClasses(symbolName, scope)
            psiClasses.forEach { definitions.add(createLocation(it)) }

            // Search for methods
            cache.getMethodsByName(symbolName, scope).forEach { 
                definitions.add(createLocation(it)) 
            }

            // Search for fields
            cache.getFieldsByName(symbolName, scope).forEach { 
                definitions.add(createLocation(it)) 
            }
        }

        return Response(Json.encodeToString(definitions))
    }

    private fun createLocation(element: PsiElement): DefinitionLocation {
        val containingFile = element.containingFile
        val virtualFile = containingFile.virtualFile
        val project = element.project
        val basePath = project.basePath ?: ""
        val relativePath = virtualFile.path.removePrefix(basePath).removePrefix("/")

        val textRange = element.textRange
        val document = PsiDocumentManager.getInstance(project).getDocument(containingFile)
        val lineNumber = document?.getLineNumber(textRange.startOffset) ?: 0

        return when (element) {
            is PsiClass -> DefinitionLocation(
                name = element.name ?: "anonymous",
                filePath = relativePath,
                startOffset = textRange.startOffset,
                endOffset = textRange.endOffset,
                lineNumber = lineNumber + 1,
                type = when {
                    element.isInterface -> "interface"
                    element.isEnum -> "enum"
                    element.isAnnotationType -> "annotation"
                    else -> "class"
                },
                signature = buildClassSignature(element),
                containingClass = element.containingClass?.qualifiedName,
                modifiers = extractModifiers(element.modifierList),
                isAbstract = element.hasModifierProperty(PsiModifier.ABSTRACT)
            )
            is PsiMethod -> DefinitionLocation(
                name = element.name,
                filePath = relativePath,
                startOffset = textRange.startOffset,
                endOffset = textRange.endOffset,
                lineNumber = lineNumber + 1,
                type = if (element.isConstructor) "constructor" else "method",
                signature = buildMethodSignature(element),
                containingClass = element.containingClass?.qualifiedName,
                modifiers = extractModifiers(element.modifierList),
                isAbstract = element.hasModifierProperty(PsiModifier.ABSTRACT)
            )
            is PsiField -> DefinitionLocation(
                name = element.name ?: "anonymous",
                filePath = relativePath,
                startOffset = textRange.startOffset,
                endOffset = textRange.endOffset,
                lineNumber = lineNumber + 1,
                type = "field",
                signature = "${element.type.presentableText} ${element.name}",
                containingClass = element.containingClass?.qualifiedName,
                modifiers = extractModifiers(element.modifierList)
            )
            is PsiVariable -> DefinitionLocation(
                name = element.name ?: "anonymous",
                filePath = relativePath,
                startOffset = textRange.startOffset,
                endOffset = textRange.endOffset,
                lineNumber = lineNumber + 1,
                type = "variable",
                signature = "${element.type.presentableText} ${element.name}",
                containingClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)?.qualifiedName,
                modifiers = extractModifiers(element.modifierList)
            )
            else -> DefinitionLocation(
                name = (element as? PsiNamedElement)?.name ?: "unknown",
                filePath = relativePath,
                startOffset = textRange.startOffset,
                endOffset = textRange.endOffset,
                lineNumber = lineNumber + 1,
                type = "unknown",
                signature = element.text?.lines()?.firstOrNull()?.trim()
            )
        }
    }

    private fun buildClassSignature(psiClass: PsiClass): String {
        val modifiers = psiClass.modifierList?.text ?: ""
        val type = when {
            psiClass.isInterface -> "interface"
            psiClass.isEnum -> "enum"
            psiClass.isAnnotationType -> "@interface"
            else -> "class"
        }
        return "$modifiers $type ${psiClass.name}".trim()
    }

    private fun buildMethodSignature(method: PsiMethod): String {
        val params = method.parameterList.parameters.joinToString(", ") { 
            "${it.type.presentableText} ${it.name}" 
        }
        val returnType = if (method.isConstructor) "" else "${method.returnType?.presentableText ?: "void"} "
        val modifiers = method.modifierList.text
        return "$modifiers $returnType${method.name}($params)".trim()
    }

    private fun extractModifiers(modifierList: PsiModifierList?): List<String> {
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
            PsiModifier.TRANSIENT
        ).filter { modifierList.hasModifierProperty(it) }
    }
}
