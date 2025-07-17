package dev.mcp.extensions.lsp.languages.javascript

import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeListOwner
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import dev.mcp.extensions.lsp.core.interfaces.DefinitionFinder
import dev.mcp.extensions.lsp.core.models.DefinitionLocation
import dev.mcp.extensions.lsp.languages.base.BaseLanguageHandler

/**
 * Definition finder implementation for JavaScript and TypeScript languages.
 *
 * Handles navigation to definitions for:
 * - Functions and methods
 * - Classes and constructors
 * - Variables and constants
 * - React components and hooks
 */
class JavaScriptDefinitionFinder : BaseLanguageHandler(), DefinitionFinder {

    override fun findDefinitionByPosition(psiFile: PsiFile, position: Int): List<DefinitionLocation> {
        // Validate PSI file and position
        if (psiFile.virtualFile == null) {
            return emptyList()
        }

        if (position < 0 || position >= psiFile.textLength) {
            return emptyList()
        }

        val element = psiFile.findElementAt(position) ?: return emptyList()

        // Priority 1: Handle React components and hooks with higher priority
        val reactResult = resolveReactDefinition(element)
        if (reactResult.isNotEmpty()) {
            return reactResult
        }

        // Priority 2: Standard reference resolution
        val reference = findReference(element)
        if (reference != null) {
            val resolved = reference.resolve()
            if (resolved != null) {
                // Filter out CSS classes when JavaScript symbols are available
                if (isJavaScriptSymbol(resolved)) {
                    val location = createLocationSafely(resolved, element.text)
                    return if (location != null) listOf(location) else emptyList()
                }
            }
        }

        // Priority 3: Fallback to element-based resolution
        return findDefinitionByElement(element)
    }

    /**
     * Resolve React components, hooks, and higher-order functions
     */
    private fun resolveReactDefinition(element: PsiElement): List<DefinitionLocation> {
        try {
            // Handle React hooks
            val hookResult = resolveReactHook(element)
            if (hookResult.isNotEmpty()) {
                return hookResult
            }

            // Handle React components
            val componentResult = resolveReactComponent(element)
            if (componentResult.isNotEmpty()) {
                return componentResult
            }

            // Handle higher-order components and callback functions
            val hocResult = resolveHigherOrderFunction(element)
            if (hocResult.isNotEmpty()) {
                return hocResult
            }

            // Handle JSX elements
            val jsxResult = resolveJSXElement(element)
            if (jsxResult.isNotEmpty()) {
                return jsxResult
            }
        } catch (e: Exception) {
            logger.debug("Error resolving React definition", e)
        }

        return emptyList()
    }

    private fun resolveReactHook(element: PsiElement): List<DefinitionLocation> {
        try {
            // Check if element is a React hook call
            val callExpression = PsiTreeUtil.getParentOfType(element, JSCallExpression::class.java)
            if (callExpression != null) {
                val methodExpression = callExpression.methodExpression
                if (methodExpression is JSReferenceExpression) {
                    val referencedName = methodExpression.referencedName
                    if (referencedName != null && isReactHookName(referencedName)) {
                        // Try to resolve the hook definition
                        val reference = methodExpression.reference
                        if (reference != null) {
                            val resolved = reference.resolve()
                            if (resolved != null) {
                                val location = createLocationSafely(resolved, referencedName)
                                return if (location != null) {
                                    listOf(location.copy(
                                        confidence = 1.0f,
                                        disambiguationHint = "React Hook"
                                    ))
                                } else emptyList()
                            }
                        }
                    }
                }
            }

            // Check if element is a custom hook definition
            val function = PsiTreeUtil.getParentOfType(element, JSFunction::class.java)
            if (function != null && function.name != null && isReactHookName(function.name!!)) {
                val location = createLocationSafely(function, function.name)
                return if (location != null) {
                    listOf(location.copy(
                        confidence = 1.0f,
                        disambiguationHint = "Custom React Hook"
                    ))
                } else emptyList()
            }
        } catch (e: Exception) {
            logger.debug("Error resolving React hook", e)
        }

        return emptyList()
    }

    private fun resolveReactComponent(element: PsiElement): List<DefinitionLocation> {
        try {
            // Check if element is a React component reference
            val reference = element.reference
            if (reference != null) {
                val resolved = reference.resolve()
                if (resolved != null && isReactComponentElement(resolved)) {
                    val location = createLocationSafely(resolved, element.text)
                    return if (location != null) {
                        listOf(location.copy(
                            confidence = 1.0f,
                            disambiguationHint = "React Component"
                        ))
                    } else emptyList()
                }
            }

            // Check if element is part of a component definition
            val componentElement = findComponentDefinition(element)
            if (componentElement != null) {
                val location = createLocationSafely(componentElement, element.text)
                return if (location != null) {
                    listOf(location.copy(
                        confidence = 1.0f,
                        disambiguationHint = "React Component Definition"
                    ))
                } else emptyList()
            }
        } catch (e: Exception) {
            logger.debug("Error resolving React component", e)
        }

        return emptyList()
    }

    private fun resolveHigherOrderFunction(element: PsiElement): List<DefinitionLocation> {
        try {
            // Check if element is part of a higher-order function pattern
            val callExpression = PsiTreeUtil.getParentOfType(element, JSCallExpression::class.java)
            if (callExpression != null) {
                val methodExpression = callExpression.methodExpression

                // Handle HOC patterns like withRouter(Component)
                if (methodExpression is JSReferenceExpression) {
                    val referencedName = methodExpression.referencedName
                    if (referencedName != null && isHigherOrderFunctionName(referencedName)) {
                        val reference = methodExpression.reference
                        if (reference != null) {
                            val resolved = reference.resolve()
                            if (resolved != null) {
                                val location = createLocationSafely(resolved, referencedName)
                                return if (location != null) {
                                    listOf(location.copy(
                                        confidence = 1.0f,
                                        disambiguationHint = "Higher-Order Function"
                                    ))
                                } else emptyList()
                            }
                        }
                    }
                }

                // Handle callback functions in arguments
                val arguments = callExpression.arguments
                for (arg in arguments) {
                    if (arg.textRange.contains(element.textRange)) {
                        if (arg is JSFunction) {
                            val location = createLocationSafely(arg, "callback")
                            return if (location != null) {
                                listOf(location.copy(
                                    confidence = 0.9f,
                                    disambiguationHint = "Callback Function"
                                ))
                            } else emptyList()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug("Error resolving higher-order function", e)
        }

        return emptyList()
    }

    private fun resolveJSXElement(element: PsiElement): List<DefinitionLocation> {
        try {
            // Check if element is a JSX tag using a more generic approach
            val parent = element.parent
            if (parent != null && parent.javaClass.simpleName.contains("JSX")) {
                // Try to get the tag name from the parent element
                val tagName = parent.text?.split("<", ">", " ")?.getOrNull(1)?.trim()
                if (tagName != null && isReactComponentName(tagName)) {
                    // Try to resolve the component definition
                    val reference = parent.reference
                    if (reference != null) {
                        val resolved = reference.resolve()
                        if (resolved != null) {
                            val location = createLocationSafely(resolved, tagName)
                            return if (location != null) {
                                listOf(location.copy(
                                    confidence = 1.0f,
                                    disambiguationHint = "JSX Component"
                                ))
                            } else emptyList()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug("Error resolving JSX element", e)
        }

        return emptyList()
    }

    private fun isJavaScriptSymbol(element: PsiElement): Boolean {
        return element is JSFunction ||
               element is JSClass ||
               element is JSVariable ||
               element is JSField ||
               element is JSProperty
    }

    private fun isReactHookName(name: String): Boolean {
        return name.startsWith("use") && name.length > 3 && name[3].isUpperCase()
    }

    private fun isReactComponentName(name: String): Boolean {
        return name.isNotEmpty() && name[0].isUpperCase()
    }

    private fun isReactComponentElement(element: PsiElement): Boolean {
        return when (element) {
            is JSFunction -> {
                val name = element.name
                name != null && isReactComponentName(name)
            }
            is JSClass -> {
                val name = element.name
                name != null && isReactComponentName(name)
            }
            is JSVariable -> {
                val name = element.name
                name != null && isReactComponentName(name) &&
                element.initializer is JSFunction
            }
            else -> false
        }
    }

    private fun findComponentDefinition(element: PsiElement): PsiElement? {
        // Look for React component patterns
        val function = PsiTreeUtil.getParentOfType(element, JSFunction::class.java)
        if (function != null && function.name != null && isReactComponentName(function.name!!)) {
            return function
        }

        val variable = PsiTreeUtil.getParentOfType(element, JSVariable::class.java)
        if (variable != null && variable.name != null && isReactComponentName(variable.name!!)) {
            return variable
        }

        val jsClass = PsiTreeUtil.getParentOfType(element, JSClass::class.java)
        if (jsClass != null && jsClass.name != null && isReactComponentName(jsClass.name!!)) {
            return jsClass
        }

        return null
    }

    private fun isHigherOrderFunctionName(name: String): Boolean {
        return name.startsWith("with") || // withRouter, withStyles
               name.startsWith("connect") || // Redux connect
               name.startsWith("memo") || // React.memo
               name.startsWith("forwardRef") || // React.forwardRef
               name.startsWith("enhance") || // General HOC pattern
               name.endsWith("HOC") || // Explicit HOC naming
               name.endsWith("Hoc")
    }

    override fun findDefinitionByName(project: Project, symbolName: String): List<DefinitionLocation> {
        val definitions = mutableListOf<DefinitionLocation>()

        // Enhanced implementation with prioritization
        try {
            // Priority 1: Look for React components and hooks first
            if (isReactComponentName(symbolName)) {
                val reactDefinitions = searchReactComponents(project, symbolName)
                definitions.addAll(reactDefinitions)
            }

            if (isReactHookName(symbolName)) {
                val hookDefinitions = searchReactHooks(project, symbolName)
                definitions.addAll(hookDefinitions)
            }

            // Priority 2: Look for regular JavaScript symbols
            val jsDefinitions = searchJavaScriptSymbols(project, symbolName)
            definitions.addAll(jsDefinitions)

            // Sort by confidence and prioritize JavaScript symbols over CSS
            definitions.sortByDescending { it.confidence }

        } catch (e: Exception) {
            logger.error("Error finding definitions by name", e)
        }

        return definitions
    }

    private fun searchReactComponents(project: Project, symbolName: String): List<DefinitionLocation> {
        // Implementation would use PsiSearchHelper to find React components
        return emptyList()
    }

    private fun searchReactHooks(project: Project, symbolName: String): List<DefinitionLocation> {
        // Implementation would use PsiSearchHelper to find React hooks
        return emptyList()
    }

    private fun searchJavaScriptSymbols(project: Project, symbolName: String): List<DefinitionLocation> {
        // Implementation would use PsiSearchHelper to find JavaScript symbols
        return emptyList()
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

            // Get text range safely
            val textRange = element.textRange ?: return null
            val startOffset = textRange.startOffset
            val endOffset = textRange.endOffset

            // Calculate relative path safely
            val relativePath = try {
                virtualFile.path.removePrefix(basePath).removePrefix("/")
            } catch (e: Exception) {
                virtualFile.name
            }

            // Calculate line number safely
            val lineNumber = try {
                val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
                    .getDocument(virtualFile)
                document?.getLineNumber(startOffset)?.plus(1) ?: 1
            } catch (e: Exception) {
                1
            }

            // Calculate confidence based on location and symbol type
            val confidence = calculateSymbolConfidence(element)

            // Create symbol-specific disambiguation hint
            val disambiguationHint = createDisambiguationHint(element)

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
                getAccessibilityWarning(element)
            } catch (e: Exception) {
                null
            }

            return DefinitionLocation(
                name = getElementName(element),
                filePath = relativePath,
                startOffset = startOffset,
                endOffset = endOffset,
                lineNumber = lineNumber,
                type = getSymbolKind(element),
                signature = getSignature(element),
                containingClass = getContainingClass(element),
                modifiers = getModifiers(element),
                isAbstract = isAbstract(element),
                confidence = confidence,
                disambiguationHint = disambiguationHint,
                isTestCode = isTestCode,
                isLibraryCode = isLibraryCode,
                accessibilityWarning = accessibilityWarning
            )
        } catch (e: Exception) {
            logger.debug("Error creating location for element", e)
            return null
        }
    }

    private fun calculateSymbolConfidence(element: PsiElement): Float {
        val baseConfidence = when {
            isInLibraryCode(element.containingFile.virtualFile, element.project) -> 0.5f
            isInTestCode(element.containingFile.virtualFile) -> 0.95f
            else -> 1.0f // Project code
        }

        // Boost confidence for React-specific symbols
        val reactBoost = when {
            isReactComponentElement(element) -> 0.1f
            isReactHookElement(element) -> 0.1f
            else -> 0.0f
        }

        // Boost confidence for JavaScript symbols over CSS
        val jsBoost = if (isJavaScriptSymbol(element)) 0.1f else 0.0f

        return minOf(1.0f, baseConfidence + reactBoost + jsBoost)
    }

    private fun createDisambiguationHint(element: PsiElement): String? {
        return when (element) {
            is JSFunction -> {
                val container = PsiTreeUtil.getParentOfType(element, JSClass::class.java)
                when {
                    element.isConstructor -> "Constructor in ${container?.name ?: "global"}"
                    element.isGetProperty -> "Getter in ${container?.name ?: "global"}"
                    element.isSetProperty -> "Setter in ${container?.name ?: "global"}"
                    isReactComponentElement(element) -> "React Component Function"
                    isReactHookElement(element) -> "React Hook"
                    element.isAsync -> "Async Function"
                    element.isGenerator -> "Generator Function"
                    container != null -> "Method in ${container.name}"
                    else -> "Function"
                }
            }

            is JSClass -> {
                when {
                    isReactComponentElement(element) -> "React Component Class"
                    else -> "Class"
                }
            }

            is JSVariable -> {
                when {
                    element.isConst -> "Constant"
                    isReactHookElement(element) -> "React Hook Variable"
                    isReactComponentElement(element) -> "React Component Variable"
                    else -> "Variable"
                }
            }

            is JSProperty -> "Object Property"
            is JSField -> "Field"
            else -> element.javaClass.simpleName
        }
    }

    private fun isReactHookElement(element: PsiElement): Boolean {
        val name = when (element) {
            is JSFunction -> element.name
            is JSVariable -> element.name
            else -> null
        }
        return name != null && isReactHookName(name)
    }

    override fun supportsFile(psiFile: PsiFile): Boolean {
        val languageId = psiFile.language.id
        return languageId in setOf("JavaScript", "TypeScript", "JSX", "TSX", "ECMAScript 6")
    }

    override fun supportsElement(element: PsiElement): Boolean {
        return element is JSFunction ||
                element is JSClass ||
                element is JSVariable ||
                element is JSField ||
                element is JSProperty
    }

    override fun getSupportedLanguage(): String {
        return "JavaScript/TypeScript"
    }

    private fun findReference(element: PsiElement): PsiReference? {
        // Try to get reference from the element or its parent
        var current: PsiElement? = element
        while (current != null && current !is PsiFile) {
            val reference = current.reference
            if (reference != null) {
                return reference
            }

            // Special handling for JSReferenceExpression
            if (current is JSReferenceExpression) {
                return current.reference
            }

            current = current.parent
        }
        return null
    }

    private fun findDefinitionByElement(element: PsiElement): List<DefinitionLocation> {
        // Handle special cases where we're already at a definition
        val definitionElement = when (val parent = element.parent) {
            is JSFunction -> parent
            is JSClass -> parent
            is JSVariable -> parent
            else -> null
        }

        return if (definitionElement != null) {
            val location = createLocationSafely(definitionElement, null)
            if (location != null) listOf(location) else emptyList()
        } else {
            emptyList()
        }
    }

    private fun getElementName(element: PsiElement): String {
        return when (element) {
            is JSFunction -> element.name ?: "anonymous"
            is JSClass -> element.name ?: "anonymous"
            is JSVariable -> element.name ?: "anonymous"
            is JSNamedElement -> element.name ?: "anonymous"
            is PsiFile -> element.name
            else -> element.text.take(20)
        }
    }

    private fun getContainingClass(element: PsiElement): String? {
        val containingClass = PsiTreeUtil.getParentOfType(element, JSClass::class.java)
        return containingClass?.name
    }

    private fun getSymbolKind(element: PsiElement): String {
        return when (element) {
            is JSFunction -> when {
                element.isConstructor -> "constructor"
                element.isAsync -> "async_function"
                element.isGenerator -> "generator_function"
                isReactComponentElement(element) -> "component"
                isReactHookElement(element) -> "hook"
                else -> "function"
            }

            is JSClass -> when {
                isReactComponentElement(element) -> "component"
                else -> "class"
            }

            is JSVariable -> when {
                element.isConst -> "constant"
                isReactHookElement(element) -> "hook"
                isReactComponentElement(element) -> "component"
                else -> "variable"
            }

            is JSProperty -> "property"
            is JSField -> "field"
            else -> "unknown"
        }
    }

    private fun getSignature(element: PsiElement): String? {
        return when (element) {
            is JSFunction -> buildFunctionSignature(element)
            is JSClass -> buildClassSignature(element)
            is JSVariable -> buildVariableSignature(element)
            else -> null
        }
    }

    private fun buildFunctionSignature(function: JSFunction): String {
        return try {
            val params = function.parameters.joinToString(", ") { param ->
                val type = param.typeElement?.text ?: "any"
                "${param.name ?: "param"}: $type"
            }
            val name = function.name ?: "anonymous"
            val returnType = function.returnTypeElement?.text?.let { ": $it" } ?: ""
            val asyncModifier = if (function.isAsync) "async " else ""
            val generatorModifier = if (function.isGenerator) "* " else ""
            "$asyncModifier${generatorModifier}function $name($params)$returnType"
        } catch (e: Exception) {
            function.name ?: "anonymous function"
        }
    }

    private fun buildClassSignature(jsClass: JSClass): String {
        return try {
            val name = jsClass.name ?: "anonymous"
            val superClass = jsClass.superClasses.firstOrNull()?.name
            val extendsClause = superClass?.let { " extends $it" } ?: ""
            "class $name$extendsClause"
        } catch (e: Exception) {
            jsClass.name ?: "anonymous class"
        }
    }

    private fun buildVariableSignature(variable: JSVariable): String {
        return try {
            val name = variable.name ?: "anonymous"
            val type = variable.typeElement?.text?.let { ": $it" } ?: ""
            val kind = if (variable.isConst) "const" else "let"
            "$kind $name$type"
        } catch (e: Exception) {
            variable.name ?: "anonymous variable"
        }
    }

    private fun getModifiers(element: PsiElement): List<String> {
        val modifiers = mutableListOf<String>()

        try {
            when (element) {
                is JSFunction -> {
                    if (element.isAsync) modifiers.add("async")
                    if (element.isGenerator) modifiers.add("generator")
                    if (isStaticMember(element)) modifiers.add("static")
                    if (isExported(element)) modifiers.add("export")
                }

                is JSClass -> {
                    if (isExported(element)) modifiers.add("export")
                }

                is JSVariable -> {
                    if (element.isConst) modifiers.add("const")
                    if (isExported(element)) modifiers.add("export")
                }
            }
        } catch (e: Exception) {
            // Silent fallback
        }

        return modifiers
    }

    private fun getAccessibilityWarning(element: PsiElement): String? {
        return try {
            // Check if the element is private and being accessed from outside
            if (element is JSAttributeListOwner &&
                element.attributeList?.text?.contains("private") == true
            ) {
                return "Private member - not accessible from outside its class"
            }

            // Check for TypeScript private members
            if (element.text.contains("private ")) {
                return "Private member - not accessible from outside its class"
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    private fun isStaticMember(element: PsiElement): Boolean {
        return try {
            element is JSAttributeListOwner &&
                    element.attributeList?.text?.contains("static") == true
        } catch (e: Exception) {
            false
        }
    }

    private fun isExported(element: PsiElement): Boolean {
        return try {
            element.text.startsWith("export") || element.parent?.text?.startsWith("export") == true
        } catch (e: Exception) {
            false
        }
    }

    private fun isAbstract(element: PsiElement): Boolean {
        return try {
            element.text.contains("abstract ")
        } catch (e: Exception) {
            false
        }
    }
}
