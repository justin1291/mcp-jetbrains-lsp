package dev.mcp.extensions.lsp.tools

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool
import java.io.File

@Serializable
data class GetSymbolsArgs(
    val filePath: String,
    val hierarchical: Boolean = true,
    val symbolTypes: List<String>? = null,
    val includeImports: Boolean = false
)

@Serializable
data class SymbolInfo(
    val name: String,
    val type: String,
    val kind: String,
    val startOffset: Int,
    val endOffset: Int,
    val lineNumber: Int,
    val modifiers: List<String> = emptyList(),
    val parameters: List<String>? = null,
    val returnType: String? = null,
    val children: List<SymbolInfo>? = null  // For hierarchical structure
)

class GetSymbolsInFileTool : AbstractMcpTool<GetSymbolsArgs>(GetSymbolsArgs.serializer()) {
    override val name: String = "get_symbols_in_file"
    override val description: String = """
        Extract all symbols (classes, methods, fields, etc.) from a specific file.
        
        Use this tool when you need to:
        - Get an overview of all classes, methods, and fields in a file
        - Understand the structure and organization of code
        - Find specific symbols within a file before using other tools
        - Create a mental map of what's available in a file
        
        Parameters:
        - filePath: Path to the file relative to project root
        - hierarchical: If true, returns nested structure (methods inside classes). If false, returns flat list
        - symbolTypes: Optional filter for specific types ["class", "method", "field", "import"]
        - includeImports: Whether to include import statements
        
        Returns a list of symbols with their locations, types, and modifiers.
        This is your starting point for understanding any file's contents.
    """.trimIndent()

    override fun handle(project: Project, args: GetSymbolsArgs): Response {
        return ReadAction.compute<Response, Exception> {
            try {
                val file = File(project.basePath, args.filePath)
                if (!file.exists()) {
                    return@compute Response(null, "File not found: ${args.filePath}")
                }

                val psiFile = PsiManager.getInstance(project).findFile(
                    VfsUtil.findFileByIoFile(file, true) ?: return@compute Response(null, "Cannot find file in VFS")
                ) ?: return@compute Response(null, "Cannot parse file")

                val symbols = if (args.hierarchical) {
                    extractSymbolsHierarchical(psiFile, args)
                } else {
                    extractSymbolsFlat(psiFile, args)
                }

                Response(Json.encodeToString(symbols))
            } catch (e: Exception) {
                Response(null, "Error extracting symbols: ${e.message}")
            }
        }
    }

    private fun extractSymbolsFlat(psiFile: PsiFile, args: GetSymbolsArgs): List<SymbolInfo> {
        val symbols = mutableListOf<SymbolInfo>()

        // Visit all PSI elements and extract symbols
        psiFile.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    is PsiClass -> {
                        if (shouldIncludeSymbol("class", args.symbolTypes)) {
                            symbols.add(extractClassInfo(element, includeChildren = false))
                        }
                    }
                    is PsiMethod -> {
                        if (shouldIncludeSymbol("method", args.symbolTypes)) {
                            symbols.add(extractMethodInfo(element))
                        }
                    }
                    is PsiField -> {
                        if (shouldIncludeSymbol("field", args.symbolTypes)) {
                            symbols.add(extractFieldInfo(element))
                        }
                    }
                    is PsiImportStatement -> {
                        if (args.includeImports && shouldIncludeSymbol("import", args.symbolTypes)) {
                            symbols.add(extractImportInfo(element))
                        }
                    }
                }
                super.visitElement(element)
            }
        })

        return symbols
    }

    private fun extractSymbolsHierarchical(psiFile: PsiFile, args: GetSymbolsArgs): List<SymbolInfo> {
        val symbols = mutableListOf<SymbolInfo>()

        // Process imports first if requested
        if (args.includeImports && shouldIncludeSymbol("import", args.symbolTypes)) {
            psiFile.children.filterIsInstance<PsiImportList>().forEach { importList ->
                importList.importStatements.forEach { import ->
                    symbols.add(extractImportInfo(import))
                }
            }
        }

        // Process top-level elements
        psiFile.children.forEach { element ->
            when (element) {
                is PsiClass -> {
                    if (shouldIncludeSymbol("class", args.symbolTypes)) {
                        symbols.add(extractClassInfo(element, includeChildren = true, args = args))
                    }
                }
                // Handle other top-level elements if needed
            }
        }

        return symbols
    }

    private fun extractClassInfo(psiClass: PsiClass, includeChildren: Boolean = false, args: GetSymbolsArgs? = null): SymbolInfo {
        val textRange = psiClass.textRange
        val document = PsiDocumentManager.getInstance(psiClass.project).getDocument(psiClass.containingFile)
        val lineNumber = document?.getLineNumber(textRange.startOffset) ?: 0

        val children = if (includeChildren && args != null) {
            val childSymbols = mutableListOf<SymbolInfo>()
            
            // Add inner classes
            psiClass.innerClasses.forEach { innerClass ->
                if (shouldIncludeSymbol("class", args.symbolTypes)) {
                    childSymbols.add(extractClassInfo(innerClass, includeChildren = true, args = args))
                }
            }
            
            // Add methods
            psiClass.methods.forEach { method ->
                if (shouldIncludeSymbol("method", args.symbolTypes)) {
                    childSymbols.add(extractMethodInfo(method))
                }
            }
            
            // Add fields
            psiClass.fields.forEach { field ->
                if (shouldIncludeSymbol("field", args.symbolTypes)) {
                    childSymbols.add(extractFieldInfo(field))
                }
            }
            
            childSymbols
        } else null

        return SymbolInfo(
            name = psiClass.name ?: "anonymous",
            type = psiClass.qualifiedName ?: psiClass.name ?: "unknown",
            kind = when {
                psiClass.isInterface -> "interface"
                psiClass.isEnum -> "enum"
                psiClass.isAnnotationType -> "annotation"
                else -> "class"
            },
            startOffset = textRange.startOffset,
            endOffset = textRange.endOffset,
            lineNumber = lineNumber + 1, // Convert to 1-based
            modifiers = extractModifiers(psiClass.modifierList),
            children = children
        )
    }

    private fun extractMethodInfo(psiMethod: PsiMethod): SymbolInfo {
        val textRange = psiMethod.textRange
        val document = PsiDocumentManager.getInstance(psiMethod.project).getDocument(psiMethod.containingFile)
        val lineNumber = document?.getLineNumber(textRange.startOffset) ?: 0

        return SymbolInfo(
            name = psiMethod.name,
            type = "${psiMethod.containingClass?.qualifiedName ?: "unknown"}.${psiMethod.name}",
            kind = if (psiMethod.isConstructor) "constructor" else "method",
            startOffset = textRange.startOffset,
            endOffset = textRange.endOffset,
            lineNumber = lineNumber + 1,
            modifiers = extractModifiers(psiMethod.modifierList),
            parameters = psiMethod.parameterList.parameters.map { param ->
                "${param.type.presentableText} ${param.name}"
            },
            returnType = psiMethod.returnType?.presentableText
        )
    }

    private fun extractFieldInfo(psiField: PsiField): SymbolInfo {
        val textRange = psiField.textRange
        val document = PsiDocumentManager.getInstance(psiField.project).getDocument(psiField.containingFile)
        val lineNumber = document?.getLineNumber(textRange.startOffset) ?: 0

        return SymbolInfo(
            name = psiField.name ?: "anonymous",
            type = psiField.type.presentableText,
            kind = "field",
            startOffset = textRange.startOffset,
            endOffset = textRange.endOffset,
            lineNumber = lineNumber + 1,
            modifiers = extractModifiers(psiField.modifierList)
        )
    }

    private fun extractImportInfo(psiImport: PsiImportStatement): SymbolInfo {
        val textRange = psiImport.textRange
        val document = PsiDocumentManager.getInstance(psiImport.project).getDocument(psiImport.containingFile)
        val lineNumber = document?.getLineNumber(textRange.startOffset) ?: 0

        return SymbolInfo(
            name = psiImport.qualifiedName ?: "unknown",
            type = if (psiImport.isOnDemand) "wildcard-import" else "import",
            kind = "import",
            startOffset = textRange.startOffset,
            endOffset = textRange.endOffset,
            lineNumber = lineNumber + 1
        )
    }

    private fun shouldIncludeSymbol(symbolType: String, filterTypes: List<String>?): Boolean {
        return filterTypes == null || filterTypes.contains(symbolType)
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
