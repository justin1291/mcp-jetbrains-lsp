package dev.mcp.extensions.lsp.languages.javascript

import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.jsdoc.JSDocComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import dev.mcp.extensions.lsp.core.interfaces.SymbolExtractor
import dev.mcp.extensions.lsp.core.models.GetSymbolsArgs
import dev.mcp.extensions.lsp.core.models.SymbolInfo
import dev.mcp.extensions.lsp.core.models.TypeInfo
import dev.mcp.extensions.lsp.core.models.Visibility
import dev.mcp.extensions.lsp.core.utils.symbol
import dev.mcp.extensions.lsp.languages.base.BaseLanguageHandler

class JavaScriptSymbolExtractor : BaseLanguageHandler(), SymbolExtractor {

    override fun extractSymbolsFlat(psiFile: PsiFile, args: GetSymbolsArgs): List<SymbolInfo> {
        logger.info("Extracting symbols (flat) from JavaScript/TypeScript file: ${psiFile.name}")

        val symbols = mutableListOf<SymbolInfo>()

        // Use visitor pattern for better compatibility
        psiFile.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    is JSFunction -> {
                        val kind = getFunctionKind(element)
                        if (shouldIncludeSymbol(kind, args.symbolTypes)) {
                            val symbol = extractFunctionInfo(element)
                            if (shouldIncludeSymbol(symbol, args)) {
                                symbols.add(symbol)
                            }
                        }
                    }

                    is JSVariable -> {
                        val kind = getVariableKind(element)
                        if (shouldIncludeSymbol(kind, args.symbolTypes)) {
                            val symbol = extractVariableInfo(element)
                            if (shouldIncludeSymbol(symbol, args)) {
                                symbols.add(symbol)
                            }
                        }
                    }

                    is JSField -> {
                        if (shouldIncludeSymbol("field", args.symbolTypes)) {
                            val symbol = extractFieldInfo(element)
                            if (shouldIncludeSymbol(symbol, args)) {
                                symbols.add(symbol)
                            }
                        }
                    }

                    else -> {
                        if (args.includeImports && shouldIncludeSymbol("import", args.symbolTypes)) {
                            if (element.text.trim().startsWith("import ")) {
                                symbols.add(extractImportInfo(element))
                            }
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
        logger.info("Extracting symbols (hierarchical) from JavaScript/TypeScript file: ${psiFile.name}")

        val symbols = mutableListOf<SymbolInfo>()

        // Process imports first if requested
        if (args.includeImports && shouldIncludeSymbol("import", args.symbolTypes)) {
            psiFile.accept(object : PsiRecursiveElementVisitor() {
                override fun visitElement(element: PsiElement) {
                    if (element.text.trim().startsWith("import ")) {
                        symbols.add(extractImportInfo(element))
                    }
                    super.visitElement(element)
                }
            })
        }

        // Process top-level declarations
        psiFile.children.forEach { child ->
            when (child) {
                is JSFunction -> {
                    val kind = getFunctionKind(child)
                    if (shouldIncludeSymbol(kind, args.symbolTypes)) {
                        val symbol = extractFunctionInfo(child)
                        if (shouldIncludeSymbol(symbol, args)) {
                            symbols.add(symbol)
                        }
                    }
                }

                is JSVarStatement -> {
                    child.variables.forEach { variable ->
                        val kind = getVariableKind(variable)
                        if (shouldIncludeSymbol(kind, args.symbolTypes)) {
                            val symbol = extractVariableInfo(variable)
                            if (shouldIncludeSymbol(symbol, args)) {
                                symbols.add(symbol)
                            }
                        }
                    }
                }
            }
        }

        logger.debug("Extracted ${symbols.size} hierarchical symbols")
        return symbols
    }

    override fun supportsFile(psiFile: PsiFile): Boolean {
        val languageId = psiFile.language.id
        return languageId in setOf("JavaScript", "TypeScript", "JSX", "TSX", "ECMAScript 6")
    }

    override fun getSupportedLanguage(): String {
        return "JavaScript/TypeScript"
    }
    
    override fun getSupportedSymbolTypes(includeFrameworkTypes: Boolean): Set<String> {
        val types = mutableSetOf(
            // Core JavaScript/TypeScript types
            "function",
            "async_function",
            "generator",
            "arrow_function",
            "method",
            "constructor",
            "getter",
            "setter",
            "variable",
            "constant",
            "field",
            "class",
            "interface",
            "enum",
            "type_alias",
            "import",
            "export",
            "module",
            "namespace",
            "decorator"
        )

        // Framework-specific types (only include if requested)
        if (includeFrameworkTypes) {
            types.addAll(listOf(
                // React
                "component",
                "hook",
                // Vue
                "directive", 
                "mixin",
                // Angular
                "service",
                "pipe"
            ))
        }
        
        return types
    }

    private fun extractFunctionInfo(function: JSFunction): SymbolInfo {
        return symbol {
            name(function.name ?: "anonymous")
            kind(getFunctionKind(function))

            location(
                function.textRange.startOffset,
                function.textRange.endOffset,
                getLineNumber(function) + 1
            )

            // Type info for return type
            val returnTypeStr = getReturnTypeString(function)
            if (returnTypeStr != null) {
                type(
                    TypeInfo(
                        displayName = returnTypeStr,
                        baseType = returnTypeStr,
                        typeParameters = null,
                        isArray = returnTypeStr.contains("[]"),
                        rawType = returnTypeStr
                    )
                )
            }

            // Signature
            signature(buildFunctionSignature(function))

            // Visibility
            visibility(getJSVisibility(function))

            // Documentation
            getDocComment(function)?.let {
                documentation(
                    present = true,
                    summary = extractJSDocSummary(it),
                    format = "jsdoc"
                )
            }

            // Modifiers
            if (function.isAsync) modifier("async")
            if (function.isGenerator) modifier("generator")
            if (isArrowFunction(function)) modifier("arrow")

            // Parameters as language data
            val params = function.parameters.map { param ->
                val paramType = param.typeElement?.text ?: "any"
                "$paramType ${param.name}"
            }
            if (params.isNotEmpty()) {
                languageData("parameters", params.joinToString(", "))
            }
            languageData("parameterCount", function.parameters.size.toString())

            // Mark React components
            if (isReactComponent(function)) {
                languageData("componentType", "functional")
            }

            // Mark hooks
            if (isReactHook(function)) {
                languageData("hookType", "custom")
            }
        }
    }

    private fun extractVariableInfo(variable: JSVariable): SymbolInfo {
        return symbol {
            name(variable.name ?: "anonymous")
            kind(getVariableKind(variable))

            location(
                variable.textRange.startOffset,
                variable.textRange.endOffset,
                getLineNumber(variable) + 1
            )

            // Type info
            val varType = variable.typeElement
            if (varType != null) {
                type(
                    TypeInfo(
                        displayName = varType.text,
                        baseType = varType.text,
                        typeParameters = null,
                        isArray = varType.text.contains("[]"),
                        rawType = varType.text
                    )
                )
            }

            // Visibility
            visibility(getJSVisibility(variable))

            // Documentation
            getDocComment(variable)?.let {
                documentation(
                    present = true,
                    summary = extractJSDocSummary(it),
                    format = "jsdoc"
                )
            }

            // Mark as constant if applicable
            if (variable.isConst) {
                modifier("const")
            }

            // Initial value for constants
            if (variable.isConst && variable.hasInitializer()) {
                variable.initializer?.let { initializer ->
                    if (initializer is JSLiteralExpression) {
                        languageData("initialValue", initializer.text)
                    }
                }
            }

            // Mark React components
            val initializer = variable.initializer
            if (initializer != null && isReactComponent(initializer)) {
                languageData("componentType", "variable")
            }

            // Mark hooks
            if (isReactHook(variable)) {
                languageData("hookType", "custom")
            }
        }
    }

    private fun extractFieldInfo(field: JSField): SymbolInfo {
        return symbol {
            name(field.name ?: "anonymous")
            kind("field")

            location(
                field.textRange.startOffset,
                field.textRange.endOffset,
                getLineNumber(field) + 1
            )

            // Type info
            field.typeElement?.let { fieldType ->
                type(
                    TypeInfo(
                        displayName = fieldType.text,
                        baseType = fieldType.text,
                        typeParameters = null,
                        isArray = fieldType.text.contains("[]"),
                        rawType = fieldType.text
                    )
                )
            }

            // Visibility - simplified approach
            visibility(getJSFieldVisibility(field))

            // Documentation
            getDocComment(field)?.let {
                documentation(
                    present = true,
                    summary = extractJSDocSummary(it),
                    format = "jsdoc"
                )
            }

            // Initial value for constants
            field.initializer?.let { initializer ->
                if (initializer is JSLiteralExpression) {
                    languageData("initialValue", initializer.text)
                }
            }
        }
    }

    private fun extractImportInfo(import: PsiElement): SymbolInfo {
        return symbol {
            val importText = import.text.trim()
            name(importText)
            kind("import")

            location(
                import.textRange.startOffset,
                import.textRange.endOffset,
                getLineNumber(import) + 1
            )

            // Simplified import parsing
            val importType = when {
                importText.contains("import * as") -> "namespace"
                importText.contains("import {") -> "named"
                else -> "default"
            }
            languageData("importType", importType)

            visibility("public") // Imports are always public
        }
    }

    // Helper methods

    private fun getFunctionKind(function: JSFunction): String {
        return when {
            isArrowFunction(function) -> "arrow_function"
            function.isAsync -> "async_function"
            function.isGenerator -> "generator_function"
            function.isConstructor -> "constructor"
            function.isGetProperty -> "getter"
            function.isSetProperty -> "setter"
            isMethodInClass(function) -> "method"
            else -> "function"
        }
    }

    private fun getVariableKind(variable: JSVariable): String {
        return when {
            variable.isConst -> "constant"
            else -> "variable"
        }
    }

    private fun getJSVisibility(element: PsiElement): String {
        return when {
            isExported(element) -> "public"
            else -> "module"
        }
    }

    private fun getJSFieldVisibility(field: JSField): String {
        return when {
            field.name?.startsWith("#") == true -> "private"
            else -> "public"
        }
    }

    private fun isArrowFunction(function: JSFunction): Boolean {
        return function.text.contains("=>")
    }

    private fun isMethodInClass(function: JSFunction): Boolean {
        // Check if function is a method within a class
        var parent = function.parent
        while (parent != null) {
            if (parent.text.contains("class ")) {
                return true
            }
            parent = parent.parent
        }
        return false
    }

    private fun isExported(element: PsiElement): Boolean {
        // Simplified export detection
        return element.text.startsWith("export") || element.parent?.text?.startsWith("export") == true
    }

    private fun getDocComment(element: PsiElement): JSDocComment? {
        return PsiTreeUtil.getChildOfType(element, JSDocComment::class.java)
    }

    private fun getReturnTypeString(function: JSFunction): String? {
        // Try to extract return type from function signature
        val functionText = function.text
        val returnTypeMatch = Regex(""":\s*([^{]+?)\s*[{=]""").find(functionText)
        return returnTypeMatch?.groupValues?.get(1)?.trim()
    }

    private fun isReactComponent(element: PsiElement): Boolean {
        return when (element) {
            is JSFunction -> {
                val name = element.name
                name != null && name.isNotEmpty() && name[0].isUpperCase() && hasJSXInBody(element)
            }

            is JSVariable -> {
                val name = element.name
                name != null && name.isNotEmpty() && name[0].isUpperCase() &&
                        element.initializer?.text?.contains("React") == true
            }

            else -> false
        }
    }

    private fun isReactHook(element: PsiElement): Boolean {
        val name = when (element) {
            is JSFunction -> element.name
            is JSVariable -> element.name
            else -> null
        }
        return name != null && name.startsWith("use") && name.length > 3 && name[3].isUpperCase()
    }

    private fun hasJSXInBody(function: JSFunction): Boolean {
        // This would need proper JSX detection in practice
        val bodyText = function.block?.text ?: ""
        return bodyText.contains("<") && bodyText.contains("/>")
    }

    private fun extractJSDocSummary(docComment: JSDocComment): String {
        val text = docComment.text
        val firstSentence = text.substringBefore(". ").trim()
        return if (firstSentence.isNotEmpty()) "$firstSentence." else text.take(100)
    }

    private fun buildFunctionSignature(function: JSFunction): String {
        val params = function.parameters.joinToString(", ") { param ->
            val type = param.typeElement?.text ?: "any"
            "${param.name}: $type"
        }
        val returnType = getReturnTypeString(function) ?: "void"
        val name = function.name ?: "anonymous"
        return "$name($params): $returnType"
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
