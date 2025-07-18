package dev.mcp.extensions.lsp.languages.javascript

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import dev.mcp.extensions.lsp.core.interfaces.DefinitionFinder
import dev.mcp.extensions.lsp.core.models.DefinitionLocation
import dev.mcp.extensions.lsp.core.utils.LanguageUtils
import dev.mcp.extensions.lsp.languages.base.BaseLanguageHandler

/**
 * JavaScript/TypeScript definition finder with support for:
 * - ES6 modules (import/export)
 * - CommonJS modules (require/module.exports)
 * - Function declarations and expressions
 * - Class definitions
 * - Variable declarations (var, let, const)
 * - Object properties and methods
 * - TypeScript interfaces and types
 */
@Service
class JavaScriptDefinitionFinder : BaseLanguageHandler(), DefinitionFinder {

    override fun findDefinitionByPosition(psiFile: PsiFile, position: Int): List<DefinitionLocation> {
        if (psiFile.virtualFile == null || position < 0 || position >= psiFile.textLength) {
            return emptyList()
        }

        try {
            // Find element at position with better context handling
            val elementAtPosition = findElementAtPositionWithContext(psiFile, position)
            if (elementAtPosition != null) {
                // Try reference resolution first (most common case)
                val referenceResult = resolveReferenceAtElement(elementAtPosition)
                if (referenceResult.isNotEmpty()) return referenceResult

                // Try finding declaration in containing elements
                val declarationResult = findDeclarationAtPosition(elementAtPosition)
                if (declarationResult.isNotEmpty()) return declarationResult
            }

            // Fallback: search in nearby positions
            return findDefinitionWithFallback(psiFile, position)

        } catch (e: Exception) {
            logger.warn("Error finding definition at position $position in ${psiFile.name}: ${e.message}")
            return emptyList()
        }
    }

    override fun findDefinitionByName(project: Project, symbolName: String): List<DefinitionLocation> {
        if (symbolName.isBlank()) return emptyList()

        try {
            val projectScope = com.intellij.psi.search.GlobalSearchScope.projectScope(project)
            val allScope = com.intellij.psi.search.GlobalSearchScope.allScope(project)
            val definitions = mutableListOf<DefinitionLocation>()

            // Handle qualified names (e.g., "object.method")
            val parts = symbolName.split(".")
            if (parts.size >= 2) {
                searchQualifiedSymbol(project, parts, projectScope, definitions, isProjectScope = true)
                searchQualifiedSymbol(project, parts, allScope, definitions, isProjectScope = false)
            } else {
                // Simple name search
                searchSimpleSymbol(project, symbolName, projectScope, definitions, isProjectScope = true)
                searchSimpleSymbol(project, symbolName, allScope, definitions, isProjectScope = false)
            }

            return definitions.sortedByDescending { it.confidence }

        } catch (e: Exception) {
            logger.warn("Error finding definition by name '$symbolName': ${e.message}")
            return emptyList()
        }
    }

    private fun findElementAtPositionWithContext(psiFile: PsiFile, position: Int): PsiElement? {
        try {
            // Try the exact position first
            var element = psiFile.findElementAt(position)
            if (element != null && element.text.isNotBlank()) {
                return element
            }

            // Try nearby positions to handle whitespace and edge cases
            for (offset in 1..3) {
                // Try before
                if (position - offset >= 0) {
                    element = psiFile.findElementAt(position - offset)
                    if (element != null && element.text.isNotBlank()) {
                        return element
                    }
                }
                
                // Try after
                if (position + offset < psiFile.textLength) {
                    element = psiFile.findElementAt(position + offset)
                    if (element != null && element.text.isNotBlank()) {
                        return element
                    }
                }
            }

            return null
        } catch (e: Exception) {
            logger.debug("Error finding element at position: ${e.message}")
            return null
        }
    }

    private fun resolveReferenceAtElement(element: PsiElement): List<DefinitionLocation> {
        try {
            // Try parent reference first
            val parentReference = element.parent?.reference
            if (parentReference != null) {
                val resolved = parentReference.resolve()
                if (resolved != null) {
                    val location = createJavaScriptLocation(resolved)
                    if (location != null) {
                        return listOf(location.copy(confidence = 1.0f))
                    }
                }
            }

            // Try direct reference
            val directReference = element.reference
            if (directReference != null) {
                val resolved = directReference.resolve()
                if (resolved != null) {
                    val location = createJavaScriptLocation(resolved)
                    if (location != null) {
                        return listOf(location.copy(confidence = 1.0f))
                    }
                }
            }

        } catch (e: Exception) {
            logger.debug("Error resolving reference: ${e.message}")
        }
        return emptyList()
    }

    private fun findDeclarationAtPosition(element: PsiElement): List<DefinitionLocation> {
        try {
            // Look for containing function, class, or variable declaration
            var parent = element.parent
            while (parent != null) {
                val location = when {
                    isJavaScriptFunction(parent) -> createJavaScriptLocation(parent)
                    isJavaScriptClass(parent) -> createJavaScriptLocation(parent)
                    isJavaScriptVariable(parent) -> createJavaScriptLocation(parent)
                    isTypeScriptInterface(parent) -> createJavaScriptLocation(parent)
                    else -> null
                }
                
                if (location != null) {
                    return listOf(location.copy(confidence = 0.8f))
                }
                parent = parent.parent
            }
        } catch (e: Exception) {
            logger.debug("Error finding declaration: ${e.message}")
        }
        return emptyList()
    }

    private fun findDefinitionWithFallback(psiFile: PsiFile, position: Int): List<DefinitionLocation> {
        try {
            // Search in nearby positions (-5 to +5 characters)
            for (offset in -5..5) {
                val adjustedPosition = position + offset
                if (adjustedPosition >= 0 && adjustedPosition < psiFile.textLength) {
                    val element = psiFile.findElementAt(adjustedPosition)
                    if (element != null) {
                        val result = resolveReferenceAtElement(element)
                        if (result.isNotEmpty()) {
                            return result.map {
                                it.copy(
                                    confidence = it.confidence * 0.8f,
                                    disambiguationHint = "Near search: ${it.disambiguationHint ?: "Found nearby"}"
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug("Error in fallback search: ${e.message}")
        }
        return emptyList()
    }

    private fun searchQualifiedSymbol(
        project: Project,
        parts: List<String>,
        scope: com.intellij.psi.search.GlobalSearchScope,
        definitions: MutableList<DefinitionLocation>,
        isProjectScope: Boolean
    ) {
        try {
            // Search for files that might contain the symbol
            val searchService = com.intellij.psi.search.PsiSearchHelper.getInstance(project)
            
            // Search for each part of the qualified name
            parts.forEach { part ->
                searchService.processElementsWithWord(
                    { element, _ ->
                        if (LanguageUtils.isJavaScriptOrTypeScript(element.language)) {
                            val location = createJavaScriptLocation(element)
                            if (location != null && location.name.contains(part)) {
                                definitions.add(
                                    location.copy(
                                        confidence = calculateJavaScriptConfidence(element, parts.joinToString(".")) *
                                            if (isProjectScope) 1.0f else 0.7f
                                    )
                                )
                            }
                        }
                        true
                    },
                    scope,
                    parts.last(),
                    com.intellij.psi.search.UsageSearchContext.ANY,
                    true
                )
            }
        } catch (e: Exception) {
            logger.debug("Error searching qualified symbol: ${e.message}")
        }
    }

    private fun searchSimpleSymbol(
        project: Project,
        symbolName: String,
        scope: com.intellij.psi.search.GlobalSearchScope,
        definitions: MutableList<DefinitionLocation>,
        isProjectScope: Boolean
    ) {
        try {
            val searchService = com.intellij.psi.search.PsiSearchHelper.getInstance(project)
            
            searchService.processElementsWithWord(
                { element, _ ->
                    if (LanguageUtils.isJavaScriptOrTypeScript(element.language)) {
                        if (isJavaScriptDefinitionElement(element) && 
                            (getElementName(element) == symbolName || element.text.contains(symbolName))) {
                            val location = createJavaScriptLocation(element)
                            if (location != null) {
                                definitions.add(
                                    location.copy(
                                        confidence = calculateJavaScriptConfidence(element, symbolName) *
                                            if (isProjectScope) 1.0f else 0.7f
                                    )
                                )
                            }
                        }
                    }
                    true
                },
                scope,
                symbolName,
                com.intellij.psi.search.UsageSearchContext.ANY,
                true
            )
        } catch (e: Exception) {
            logger.debug("Error searching simple symbol: ${e.message}")
        }
    }

    private fun createJavaScriptLocation(element: PsiElement): DefinitionLocation? {
        try {
            val containingFile = element.containingFile ?: return null
            val virtualFile = containingFile.virtualFile ?: return null
            val project = element.project
            val textRange = element.textRange ?: return null

            // Safe path calculation
            val basePath = project.basePath ?: ""
            val relativePath = try {
                virtualFile.path.removePrefix(basePath).removePrefix("/")
            } catch (e: Exception) {
                virtualFile.name
            }

            // Safe line number calculation
            val lineNumber = try {
                val document = com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(containingFile)
                document?.getLineNumber(textRange.startOffset)?.plus(1) ?: 1
            } catch (e: Exception) {
                1
            }

            val elementName = getElementName(element)
            val elementType = determineJavaScriptElementType(element)
            val signature = buildJavaScriptSignature(element)

            return DefinitionLocation(
                name = elementName,
                filePath = relativePath,
                startOffset = textRange.startOffset,
                endOffset = textRange.endOffset,
                lineNumber = lineNumber,
                type = elementType,
                signature = signature,
                isTestCode = isInTestCode(virtualFile),
                isLibraryCode = isInLibraryCode(virtualFile, project),
                disambiguationHint = generateJavaScriptDisambiguationHint(element, elementType)
            )

        } catch (e: Exception) {
            logger.warn("Error creating JavaScript location: ${e.message}")
            return null
        }
    }

    private fun isJavaScriptFunction(element: PsiElement): Boolean {
        val text = element.text.lowercase()
        return text.contains("function ") || 
               text.matches(Regex(".*\\w+\\s*\\(.*\\)\\s*=>.*")) ||
               text.matches(Regex(".*\\w+\\s*\\(.*\\)\\s*\\{.*"))
    }

    private fun isJavaScriptClass(element: PsiElement): Boolean {
        val text = element.text.lowercase()
        return text.contains("class ") || text.contains("interface ")
    }

    private fun isJavaScriptVariable(element: PsiElement): Boolean {
        val text = element.text.lowercase()
        return text.contains("var ") || text.contains("let ") || text.contains("const ")
    }

    private fun isTypeScriptInterface(element: PsiElement): Boolean {
        val text = element.text.lowercase()
        return text.contains("interface ") || text.contains("type ")
    }

    private fun isJavaScriptDefinitionElement(element: PsiElement): Boolean {
        return isJavaScriptFunction(element) || 
               isJavaScriptClass(element) || 
               isJavaScriptVariable(element) ||
               isTypeScriptInterface(element)
    }

    private fun getElementName(element: PsiElement): String {
        // Try to extract the name from various JavaScript/TypeScript patterns
        val text = element.text
        
        // Function declarations: function name() or const name = () =>
        val functionMatch = Regex("""(?:function\s+(\w+)|(?:const|let|var)\s+(\w+)\s*=|\s*(\w+)\s*\()""")
            .find(text)
        if (functionMatch != null) {
            return functionMatch.groupValues.find { it.isNotBlank() && it != text } ?: "anonymous"
        }

        // Class declarations: class Name
        val classMatch = Regex("""class\s+(\w+)""").find(text)
        if (classMatch != null) {
            return classMatch.groupValues[1]
        }

        // Interface/Type declarations: interface Name or type Name
        val typeMatch = Regex("""(?:interface|type)\s+(\w+)""").find(text)
        if (typeMatch != null) {
            return typeMatch.groupValues[1]
        }

        // Variable declarations: var/let/const name
        val varMatch = Regex("""(?:var|let|const)\s+(\w+)""").find(text)
        if (varMatch != null) {
            return varMatch.groupValues[1]
        }

        // Fallback to element text or "unknown"
        return (element as? PsiNamedElement)?.name 
            ?: element.text.take(50).trim()
            ?: "unknown"
    }

    private fun determineJavaScriptElementType(element: PsiElement): String {
        return when {
            isJavaScriptFunction(element) -> "function"
            isJavaScriptClass(element) -> {
                if (element.text.lowercase().contains("interface")) "interface" else "class"
            }
            isJavaScriptVariable(element) -> {
                when {
                    element.text.contains("const") -> "constant"
                    element.text.contains("let") -> "variable"
                    element.text.contains("var") -> "variable"
                    else -> "variable"
                }
            }
            isTypeScriptInterface(element) -> {
                if (element.text.lowercase().contains("type")) "type" else "interface"
            }
            else -> "unknown"
        }
    }

    private fun buildJavaScriptSignature(element: PsiElement): String? {
        return try {
            val text = element.text.lines().firstOrNull()?.take(100)?.trim()
            text
        } catch (e: Exception) {
            null
        }
    }

    private fun generateJavaScriptDisambiguationHint(element: PsiElement, elementType: String): String? {
        return try {
            val containingFile = element.containingFile
            val fileName = containingFile?.name ?: "unknown file"
            val languageId = element.language.id
            
            when (elementType) {
                "function" -> {
                    if (element.text.contains("=>")) {
                        "Arrow function in $fileName"
                    } else {
                        "$languageId function in $fileName"
                    }
                }
                "class" -> "$languageId class in $fileName"
                "interface" -> "TypeScript interface in $fileName"
                "type" -> "TypeScript type in $fileName"
                "constant" -> "Constant in $fileName"
                "variable" -> "Variable in $fileName"
                else -> "$languageId element in $fileName"
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateJavaScriptConfidence(element: PsiElement, searchTerm: String): Float {
        return try {
            val elementName = getElementName(element)
            val containingFile = element.containingFile
            val virtualFile = containingFile?.virtualFile
            val project = element.project

            val isInLibrary = try {
                virtualFile?.let { isInLibraryCode(it, project) } ?: false
            } catch (e: Exception) {
                false
            }

            when {
                // Exact name match in project code
                elementName == searchTerm && !isInLibrary -> 1.0f
                // Exact name match in library code  
                elementName == searchTerm && isInLibrary -> 0.5f
                // Case-insensitive match
                elementName.equals(searchTerm, ignoreCase = true) -> 0.7f
                // Partial match
                elementName.contains(searchTerm, ignoreCase = true) -> 0.3f
                else -> 0.1f
            }
        } catch (e: Exception) {
            0.1f
        }
    }

    override fun createLocation(element: PsiElement, searchTerm: String?): DefinitionLocation {
        return createJavaScriptLocation(element) ?: createFallbackLocation(element, searchTerm)
    }

    private fun createFallbackLocation(element: PsiElement, searchTerm: String?): DefinitionLocation {
        val containingFile = element.containingFile
        val virtualFile = containingFile?.virtualFile
        val textRange = element.textRange
        
        return DefinitionLocation(
            name = getElementName(element),
            filePath = virtualFile?.name ?: "unknown",
            startOffset = textRange?.startOffset ?: 0,
            endOffset = textRange?.endOffset ?: 0,
            lineNumber = 1,
            type = "unknown",
            signature = element.text?.lines()?.firstOrNull()?.take(100),
            isTestCode = false,
            isLibraryCode = false,
            confidence = 0.1f,
            disambiguationHint = "JavaScript/TypeScript element"
        )
    }

    override fun supportsElement(element: PsiElement): Boolean {
        return LanguageUtils.isJavaScriptOrTypeScript(element.language)
    }

    override fun supportsFile(psiFile: PsiFile): Boolean {
        return LanguageUtils.isJavaScriptOrTypeScript(psiFile.language)
    }

    override fun getSupportedLanguage(): String {
        return "JavaScript/TypeScript"
    }
}
