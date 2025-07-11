package dev.mcp.extensions.lsp.languages.java

import com.intellij.psi.*
import dev.mcp.extensions.lsp.core.interfaces.SymbolExtractor
import dev.mcp.extensions.lsp.core.models.GetSymbolsArgs
import dev.mcp.extensions.lsp.core.models.SymbolInfo
import dev.mcp.extensions.lsp.languages.base.BaseLanguageHandler

/**
 * Symbol extractor implementation for Java and Kotlin languages.
 */
class JavaSymbolExtractor : BaseLanguageHandler(), SymbolExtractor {
    
    override fun extractSymbolsFlat(psiFile: PsiFile, args: GetSymbolsArgs): List<SymbolInfo> {
        logger.info("Extracting symbols (flat) from Java/Kotlin file: ${psiFile.name}")
        
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

        logger.debug("Extracted ${symbols.size} flat symbols")
        return symbols
    }
    
    override fun extractSymbolsHierarchical(psiFile: PsiFile, args: GetSymbolsArgs): List<SymbolInfo> {
        logger.info("Extracting symbols (hierarchical) from Java/Kotlin file: ${psiFile.name}")
        
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

        logger.debug("Extracted ${symbols.size} hierarchical symbols")
        return symbols
    }
    
    override fun supportsFile(psiFile: PsiFile): Boolean {
        val languageId = psiFile.language.id
        return languageId == "JAVA" || languageId == "kotlin" || languageId == "Kotlin"
    }
    
    override fun getSupportedLanguage(): String {
        return "Java/Kotlin"
    }
    
    private fun extractClassInfo(
        psiClass: PsiClass, 
        includeChildren: Boolean = false, 
        args: GetSymbolsArgs? = null
    ): SymbolInfo {
        val textRange = psiClass.textRange
        val document = PsiDocumentManager.getInstance(psiClass.project).getDocument(psiClass.containingFile)
        val lineNumber = document?.getLineNumber(textRange.startOffset) ?: 0
        
        val annotations = getAnnotations(psiClass)

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
            children = children,
            isDeprecated = hasAnnotation(psiClass, "java.lang.Deprecated"),
            hasJavadoc = psiClass.docComment != null,
            visibility = getVisibility(psiClass.modifierList),
            annotations = annotations
        )
    }

    private fun extractMethodInfo(psiMethod: PsiMethod): SymbolInfo {
        val textRange = psiMethod.textRange
        val document = PsiDocumentManager.getInstance(psiMethod.project).getDocument(psiMethod.containingFile)
        val lineNumber = document?.getLineNumber(textRange.startOffset) ?: 0
        
        val superMethods = psiMethod.findSuperMethods()
        val annotations = getAnnotations(psiMethod)

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
            returnType = psiMethod.returnType?.presentableText,
            isDeprecated = hasAnnotation(psiMethod, "java.lang.Deprecated"),
            hasJavadoc = psiMethod.docComment != null,
            isOverride = hasAnnotation(psiMethod, "java.lang.Override") || superMethods.isNotEmpty(),
            overrides = superMethods.firstOrNull()?.let { 
                "${it.containingClass?.qualifiedName}.${it.name}"
            },
            visibility = getVisibility(psiMethod.modifierList),
            annotations = annotations
        )
    }

    private fun extractFieldInfo(psiField: PsiField): SymbolInfo {
        val textRange = psiField.textRange
        val document = PsiDocumentManager.getInstance(psiField.project).getDocument(psiField.containingFile)
        val lineNumber = document?.getLineNumber(textRange.startOffset) ?: 0
        
        val annotations = getAnnotations(psiField)

        return SymbolInfo(
            name = psiField.name ?: "anonymous",
            type = psiField.type.presentableText,
            kind = "field",
            startOffset = textRange.startOffset,
            endOffset = textRange.endOffset,
            lineNumber = lineNumber + 1,
            modifiers = extractModifiers(psiField.modifierList),
            isDeprecated = hasAnnotation(psiField, "java.lang.Deprecated"),
            hasJavadoc = psiField.docComment != null,
            visibility = getVisibility(psiField.modifierList),
            annotations = annotations
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
}
