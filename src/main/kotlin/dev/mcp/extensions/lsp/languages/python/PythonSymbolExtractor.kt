package dev.mcp.extensions.lsp.languages.python

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.*
import dev.mcp.extensions.lsp.core.interfaces.SymbolExtractor
import dev.mcp.extensions.lsp.core.models.GetSymbolsArgs
import dev.mcp.extensions.lsp.core.models.SymbolInfo
import dev.mcp.extensions.lsp.core.models.SymbolKind
import dev.mcp.extensions.lsp.core.models.Visibility
import dev.mcp.extensions.lsp.core.utils.symbol

/**
 * Symbol extractor implementation for Python language.
 * Requires Python plugin to be installed at runtime.
 *
 * Registered as a service in mcp-lsp-python.xml when Python module is available.
 */

class PythonSymbolExtractor : PythonBaseHandler(), SymbolExtractor {

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
     * Check if a function is a property (has @property decorator).
     */
    private fun isProperty(function: PyFunction): Boolean {
        return extractDecorators(function).any { it == "@property" }
    }

    /**
     * Get the type of method (static, class, instance, property).
     */
    private fun getMethodType(function: PyFunction): String {
        val decorators = extractDecorators(function)
        return when {
            decorators.contains("@staticmethod") -> "static"
            decorators.contains("@classmethod") -> "class"
            decorators.contains("@property") -> "property"
            else -> "instance"
        }
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
     * Extract docstring from an element.
     */
    private fun extractDocstring(element: PyDocStringOwner): String? {
        return element.docStringValue
    }

    /**
     * Check if a variable is relevant (not a loop variable, etc.).
     */
    private fun isRelevantVariable(target: PyTargetExpression): Boolean {
        val parent = target.parent
        return parent !is PyForPart &&
                parent !is PyComprehensionElement &&
                parent !is PyWithItem
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

    override fun extractSymbolsFlat(psiFile: PsiFile, args: GetSymbolsArgs): List<SymbolInfo> {
        logger.info("Extracting symbols (flat) from Python file: ${psiFile.name}")

        // Ensure we have a Python file
        if (psiFile !is PyFile) {
            logger.warn("File is not a Python file: ${psiFile.javaClass.name}")
            return emptyList()
        }

        val symbols = mutableListOf<SymbolInfo>()

        psiFile.accept(object : PyRecursiveElementVisitor() {
            override fun visitPyClass(node: PyClass) {
                if (shouldIncludeSymbol("class", args.symbolTypes)) {
                    val symbol = extractClassInfo(node)
                    if (shouldIncludeSymbol(symbol, args)) {
                        symbols.add(symbol)
                    }
                }
                super.visitPyClass(node)
            }

            override fun visitPyFunction(node: PyFunction) {
                val kind = determineSymbolKind(node)
                if (shouldIncludeSymbol(kind.value, args.symbolTypes)) {
                    val symbol = extractFunctionInfo(node)
                    if (shouldIncludeSymbol(symbol, args)) {
                        symbols.add(symbol)
                    }
                }
                super.visitPyFunction(node)
            }

            override fun visitPyTargetExpression(node: PyTargetExpression) {
                if (shouldIncludeSymbol("variable", args.symbolTypes) ||
                    shouldIncludeSymbol("field", args.symbolTypes)
                ) {
                    if (isRelevantVariable(node)) {
                        val symbol = extractVariableInfo(node)
                        if (shouldIncludeSymbol(symbol, args)) {
                            symbols.add(symbol)
                        }
                    }
                }
                super.visitPyTargetExpression(node)
            }

            override fun visitPyImportStatement(node: PyImportStatement) {
                if (args.includeImports && shouldIncludeSymbol("import", args.symbolTypes)) {
                    symbols.add(extractImportInfo(node))
                }
                super.visitPyImportStatement(node)
            }

            override fun visitPyFromImportStatement(node: PyFromImportStatement) {
                if (args.includeImports && shouldIncludeSymbol("import", args.symbolTypes)) {
                    symbols.add(extractFromImportInfo(node))
                }
                super.visitPyFromImportStatement(node)
            }
        })

        logger.debug("Extracted ${symbols.size} flat symbols")
        return symbols
    }

    override fun extractSymbolsHierarchical(psiFile: PsiFile, args: GetSymbolsArgs): List<SymbolInfo> {
        logger.info("Extracting symbols (hierarchical) from Python file: ${psiFile.name}")

        if (psiFile !is PyFile) {
            logger.warn("File is not a Python file")
            return emptyList()
        }

        val symbols = mutableListOf<SymbolInfo>()

        // Process imports first if requested
        if (args.includeImports && shouldIncludeSymbol("import", args.symbolTypes)) {
            // Process all import statements in the file
            psiFile.accept(object : PyRecursiveElementVisitor() {
                override fun visitPyImportStatement(node: PyImportStatement) {
                    symbols.add(extractImportInfo(node))
                }

                override fun visitPyFromImportStatement(node: PyFromImportStatement) {
                    symbols.add(extractFromImportInfo(node))
                }
            })
        }

        // Process top-level elements
        psiFile.topLevelClasses.forEach { pyClass ->
            if (shouldIncludeSymbol("class", args.symbolTypes)) {
                val symbol = extractClassInfo(pyClass, includeChildren = true, args = args)
                if (shouldIncludeSymbol(symbol, args)) {
                    symbols.add(symbol)
                }
            }
        }

        // Process top-level functions
        psiFile.topLevelFunctions.forEach { pyFunction ->
            val kind = determineSymbolKind(pyFunction)
            if (shouldIncludeSymbol(kind.value, args.symbolTypes)) {
                val symbol = extractFunctionInfo(pyFunction)
                if (shouldIncludeSymbol(symbol, args)) {
                    symbols.add(symbol)
                }
            }
        }

        // Process module-level variables
        psiFile.topLevelAttributes.forEach { attr ->
            if (shouldIncludeSymbol("variable", args.symbolTypes) && isRelevantVariable(attr)) {
                val symbol = extractVariableInfo(attr)
                if (shouldIncludeSymbol(symbol, args)) {
                    symbols.add(symbol)
                }
            }
        }

        logger.debug("Extracted ${symbols.size} hierarchical symbols")
        return symbols
    }

    override fun supportsFile(psiFile: PsiFile): Boolean {
        val languageId = psiFile.language.id
        return languageId == "Python" || languageId == "PythonCore"
    }

    override fun getSupportedLanguage(): String = "Python"

    private fun extractClassInfo(
        pyClass: PyClass,
        includeChildren: Boolean = false,
        args: GetSymbolsArgs? = null
    ): SymbolInfo {
        return symbol {
            name(pyClass.name ?: "anonymous")
            qualifiedName(pyClass.qualifiedName)
            kind("class")

            location(
                pyClass.textRange.startOffset,
                pyClass.textRange.endOffset,
                getLineNumber(pyClass) + 1
            )

            // Python modifiers
            getPythonModifiers(pyClass).forEach { modifier(it) }

            // Visibility based on naming convention
            visibility(getPythonVisibility(pyClass.name))

            // Documentation (docstring)
            pyClass.docStringValue?.let {
                documentation(
                    present = true,
                    summary = extractDocstringSummary(it),
                    format = "docstring"
                )
            }

            // Decorators
            extractDecorators(pyClass).forEach { dec ->
                decorator(
                    dec.removePrefix("@"),
                    builtin = isPythonBuiltinDecorator(dec)
                )
            }

            // Base classes (extends/implements)
            pyClass.superClassExpressions.forEach { superClass ->
                implements(superClass.text)
            }

            // Type parameters (if generic) - commented out as PyClass doesn't have typeParameterList in basic Python PSI
            // pyClass.typeParameterList?.typeParameters?.let { typeParams ->
            //     if (typeParams.isNotEmpty()) {
            //         languageData("typeParameters", typeParams.joinToString(", ") { it.name ?: "?" })
            //     }
            // }

            // Special class types
            if (isDataclass(pyClass)) {
                languageData("classType", "dataclass")
            }

            // Add children if requested
            if (includeChildren && args != null) {
                val childSymbols = mutableListOf<SymbolInfo>()

                // Add nested classes
                pyClass.nestedClasses.forEach { nestedClass ->
                    if (shouldIncludeSymbol("class", args.symbolTypes)) {
                        val nestedSymbol = extractClassInfo(nestedClass, includeChildren = true, args = args)
                        if (shouldIncludeSymbol(nestedSymbol, args)) {
                            childSymbols.add(nestedSymbol)
                        }
                    }
                }

                // Add methods
                pyClass.methods.forEach { method ->
                    val methodKind = determineSymbolKind(method)
                    if (shouldIncludeSymbol(methodKind.value, args.symbolTypes)) {
                        val methodSymbol = extractFunctionInfo(method)
                        if (shouldIncludeSymbol(methodSymbol, args)) {
                            childSymbols.add(methodSymbol)
                        }
                    }
                }

                // Add class attributes
                pyClass.classAttributes.forEach { attr ->
                    if (shouldIncludeSymbol("field", args.symbolTypes) && isRelevantVariable(attr)) {
                        val attrSymbol = extractVariableInfo(attr)
                        if (shouldIncludeSymbol(attrSymbol, args)) {
                            childSymbols.add(attrSymbol)
                        }
                    }
                }

                children(childSymbols)
            }

            // Check if deprecated
            if (hasDeprecatedDecorator(extractDecorators(pyClass))) {
                deprecated()
            }
        }
    }

    private fun extractFunctionInfo(pyFunction: PyFunction): SymbolInfo {
        return symbol {
            name(pyFunction.name ?: "anonymous")
            qualifiedName(pyFunction.qualifiedName)
            kind(determineSymbolKind(pyFunction).value)

            location(
                pyFunction.textRange.startOffset,
                pyFunction.textRange.endOffset,
                getLineNumber(pyFunction) + 1
            )

            // Python modifiers
            getPythonModifiers(pyFunction).forEach { modifier(it) }

            // Return type info - simplified for now
            // TODO: Add proper type annotation support when Python PSI API is available
            type(null)

            // Signature
            signature(buildFunctionSignature(pyFunction))

            // Visibility
            visibility(getPythonVisibility(pyFunction.name))

            // Documentation (docstring)
            pyFunction.docStringValue?.let {
                documentation(
                    present = true,
                    summary = extractDocstringSummary(it),
                    format = "docstring"
                )
            }

            // Decorators
            extractDecorators(pyFunction).forEach { dec ->
                decorator(
                    dec.removePrefix("@"),
                    builtin = isPythonBuiltinDecorator(dec)
                )
            }

            // Parameters - simplified without type hints for now
            val params = pyFunction.parameterList.parameters.map { param ->
                param.name ?: "unknown"
            }
            if (params.isNotEmpty()) {
                languageData("parameters", params.joinToString(", "))
            }
            languageData("parameterCount", pyFunction.parameterList.parameters.size.toString())

            // Check if it's an override (by name convention in Python)
            if (pyFunction.containingClass != null && isOverrideMethod(pyFunction)) {
                // Python doesn't have explicit override, but we can detect common patterns
                languageData("possibleOverride", "true")
            }

            // Check if deprecated
            val decorators = extractDecorators(pyFunction)
            if (hasDeprecatedDecorator(decorators)) {
                deprecated()
            }

            // Mark special method types
            if (pyFunction.isAsync) async()
            if (pyFunction.isGenerator) generator()
        }
    }

    private fun extractVariableInfo(targetExpression: PyTargetExpression): SymbolInfo {
        return symbol {
            name(targetExpression.name ?: "anonymous")
            qualifiedName(targetExpression.qualifiedName)

            // Determine kind
            val varKind = when {
                targetExpression.containingClass != null -> "field"
                isConstant(targetExpression.name ?: "") -> "variable" // could use "constant" 
                else -> "variable"
            }
            kind(varKind)

            location(
                targetExpression.textRange.startOffset,
                targetExpression.textRange.endOffset,
                getLineNumber(targetExpression) + 1
            )

            // Type info - simplified for now without type annotations
            // TODO: Add type annotation support when Python PSI API is available
            type(null)

            // Visibility
            visibility(getPythonVisibility(targetExpression.name))

            // Documentation
            targetExpression.docStringValue?.let {
                documentation(
                    present = true,
                    summary = extractDocstringSummary(it),
                    format = "docstring"
                )
            }

            // Modifiers
            if (isConstant(targetExpression.name ?: "")) {
                modifier("constant")
            }

            // Check if it's a class variable
            if (targetExpression.containingClass != null) {
                languageData("scope", "class")
            } else {
                languageData("scope", "module")
            }
        }
    }

    private fun extractImportInfo(importStatement: PyImportStatement): SymbolInfo {
        val importedNames = importStatement.importElements.mapNotNull { it.importedQName?.toString() }

        return symbol {
            name(importedNames.joinToString(", "))
            kind("import")

            location(
                importStatement.textRange.startOffset,
                importStatement.textRange.endOffset,
                getLineNumber(importStatement) + 1
            )

            visibility("public") // imports are always public in Python

            languageData("importType", "regular")
        }
    }

    private fun extractFromImportInfo(fromImportStatement: PyFromImportStatement): SymbolInfo {
        val source = fromImportStatement.importSourceQName?.toString() ?: "unknown"
        val importedNames = fromImportStatement.importElements.mapNotNull { it.importedQName?.toString() }

        return symbol {
            name("from $source import ${importedNames.joinToString(", ")}")
            kind("import")

            location(
                fromImportStatement.textRange.startOffset,
                fromImportStatement.textRange.endOffset,
                getLineNumber(fromImportStatement) + 1
            )

            visibility("public")

            languageData("importType", if (fromImportStatement.isStarImport) "star" else "from")
            languageData("source", source)
        }
    }

    private fun determineSymbolKind(function: PyFunction): SymbolKind {
        val decorators = extractDecorators(function)

        return when {
            function.name == "__init__" -> SymbolKind.Constructor  // Python constructor
            function.isGenerator -> SymbolKind.Generator
            function.isAsync -> SymbolKind.AsyncFunction
            decorators.contains("@property") -> SymbolKind.Property
            function.containingClass == null -> SymbolKind.Function
            else -> SymbolKind.Method
        }
    }

    private fun buildFunctionSignature(function: PyFunction): String {
        val params = function.parameterList.parameters.joinToString(", ") { param ->
            param.name ?: "unknown"
        }

        // TODO: Add return type when Python PSI API supports it
        return "${function.name}($params)"
    }

    private fun extractDocstringSummary(docstring: String): String {
        // Get the first line or sentence as summary
        val lines = docstring.trim().lines()
        return if (lines.isNotEmpty()) {
            val firstLine = lines.first().trim()
            if (firstLine.endsWith('.')) firstLine else "$firstLine."
        } else {
            docstring.take(100)
        }
    }

    private fun hasDeprecatedDecorator(decorators: List<String>): Boolean {
        return decorators.any { it.contains("deprecated", ignoreCase = true) }
    }

    private fun isPythonBuiltinDecorator(decorator: String): Boolean {
        val builtin = setOf(
            "@property", "@staticmethod", "@classmethod",
            "@abstractmethod", "@cached_property", "@contextmanager",
            "@dataclass", "@lru_cache", "@functools.wraps"
        )
        return builtin.contains(decorator)
    }

    private fun isOverrideMethod(function: PyFunction): Boolean {
        // Common Python override patterns
        val overrideMethods = setOf(
            "__init__", "__str__", "__repr__", "__eq__", "__hash__",
            "__lt__", "__le__", "__gt__", "__ge__", "__ne__",
            "__add__", "__sub__", "__mul__", "__div__", "__mod__",
            "__enter__", "__exit__", "__iter__", "__next__",
            "__getitem__", "__setitem__", "__delitem__", "__len__",
            "__contains__", "__call__"
        )
        return overrideMethods.contains(function.name)
    }

    private fun shouldIncludeSymbol(symbolType: String, filterTypes: List<String>?): Boolean {
        return filterTypes == null || filterTypes.contains(symbolType)
    }

    private fun shouldIncludeSymbol(symbol: SymbolInfo, args: GetSymbolsArgs): Boolean {
        // Check visibility filter
        if (!args.includePrivate && symbol.visibility == Visibility.PRIVATE) {
            return false
        }

        // Check generated filter
        if (!args.includeGenerated && symbol.isSynthetic) {
            return false
        }

        return true
    }
}
