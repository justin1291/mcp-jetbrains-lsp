package dev.mcp.extensions.lsp.languages.python

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.stubs.PyClassNameIndex
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex
import dev.mcp.extensions.lsp.core.interfaces.DefinitionFinder
import dev.mcp.extensions.lsp.core.models.DefinitionLocation

/**
 * Definition finder implementation for Python language.
 * 
 * Registered as a service in mcp-lsp-python.xml when Python module is available.
 */
@Service
class PythonDefinitionFinder : PythonBaseHandler(), DefinitionFinder {
    
    // Python-specific utility methods (duplicated to avoid class loading issues)
    
    /**
     * Extract decorators from a decoratable element.
     */
    private fun extractDecorators(decorated: PyDecoratable): List<String> {
        return decorated.decoratorList?.decorators?.map { decorator ->
            "@${decorator.name ?: decorator.text}"
        } ?: emptyList()
    }
    
    /**
     * Check if a class is a dataclass.
     */
    private fun isDataclass(pyClass: PyClass): Boolean {
        return extractDecorators(pyClass).any { 
            it.contains("dataclass", ignoreCase = true) 
        }
    }
    
    /**
     * Check if an element is async.
     */
    private fun isAsync(element: PsiElement): Boolean {
        return when (element) {
            is PyFunction -> element.isAsync
            else -> false
        }
    }
    
    /**
     * Extract return type hint from a function.
     */
    private fun extractReturnTypeHint(function: PyFunction): String? {
        return function.annotation?.value?.text
    }
    
    /**
     * Get Python-specific modifiers for an element.
     */
    private fun getPythonModifiers(element: PsiElement): List<String> {
        val modifiers = mutableListOf<String>()
        
        when (element) {
            is PyFunction -> {
                if (element.isAsync) modifiers.add("async")
                if (element.isGenerator) modifiers.add("generator")
                val decorators = extractDecorators(element)
                if (decorators.contains("@abstractmethod")) modifiers.add("abstract")
                if (decorators.contains("@staticmethod")) modifiers.add("static")
                if (decorators.contains("@classmethod")) modifiers.add("class")
                if (decorators.contains("@property")) modifiers.add("property")
            }
            is PyClass -> {
                if (hasAbstractMethods(element)) modifiers.add("abstract")
                if (isDataclass(element)) modifiers.add("dataclass")
            }
        }
        
        return modifiers
    }
    
    /**
     * Check if a class has abstract methods (making it abstract).
     */
    private fun hasAbstractMethods(pyClass: PyClass): Boolean {
        // Check if inherits from ABC
        val inheritsFromABC = pyClass.superClassExpressions.any { expr ->
            expr.text.contains("ABC") || expr.text.contains("ABCMeta")
        }
        
        // Check if has any methods with @abstractmethod
        val hasAbstractMethod = pyClass.methods.any { method ->
            extractDecorators(method).contains("@abstractmethod")
        }
        
        return inheritsFromABC || hasAbstractMethod
    }
    
    override fun findDefinitionByPosition(psiFile: PsiFile, position: Int): List<DefinitionLocation> {
        logger.info("Finding Python definition at position $position in ${psiFile.name}")
        
        val element = psiFile.findElementAt(position) 
            ?: return emptyList()

        // Find the reference at this position
        val reference = element.parent?.reference ?: element.reference
        if (reference != null) {
            val resolved = reference.resolve()
            if (resolved != null) {
                val location = createLocation(resolved)
                return listOf(
                    location.copy(
                        confidence = 1.0f, // Direct reference resolution
                        disambiguationHint = generateDisambiguationHint(resolved)
                    )
                )
            }
        }

        // Try to find a named element at this position  
        val namedElement = findNamedElementAtPosition(element)
        if (namedElement != null) {
            val location = createLocation(namedElement)
            return listOf(
                location.copy(
                    confidence = 0.9f, // Found at position but not through reference
                    disambiguationHint = generateDisambiguationHint(namedElement)
                )
            )
        }

        return emptyList()
    }
    
    override fun findDefinitionByName(project: Project, symbolName: String): List<DefinitionLocation> {
        logger.info("Finding Python definition by name: $symbolName")
        
        val scope = GlobalSearchScope.projectScope(project)
        val definitions = mutableListOf<DefinitionLocation>()

        try {
            // Check if it's a qualified name (e.g., "ClassName.methodName")
            val parts = symbolName.split(".")
            if (parts.size == 2) {
                // Search for method/attribute in specific class
                val className = parts[0]
                val memberName = parts[1]
                
                // Find classes with the given name
                val classes = PyClassNameIndex.find(className, project, scope)
                for (pyClass in classes) {
                    // Look for methods
                    val methods = pyClass.findMethodByName(memberName, true, null)
                    if (methods != null) {
                        val location = createLocation(methods, symbolName)
                        definitions.add(
                            location.copy(
                                confidence = 1.0f, // Exact qualified match
                                disambiguationHint = "Method in ${pyClass.qualifiedName ?: pyClass.name}"
                            )
                        )
                    }
                    
                    // Look for attributes/fields
                    val attributes = pyClass.findClassAttribute(memberName, true, null)
                    if (attributes != null) {
                        val location = createLocation(attributes, symbolName)
                        definitions.add(
                            location.copy(
                                confidence = 1.0f, // Exact qualified match
                                disambiguationHint = "Attribute in ${pyClass.qualifiedName ?: pyClass.name}"
                            )
                        )
                    }
                }
            } else {
                // Search for classes
                val classes = PyClassNameIndex.find(symbolName, project, scope)
                classes.forEach { pyClass ->
                    val location = createLocation(pyClass, symbolName)
                    definitions.add(
                        location.copy(
                            confidence = calculateConfidence(pyClass, symbolName, isClass = true),
                            disambiguationHint = generateDisambiguationHint(pyClass)
                        )
                    )
                }

                // Search for functions
                val functions = PyFunctionNameIndex.find(symbolName, project, scope)
                functions.forEach { pyFunction ->
                    val location = createLocation(pyFunction, symbolName)
                    definitions.add(
                        location.copy(
                            confidence = calculateConfidence(pyFunction, symbolName, isClass = false),
                            disambiguationHint = generateDisambiguationHint(pyFunction)
                        )
                    )
                }
            }

        } catch (e: Exception) {
            logger.warn("Error searching for Python symbol '$symbolName': ${e.message}")
        }

        // Sort by confidence (highest first)
        val sortedDefinitions = definitions.sortedByDescending { it.confidence }
        
        logger.debug("Found ${sortedDefinitions.size} Python definitions for '$symbolName'")
        return sortedDefinitions
    }
    
    override fun createLocation(element: PsiElement, searchTerm: String?): DefinitionLocation {
        val containingFile = element.containingFile
        val virtualFile = containingFile.virtualFile
        val project = element.project
        val basePath = project.basePath ?: ""
        val relativePath = virtualFile.path.removePrefix(basePath).removePrefix("/")

        val textRange = element.textRange
        val document = PsiDocumentManager.getInstance(project).getDocument(containingFile)
        val lineNumber = document?.getLineNumber(textRange.startOffset) ?: 0

        // Check if in test or library code
        val isTestCode = isInTestCode(virtualFile)
        val isLibraryCode = isInLibraryCode(virtualFile, project)

        return when (element) {
            is PyClass -> DefinitionLocation(
                name = element.name ?: "anonymous",
                filePath = relativePath,
                startOffset = textRange.startOffset,
                endOffset = textRange.endOffset,
                lineNumber = lineNumber + 1,
                type = determineClassType(element),
                signature = buildClassSignature(element),
                containingClass = getContainingClass(element),
                modifiers = getPythonModifiers(element),
                isAbstract = hasAbstractMethods(element),
                isTestCode = isTestCode,
                isLibraryCode = isLibraryCode
            )

            is PyFunction -> DefinitionLocation(
                name = element.name ?: "anonymous",
                filePath = relativePath,
                startOffset = textRange.startOffset,
                endOffset = textRange.endOffset,
                lineNumber = lineNumber + 1,
                type = determineFunctionType(element),
                signature = buildFunctionSignature(element),
                containingClass = getContainingClass(element),
                modifiers = getPythonModifiers(element),
                isAbstract = extractDecorators(element).contains("@abstractmethod"),
                isTestCode = isTestCode,
                isLibraryCode = isLibraryCode
            )

            is PyTargetExpression -> DefinitionLocation(
                name = element.name ?: "anonymous",
                filePath = relativePath,
                startOffset = textRange.startOffset,
                endOffset = textRange.endOffset,
                lineNumber = lineNumber + 1,
                type = determineVariableType(element),
                signature = buildVariableSignature(element),
                containingClass = getContainingClass(element),
                modifiers = getVariableModifiers(element),
                isTestCode = isTestCode,
                isLibraryCode = isLibraryCode
            )

            is PyParameter -> DefinitionLocation(
                name = element.name ?: "anonymous",
                filePath = relativePath,
                startOffset = textRange.startOffset,
                endOffset = textRange.endOffset,
                lineNumber = lineNumber + 1,
                type = "parameter",
                signature = buildParameterSignature(element),
                containingClass = getContainingClass(element),
                modifiers = emptyList(),
                isTestCode = isTestCode,
                isLibraryCode = isLibraryCode
            )

            else -> DefinitionLocation(
                name = getElementName(element),
                filePath = relativePath,
                startOffset = textRange.startOffset,
                endOffset = textRange.endOffset,
                lineNumber = lineNumber + 1,
                type = "unknown",
                signature = element.text?.lines()?.firstOrNull()?.trim(),
                isTestCode = isTestCode,
                isLibraryCode = isLibraryCode
            )
        }
    }
    
    override fun supportsFile(psiFile: PsiFile): Boolean {
        val languageId = psiFile.language.id
        return languageId == "Python" || languageId == "PythonCore"
    }
    
    override fun supportsElement(element: PsiElement): Boolean {
        val languageId = element.language.id
        return languageId == "Python" || languageId == "PythonCore"
    }
    
    override fun getSupportedLanguage(): String {
        return "Python"
    }
    
    /**
     * Find a named Python element at the given position.
     */
    private fun findNamedElementAtPosition(element: PsiElement): PsiElement? {
        // Try different Python element types
        return PsiTreeUtil.getParentOfType(element, PyClass::class.java)
            ?: PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
            ?: PsiTreeUtil.getParentOfType(element, PyTargetExpression::class.java)
            ?: PsiTreeUtil.getParentOfType(element, PyParameter::class.java)
    }
    
    /**
     * Get the name of a Python element.
     */
    private fun getElementName(element: PsiElement): String {
        return when (element) {
            is PyClass -> element.name ?: "anonymous"
            is PyFunction -> element.name ?: "anonymous"
            is PyTargetExpression -> element.name ?: "anonymous"
            is PyParameter -> element.name ?: "anonymous"
            else -> "unknown"
        }
    }
    
    private fun determineClassType(pyClass: PyClass): String {
        val decorators = extractDecorators(pyClass)
        return when {
            decorators.any { it.contains("dataclass", ignoreCase = true) } -> "dataclass"
            decorators.any { it.contains("enum", ignoreCase = true) } -> "enum"
            hasAbstractMethods(pyClass) -> "abstract_class"
            else -> "class"
        }
    }
    
    private fun determineFunctionType(pyFunction: PyFunction): String {
        val decorators = extractDecorators(pyFunction)
        return when {
            pyFunction.name == "__init__" -> "constructor"
            decorators.contains("@property") -> "property"
            decorators.contains("@staticmethod") -> "static_method"
            decorators.contains("@classmethod") -> "class_method"
            decorators.contains("@abstractmethod") -> "abstract_method"
            pyFunction.isAsync -> "async_function"
            pyFunction.isGenerator -> "generator"
            pyFunction.containingClass != null -> "method"
            else -> "function"
        }
    }
    
    private fun determineVariableType(target: PyTargetExpression): String {
        val name = target.name ?: ""
        return when {
            isConstant(name) -> "constant"
            target.parent is PyAssignmentStatement && 
                PsiTreeUtil.getParentOfType(target, PyClass::class.java) != null -> "field"
            target.parent is PyAssignmentStatement -> "variable"
            else -> "variable"
        }
    }
    
    private fun buildClassSignature(pyClass: PyClass): String {
        val decorators = extractDecorators(pyClass).joinToString(" ")
        val baseClasses = pyClass.superClassExpressions.joinToString(", ") { it.text }
        val bases = if (baseClasses.isNotEmpty()) "($baseClasses)" else ""
        
        return "${decorators}class ${pyClass.name}$bases".trim()
    }

    private fun buildFunctionSignature(pyFunction: PyFunction): String {
        val decorators = extractDecorators(pyFunction).joinToString(" ")
        val async = if (pyFunction.isAsync) "async " else ""
        val params = pyFunction.parameterList.parameters.joinToString(", ") { parameter ->
            val defaultValue = parameter.defaultValue?.text?.let { " = $it" } ?: ""
            "${parameter.name}${defaultValue}"
        }
        val returnType = extractReturnTypeHint(pyFunction)?.let { " -> $it" } ?: ""
        
        return "${decorators}${async}def ${pyFunction.name}($params)$returnType".trim()
    }

    private fun buildVariableSignature(target: PyTargetExpression): String {
        val assignedValue = target.findAssignedValue()?.text
        val typeHint = target.annotation?.text?.let { ": $it" } ?: ""
        val value = assignedValue?.let { " = $it" } ?: ""
        
        return "${target.name}$typeHint$value".trim()
    }
    
    private fun buildParameterSignature(parameter: PyParameter): String {
        val defaultValue = parameter.defaultValue?.text?.let { " = $it" } ?: ""
        return "${parameter.name}${defaultValue}".trim()
    }

    private fun getContainingClass(element: PsiElement): String? {
        val containingClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java)
        return containingClass?.qualifiedName ?: containingClass?.name
    }
    
    private fun getVariableModifiers(target: PyTargetExpression): List<String> {
        val modifiers = mutableListOf<String>()
        val name = target.name ?: ""
        
        when (getPythonVisibility(name)) {
            "private" -> modifiers.add("private")
            "protected" -> modifiers.add("protected")
            "public" -> modifiers.add("public")
        }
        
        if (isConstant(name)) {
            modifiers.add("constant")
        }
        
        return modifiers
    }

    private fun calculateConfidence(element: PsiElement, searchTerm: String, isClass: Boolean): Float {
        val elementName = getElementName(element)
        val virtualFile = element.containingFile.virtualFile
        val project = element.project

        return when {
            // Exact name match in project code
            elementName == searchTerm && !isInLibraryCode(virtualFile, project) -> {
                when {
                    isClass && element is PyClass -> 1.0f
                    !isClass && (element is PyFunction || element is PyTargetExpression) -> 0.95f
                    else -> 0.9f
                }
            }
            // Exact name match in library code
            elementName == searchTerm && isInLibraryCode(virtualFile, project) -> 0.5f
            // Case-insensitive match (less common in Python than Java)
            elementName.equals(searchTerm, ignoreCase = true) -> 0.7f
            // Partial match
            elementName.contains(searchTerm, ignoreCase = true) -> 0.3f
            else -> 0.1f
        }
    }

    private fun generateDisambiguationHint(element: PsiElement): String? {
        return when (element) {
            is PyFunction -> {
                val containingClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java)?.name ?: "module"
                val decorators = extractDecorators(element)
                
                when {
                    element.name == "__init__" -> "Constructor in $containingClass"
                    decorators.contains("@staticmethod") -> "Static method in $containingClass"
                    decorators.contains("@classmethod") -> "Class method in $containingClass"
                    decorators.contains("@property") -> "Property in $containingClass"
                    decorators.contains("@abstractmethod") -> "Abstract method in $containingClass"
                    element.isAsync -> "Async method in $containingClass"
                    element.isGenerator -> "Generator method in $containingClass"
                    containingClass != "module" -> "Method in $containingClass"
                    else -> "Function in module"
                }
            }

            is PyTargetExpression -> {
                val containingClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java)?.name
                val name = element.name ?: ""
                
                when {
                    containingClass != null && isConstant(name) -> "Constant in $containingClass"
                    containingClass != null -> "Attribute in $containingClass" 
                    isConstant(name) -> "Module constant"
                    else -> "Module variable"
                }
            }

            is PyClass -> {
                val packageName = element.containingFile.name.removeSuffix(".py")
                val decorators = extractDecorators(element)
                
                when {
                    decorators.any { it.contains("dataclass", ignoreCase = true) } -> "Dataclass in $packageName"
                    decorators.any { it.contains("enum", ignoreCase = true) } -> "Enum in $packageName"
                    hasAbstractMethods(element) -> "Abstract class in $packageName"
                    else -> "Class in $packageName"
                }
            }

            is PyParameter -> "Parameter"

            else -> null
        }
    }
}
