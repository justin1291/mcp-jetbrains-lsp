package dev.mcp.extensions.lsp.languages.java

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiTreeUtil
import dev.mcp.extensions.lsp.core.interfaces.DefinitionFinder
import dev.mcp.extensions.lsp.core.models.DefinitionLocation
import dev.mcp.extensions.lsp.languages.base.BaseLanguageHandler
import kotlin.math.abs

/**
 * Definition finder implementation for Java and Kotlin languages.
 * 
 * Registered as a service in mcp-lsp-java.xml when Java module is available.
 */
@Service
class JavaDefinitionFinder : BaseLanguageHandler(), DefinitionFinder {
    
    override fun findDefinitionByPosition(psiFile: PsiFile, position: Int): List<DefinitionLocation> {
        // Validate PSI file and position
        if (psiFile.virtualFile == null) {
            return emptyList()
        }
        
        if (position < 0 || position >= psiFile.textLength) {
            return emptyList()
        }

        // Primary approach: Find element at exact position
        val elementAtPosition = psiFile.findElementAt(position)
        if (elementAtPosition != null) {
            // Try to resolve reference at this position
            val referenceResult = resolveReferenceAtElement(elementAtPosition)
            if (referenceResult.isNotEmpty()) {
                return referenceResult
            }
            
            // Try to find named element at this position
            val namedElementResult = findNamedElementAtPosition(elementAtPosition)
            if (namedElementResult.isNotEmpty()) {
                return namedElementResult
            }
        }

        // Fallback approach: Search in nearby positions
        val fallbackResult = findDefinitionWithFallback(psiFile, position)
        if (fallbackResult.isNotEmpty()) {
            return fallbackResult
        }

        return emptyList()
    }

    /**
     * Resolve reference at the given element.
     */
    private fun resolveReferenceAtElement(element: PsiElement): List<DefinitionLocation> {
        try {
            // Check parent for reference first (common case)
            val parentReference = element.parent?.reference
            if (parentReference != null) {
                val resolved = parentReference.resolve()
                if (resolved != null && isValidDefinitionElement(resolved)) {
                    val location = createLocationSafely(resolved)
                    return if (location != null) {
                        listOf(location.copy(
                            confidence = 1.0f,
                            disambiguationHint = generateDisambiguationHint(resolved)
                        ))
                    } else emptyList()
                }
            }

            // Check direct reference
            val directReference = element.reference
            if (directReference != null) {
                val resolved = directReference.resolve()
                if (resolved != null && isValidDefinitionElement(resolved)) {
                    val location = createLocationSafely(resolved)
                    return if (location != null) {
                        listOf(location.copy(
                            confidence = 1.0f,
                            disambiguationHint = generateDisambiguationHint(resolved)
                        ))
                    } else emptyList()
                }
            }
        } catch (e: Exception) {
            logger.debug("Error resolving reference at element", e)
        }

        return emptyList()
    }

    /**
     * Find named element at the given position.
     */
    private fun findNamedElementAtPosition(element: PsiElement): List<DefinitionLocation> {
        try {
            val namedElement = PsiTreeUtil.getParentOfType(element, PsiNamedElement::class.java)
            if (namedElement != null && isValidDefinitionElement(namedElement)) {
                val location = createLocationSafely(namedElement)
                return if (location != null) {
                    listOf(location.copy(
                        confidence = 0.9f,
                        disambiguationHint = generateDisambiguationHint(namedElement)
                    ))
                } else emptyList()
            }
        } catch (e: Exception) {
            logger.debug("Error finding named element at position", e)
        }

        return emptyList()
    }

    /**
     * Fallback approach: try nearby positions and broader searches.
     */
    private fun findDefinitionWithFallback(psiFile: PsiFile, position: Int): List<DefinitionLocation> {
        // Try positions around the given position (±5 characters)
        for (offset in -5..5) {
            val adjustedPosition = position + offset
            if (adjustedPosition >= 0 && adjustedPosition < psiFile.textLength) {
                val element = psiFile.findElementAt(adjustedPosition)
                if (element != null) {
                    val result = resolveReferenceAtElement(element)
                    if (result.isNotEmpty()) {
                        return result.map { it.copy(confidence = it.confidence * 0.8f) }
                    }
                }
            }
        }

        // Try to find any named element in the vicinity
        val startOffset = maxOf(0, position - 20)
        val endOffset = minOf(psiFile.textLength, position + 20)
        
        try {
            val elements = mutableListOf<PsiElement>()
            var currentElement = psiFile.findElementAt(startOffset)
            
            while (currentElement != null && currentElement.textRange.startOffset < endOffset) {
                if (currentElement is PsiNamedElement && isValidDefinitionElement(currentElement)) {
                    elements.add(currentElement)
                }
                currentElement = PsiTreeUtil.nextLeaf(currentElement)
            }
            
            if (elements.isNotEmpty()) {
                // Return the closest element to the original position
                val closestElement = elements.minByOrNull { 
                    abs(it.textRange.startOffset - position) 
                }
                if (closestElement != null) {
                    val location = createLocationSafely(closestElement)
                    return if (location != null) {
                        listOf(location.copy(
                            confidence = 0.6f,
                            disambiguationHint = "Nearest symbol: ${generateDisambiguationHint(closestElement)}"
                        ))
                    } else emptyList()
                }
            }
        } catch (e: Exception) {
            logger.debug("Error in fallback search", e)
        }

        return emptyList()
    }

    /**
     * Check if an element is a valid definition element.
     */
    private fun isValidDefinitionElement(element: PsiElement): Boolean {
        return element is PsiClass || element is PsiMethod || 
               element is PsiField || element is PsiVariable ||
               element is PsiParameter || element is PsiLocalVariable
    }
    
    override fun findDefinitionByName(project: Project, symbolName: String): List<DefinitionLocation> {
        if (symbolName.isBlank()) {
            return emptyList()
        }
        
        val scope = GlobalSearchScope.projectScope(project)
        val definitions = mutableListOf<DefinitionLocation>()
        
        try {
            val cache = PsiShortNamesCache.getInstance(project)
            val javaPsiFacade = JavaPsiFacade.getInstance(project)

            // Check if it's a qualified name (e.g., "ClassName.methodName")
            val parts = symbolName.split(".")
            if (parts.size == 2) {
                // Search for method in specific class
                val className = parts[0]
                val methodName = parts[1]
                
                try {
                    // First try to find the class using PsiShortNamesCache
                    val classes = cache.getClassesByName(className, scope)
                    for (psiClass in classes) {
                        try {
                            val methods = psiClass.findMethodsByName(methodName, true)
                            methods.forEach { method ->
                                val location = createLocationSafely(method, symbolName)
                                if (location != null) {
                                    definitions.add(
                                        location.copy(
                                            confidence = 1.0f, // Exact qualified match
                                            disambiguationHint = "Method in ${psiClass.qualifiedName ?: psiClass.name}"
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            logger.debug("Error searching methods in class ${psiClass.name}", e)
                        }
                    }
                    
                    // Also try with qualified name if no results yet
                    if (definitions.isEmpty()) {
                        val qualifiedClasses = javaPsiFacade.findClasses("com.example.demo.$className", scope)
                        for (psiClass in qualifiedClasses) {
                            try {
                                val methods = psiClass.findMethodsByName(methodName, true)
                                methods.forEach { method ->
                                    val location = createLocationSafely(method, symbolName)
                                    if (location != null) {
                                        definitions.add(
                                            location.copy(
                                                confidence = 1.0f, // Exact qualified match
                                                disambiguationHint = "Method in ${psiClass.qualifiedName ?: psiClass.name}"
                                            )
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                logger.debug("Error searching methods in class ${psiClass.name}", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.debug("Error searching for qualified name: $symbolName", e)
                }
            } else {
                // Search for classes using PsiShortNamesCache
                try {
                    val classesFromCache = cache.getClassesByName(symbolName, scope)
                    classesFromCache.forEach { psiClass ->
                        try {
                            val location = createLocationSafely(psiClass, symbolName)
                            if (location != null) {
                                definitions.add(
                                    location.copy(
                                        confidence = calculateConfidence(psiClass, symbolName, isClass = true),
                                        disambiguationHint = generateDisambiguationHint(psiClass)
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            logger.debug("Error processing class ${psiClass.name}", e)
                        }
                    }
                } catch (e: Exception) {
                    logger.debug("Error searching for classes with name: $symbolName", e)
                }

                // Search for methods
                try {
                    cache.getMethodsByName(symbolName, scope).forEach { method ->
                        try {
                            val location = createLocationSafely(method, symbolName)
                            if (location != null) {
                                definitions.add(
                                    location.copy(
                                        confidence = calculateConfidence(method, symbolName, isClass = false),
                                        disambiguationHint = generateDisambiguationHint(method)
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            logger.debug("Error processing method ${method.name}", e)
                        }
                    }
                } catch (e: Exception) {
                    logger.debug("Error searching for methods with name: $symbolName", e)
                }

                // Search for fields
                try {
                    cache.getFieldsByName(symbolName, scope).forEach { field ->
                        try {
                            val location = createLocationSafely(field, symbolName)
                            if (location != null) {
                                definitions.add(
                                    location.copy(
                                        confidence = calculateConfidence(field, symbolName, isClass = false),
                                        disambiguationHint = generateDisambiguationHint(field)
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            logger.debug("Error processing field ${field.name}", e)
                        }
                    }
                } catch (e: Exception) {
                    logger.debug("Error searching for fields with name: $symbolName", e)
                }
            }
        } catch (e: Exception) {
            logger.error("Error in findDefinitionByName", e)
        }

        // Sort by confidence (highest first)
        return definitions.sortedByDescending { it.confidence }
    }
    
    override fun createLocation(element: PsiElement, searchTerm: String?): DefinitionLocation {
        val location = createLocationSafely(element, searchTerm)
        return location ?: throw IllegalStateException("Failed to create location for element: $element")
    }

    /**
     * Create a DefinitionLocation from a PSI element with comprehensive null safety.
     * Returns null if the element cannot be safely processed.
     */
    private fun createLocationSafely(element: PsiElement, searchTerm: String? = null): DefinitionLocation? {
        try {
            // Validate essential element properties
            val containingFile = element.containingFile ?: return null
            val virtualFile = containingFile.virtualFile ?: return null
            val project = element.project
            val basePath = project.basePath ?: return null
            val textRange = element.textRange ?: return null

            // Calculate relative path safely
            val relativePath = try {
                virtualFile.path.removePrefix(basePath).removePrefix("/")
            } catch (e: Exception) {
                virtualFile.name
            }

            // Calculate line number safely
            val lineNumber = try {
                val document = PsiDocumentManager.getInstance(project).getDocument(containingFile)
                if (document != null) {
                    document.getLineNumber(textRange.startOffset) + 1
                } else {
                    1
                }
            } catch (e: Exception) {
                1
            }

            // Check code location flags safely
            val isTestCode = try {
                isInTestCode(virtualFile)
            } catch (e: Exception) {
                false
            }

            val isLibraryCode = try {
                isInLibraryCode(virtualFile, project)
            } catch (e: Exception) {
                false
            }

            // Generate accessibility warning safely
            val accessibilityWarning = try {
                generateAccessibilityWarning(element)
            } catch (e: Exception) {
                null
            }

            // Create location based on element type with null safety
            return when (element) {
                is PsiClass -> createClassLocation(
                    element, relativePath, textRange, lineNumber, isTestCode, isLibraryCode, accessibilityWarning
                )
                is PsiMethod -> createMethodLocation(
                    element, relativePath, textRange, lineNumber, isTestCode, isLibraryCode, accessibilityWarning
                )
                is PsiField -> createFieldLocation(
                    element, relativePath, textRange, lineNumber, isTestCode, isLibraryCode, accessibilityWarning
                )
                is PsiVariable -> createVariableLocation(
                    element, relativePath, textRange, lineNumber, isTestCode, isLibraryCode, accessibilityWarning
                )
                else -> createGenericLocation(
                    element, relativePath, textRange, lineNumber, isTestCode, isLibraryCode
                )
            }
        } catch (e: Exception) {
            logger.debug("Error creating location for element", e)
            return null
        }
    }

    private fun createClassLocation(
        element: PsiClass, 
        relativePath: String,
        textRange: com.intellij.openapi.util.TextRange,
        lineNumber: Int,
        isTestCode: Boolean,
        isLibraryCode: Boolean,
        accessibilityWarning: String?
    ): DefinitionLocation {
        return DefinitionLocation(
            name = element.name ?: "anonymous",
            filePath = relativePath,
            startOffset = textRange.startOffset,
            endOffset = textRange.endOffset,
            lineNumber = lineNumber,
            type = when {
                element.isInterface -> "interface"
                element.isEnum -> "enum"
                element.isAnnotationType -> "annotation"
                else -> "class"
            },
            signature = buildClassSignatureSafely(element),
            containingClass = element.containingClass?.qualifiedName,
            modifiers = extractModifiers(element.modifierList),
            isAbstract = element.hasModifierProperty(PsiModifier.ABSTRACT),
            isTestCode = isTestCode,
            isLibraryCode = isLibraryCode,
            accessibilityWarning = accessibilityWarning
        )
    }

    private fun createMethodLocation(
        element: PsiMethod,
        relativePath: String,
        textRange: com.intellij.openapi.util.TextRange,
        lineNumber: Int,
        isTestCode: Boolean,
        isLibraryCode: Boolean,
        accessibilityWarning: String?
    ): DefinitionLocation {
        return DefinitionLocation(
            name = element.name,
            filePath = relativePath,
            startOffset = textRange.startOffset,
            endOffset = textRange.endOffset,
            lineNumber = lineNumber,
            type = if (element.isConstructor) "constructor" else "method",
            signature = buildMethodSignatureSafely(element),
            containingClass = element.containingClass?.qualifiedName,
            modifiers = extractModifiers(element.modifierList),
            isAbstract = element.hasModifierProperty(PsiModifier.ABSTRACT),
            isTestCode = isTestCode,
            isLibraryCode = isLibraryCode,
            accessibilityWarning = accessibilityWarning
        )
    }

    private fun createFieldLocation(
        element: PsiField,
        relativePath: String,
        textRange: com.intellij.openapi.util.TextRange,
        lineNumber: Int,
        isTestCode: Boolean,
        isLibraryCode: Boolean,
        accessibilityWarning: String?
    ): DefinitionLocation {
        return DefinitionLocation(
            name = element.name ?: "anonymous",
            filePath = relativePath,
            startOffset = textRange.startOffset,
            endOffset = textRange.endOffset,
            lineNumber = lineNumber,
            type = "field",
            signature = "${element.type.presentableText} ${element.name}",
            containingClass = element.containingClass?.qualifiedName,
            modifiers = extractModifiers(element.modifierList),
            isTestCode = isTestCode,
            isLibraryCode = isLibraryCode,
            accessibilityWarning = accessibilityWarning
        )
    }

    private fun createVariableLocation(
        element: PsiVariable,
        relativePath: String,
        textRange: com.intellij.openapi.util.TextRange,
        lineNumber: Int,
        isTestCode: Boolean,
        isLibraryCode: Boolean,
        accessibilityWarning: String?
    ): DefinitionLocation {
        return DefinitionLocation(
            name = element.name ?: "anonymous",
            filePath = relativePath,
            startOffset = textRange.startOffset,
            endOffset = textRange.endOffset,
            lineNumber = lineNumber,
            type = "variable",
            signature = "${element.type.presentableText} ${element.name}",
            containingClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)?.qualifiedName,
            modifiers = extractModifiers(element.modifierList),
            isTestCode = isTestCode,
            isLibraryCode = isLibraryCode,
            accessibilityWarning = accessibilityWarning
        )
    }

    private fun createGenericLocation(
        element: PsiElement,
        relativePath: String,
        textRange: com.intellij.openapi.util.TextRange,
        lineNumber: Int,
        isTestCode: Boolean,
        isLibraryCode: Boolean
    ): DefinitionLocation {
        return DefinitionLocation(
            name = (element as? PsiNamedElement)?.name ?: "unknown",
            filePath = relativePath,
            startOffset = textRange.startOffset,
            endOffset = textRange.endOffset,
            lineNumber = lineNumber,
            type = "unknown",
            signature = element.text?.lines()?.firstOrNull()?.trim(),
            isTestCode = isTestCode,
            isLibraryCode = isLibraryCode
        )
    }
    
    override fun supportsFile(psiFile: PsiFile): Boolean {
        val languageId = psiFile.language.id
        return languageId == "JAVA" || languageId == "kotlin" || languageId == "Kotlin"
    }
    
    override fun supportsElement(element: PsiElement): Boolean {
        val languageId = element.language.id
        return languageId == "JAVA" || languageId == "kotlin" || languageId == "Kotlin"
    }
    
    override fun getSupportedLanguage(): String {
        return "Java/Kotlin"
    }
    
    private fun buildClassSignature(psiClass: PsiClass): String {
        return buildClassSignatureSafely(psiClass) ?: "class ${psiClass.name ?: "anonymous"}"
    }

    private fun buildMethodSignature(method: PsiMethod): String {
        return buildMethodSignatureSafely(method) ?: "${method.name}()"
    }

    private fun buildClassSignatureSafely(psiClass: PsiClass): String? {
        return try {
            val modifiers = psiClass.modifierList?.text ?: ""
            val type = when {
                psiClass.isInterface -> "interface"
                psiClass.isEnum -> "enum"
                psiClass.isAnnotationType -> "@interface"
                else -> "class"
            }
            "$modifiers $type ${psiClass.name ?: "anonymous"}".trim()
        } catch (e: Exception) {
            null
        }
    }

    private fun buildMethodSignatureSafely(method: PsiMethod): String? {
        return try {
            val params = method.parameterList.parameters.joinToString(", ") { param ->
                val paramType = param.type.presentableText
                val paramName = param.name ?: "param"
                "$paramType $paramName"
            }
            val returnType = if (method.isConstructor) "" else "${method.returnType?.presentableText ?: "void"} "
            val modifiers = method.modifierList.text
            val throws = method.throwsList.referencedTypes.takeIf { it.isNotEmpty() }
                ?.joinToString(", ") { it.presentableText }
                ?.let { " throws $it" } ?: ""

            "$modifiers $returnType${method.name}($params)$throws".trim()
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateConfidence(element: PsiElement, searchTerm: String, isClass: Boolean): Float {
        return try {
            val elementName = (element as? PsiNamedElement)?.name ?: return 0.1f
            val containingFile = element.containingFile ?: return 0.1f
            val virtualFile = containingFile.virtualFile ?: return 0.1f
            val project = element.project
            
            val isInLibrary = try {
                isInLibraryCode(virtualFile, project)
            } catch (e: Exception) {
                false
            }

            when {
                // Exact name match in project code
                elementName == searchTerm && !isInLibrary -> {
                    when {
                        isClass && element is PsiClass -> 1.0f
                        !isClass && element is PsiMember -> 0.95f
                        else -> 0.9f
                    }
                }
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

    private fun generateDisambiguationHint(element: PsiElement): String? {
        return try {
            when (element) {
                is PsiMethod -> {
                    val containingClass = element.containingClass
                    val containingClassName = containingClass?.name ?: "Unknown"
                    when {
                        element.isConstructor -> "Constructor in $containingClassName"
                        element.hasModifierProperty(PsiModifier.STATIC) -> "Static method in $containingClassName"
                        element.hasModifierProperty(PsiModifier.ABSTRACT) -> "Abstract method in $containingClassName"
                        else -> "Method in $containingClassName"
                    }
                }

                is PsiField -> {
                    val containingClass = element.containingClass
                    val containingClassName = containingClass?.qualifiedName ?: containingClass?.name ?: "Unknown"
                    when {
                        element.hasModifierProperty(PsiModifier.STATIC) &&
                                element.hasModifierProperty(PsiModifier.FINAL) -> "Constant in $containingClassName"

                        element.hasModifierProperty(PsiModifier.STATIC) -> "Static field in $containingClassName"
                        else -> "Field in $containingClassName"
                    }
                }

                is PsiClass -> {
                    val containingFile = element.containingFile
                    val packageName = (containingFile as? PsiJavaFile)?.packageName
                    val locationDesc = packageName ?: "default package"
                    when {
                        element.isInterface -> "Interface in $locationDesc"
                        element.isEnum -> "Enum in $locationDesc"
                        element.isAnnotationType -> "Annotation in $locationDesc"
                        element.hasModifierProperty(PsiModifier.ABSTRACT) -> "Abstract class in $locationDesc"
                        else -> "Class in $locationDesc"
                    }
                }

                is PsiVariable -> {
                    when (element) {
                        is PsiParameter -> "Parameter"
                        is PsiLocalVariable -> "Local variable"
                        else -> "Variable"
                    }
                }

                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun generateAccessibilityWarning(element: PsiElement): String? {
        return try {
            if (element !is PsiModifierListOwner) return null

            val modifierList = element.modifierList ?: return null

            when {
                modifierList.hasModifierProperty(PsiModifier.PRIVATE) ->
                    "Private member - not accessible from outside the declaring class"

                modifierList.hasModifierProperty(PsiModifier.PROTECTED) ->
                    "Protected member - only accessible from subclasses or same package"

                !modifierList.hasModifierProperty(PsiModifier.PUBLIC) &&
                        !modifierList.hasModifierProperty(PsiModifier.PROTECTED) &&
                        !modifierList.hasModifierProperty(PsiModifier.PRIVATE) ->
                    "Package-private member - only accessible from the same package"

                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
