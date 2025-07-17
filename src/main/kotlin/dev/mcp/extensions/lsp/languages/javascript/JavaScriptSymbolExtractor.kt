package dev.mcp.extensions.lsp.languages.javascript

import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.lang.javascript.psi.ecmal4.JSImportStatement
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

        val extractedSymbols = mutableListOf<SymbolInfo>()

        psiFile.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    is JSClass -> {
                        if (shouldIncludeSymbol("class", args.symbolTypes)) {
                            val classSymbol = extractClassInfo(element, includeChildren = false)
                            if (shouldIncludeSymbol(classSymbol, args)) {
                                extractedSymbols.add(classSymbol)
                            }
                        }
                    }

                    is JSFunction -> {
                        val functionKind = getFunctionKind(element)
                        if (shouldIncludeSymbol(functionKind, args.symbolTypes)) {
                            val functionSymbol = extractFunctionInfo(element)
                            if (shouldIncludeSymbol(functionSymbol, args)) {
                                extractedSymbols.add(functionSymbol)
                            }
                        }
                    }

                    is JSVariable -> {
                        val variableKind = getVariableKind(element)
                        if (shouldIncludeSymbol(variableKind, args.symbolTypes)) {
                            val variableSymbol = extractVariableInfo(element)
                            if (shouldIncludeSymbol(variableSymbol, args)) {
                                extractedSymbols.add(variableSymbol)
                            }
                        }
                    }

                    is JSField -> {
                        if (shouldIncludeSymbol("field", args.symbolTypes)) {
                            val fieldSymbol = extractFieldInfo(element)
                            if (shouldIncludeSymbol(fieldSymbol, args)) {
                                extractedSymbols.add(fieldSymbol)
                            }
                        }
                    }

                    is JSImportStatement -> {
                        if (args.includeImports && shouldIncludeSymbol("import", args.symbolTypes)) {
                            extractedSymbols.add(extractImportInfo(element))
                        }
                    }

                    else -> {
                        val typeScriptSymbol = tryExtractTypeScriptSymbol(element, args.symbolTypes)
                        if (typeScriptSymbol != null) {
                            extractedSymbols.add(typeScriptSymbol)
                        }
                    }
                }
                super.visitElement(element)
            }
        })

        logger.debug("Extracted ${extractedSymbols.size} flat symbols")
        return extractedSymbols
    }

    override fun extractSymbolsHierarchical(psiFile: PsiFile, args: GetSymbolsArgs): List<SymbolInfo> {
        logger.info("Extracting symbols (hierarchical) from JavaScript/TypeScript file: ${psiFile.name}")

        val hierarchicalSymbols = mutableListOf<SymbolInfo>()

        if (args.includeImports && shouldIncludeSymbol("import", args.symbolTypes)) {
            val importSymbols = extractAllImportSymbols(psiFile)
            hierarchicalSymbols.addAll(importSymbols)
        }

        val topLevelClasses = findTopLevelClasses(psiFile)
        topLevelClasses.forEach { jsClass ->
            if (shouldIncludeSymbol("class", args.symbolTypes)) {
                val classSymbolWithChildren = extractClassInfo(jsClass, includeChildren = true, args = args)
                if (shouldIncludeSymbol(classSymbolWithChildren, args)) {
                    hierarchicalSymbols.add(classSymbolWithChildren)
                }
            }
        }

        val topLevelElements = extractTopLevelElements(psiFile, args)
        hierarchicalSymbols.addAll(topLevelElements)

        val typeScriptTopLevelSymbols = extractTypeScriptTopLevelSymbols(psiFile, args)
        hierarchicalSymbols.addAll(typeScriptTopLevelSymbols)

        logger.debug("Extracted ${hierarchicalSymbols.size} hierarchical symbols")
        return hierarchicalSymbols
    }

    private fun tryExtractTypeScriptSymbol(element: PsiElement, symbolTypes: List<String>?): SymbolInfo? {
        val elementText = element.text?.trim() ?: ""
        return when {
            elementText.startsWith("interface ") && shouldIncludeSymbol("interface", symbolTypes) -> {
                extractTypeScriptInterface(element)
            }
            elementText.startsWith("enum ") && shouldIncludeSymbol("enum", symbolTypes) -> {
                extractTypeScriptEnum(element)
            }
            elementText.startsWith("type ") && shouldIncludeSymbol("type_alias", symbolTypes) -> {
                extractTypeScriptTypeAlias(element)
            }
            else -> null
        }
    }

    private fun extractAllImportSymbols(psiFile: PsiFile): List<SymbolInfo> {
        val importSymbols = mutableListOf<SymbolInfo>()
        psiFile.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is JSImportStatement) {
                    importSymbols.add(extractImportInfo(element))
                }
                super.visitElement(element)
            }
        })
        return importSymbols
    }

    private fun findTopLevelClasses(psiFile: PsiFile): List<JSClass> {
        val topLevelClasses = mutableListOf<JSClass>()
        psiFile.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is JSClass) {
                    val elementParent = element.parent
                    var isTopLevel = false

                    var currentParent = elementParent
                    while (currentParent != null && currentParent !is JSClass) {
                        if (currentParent is PsiFile) {
                            isTopLevel = true
                            break
                        }
                        currentParent = currentParent.parent
                    }

                    if (isTopLevel && !topLevelClasses.contains(element)) {
                        topLevelClasses.add(element)
                    }
                }
                super.visitElement(element)
            }
        })
        return topLevelClasses
    }

    private fun extractTopLevelElements(psiFile: PsiFile, args: GetSymbolsArgs): List<SymbolInfo> {
        val topLevelSymbols = mutableListOf<SymbolInfo>()
        
        psiFile.children.forEach { child ->
            when (child) {
                is JSFunction -> {
                    val functionKind = getFunctionKind(child)
                    if (shouldIncludeSymbol(functionKind, args.symbolTypes)) {
                        val functionSymbol = extractFunctionInfo(child)
                        if (shouldIncludeSymbol(functionSymbol, args)) {
                            topLevelSymbols.add(functionSymbol)
                        }
                    }
                }

                is JSVarStatement -> {
                    child.variables.forEach { variable ->
                        val variableKind = getVariableKind(variable)
                        if (shouldIncludeSymbol(variableKind, args.symbolTypes)) {
                            val variableSymbol = extractVariableInfo(variable)
                            if (shouldIncludeSymbol(variableSymbol, args)) {
                                topLevelSymbols.add(variableSymbol)
                            }
                        }
                    }
                }
            }
        }
        
        return topLevelSymbols
    }

    private fun extractTypeScriptTopLevelSymbols(psiFile: PsiFile, args: GetSymbolsArgs): List<SymbolInfo> {
        val typeScriptSymbols = mutableListOf<SymbolInfo>()
        
        psiFile.children.forEach { child ->
            val elementText = child.text?.trim() ?: ""
            when {
                elementText.startsWith("interface ") && shouldIncludeSymbol("interface", args.symbolTypes) -> {
                    typeScriptSymbols.add(extractTypeScriptInterface(child))
                }
                elementText.startsWith("enum ") && shouldIncludeSymbol("enum", args.symbolTypes) -> {
                    typeScriptSymbols.add(extractTypeScriptEnum(child))
                }
                elementText.startsWith("type ") && shouldIncludeSymbol("type_alias", args.symbolTypes) -> {
                    typeScriptSymbols.add(extractTypeScriptTypeAlias(child))
                }
            }
        }
        
        return typeScriptSymbols
    }

    private fun extractClassInfo(
        jsClass: JSClass,
        includeChildren: Boolean = false,
        args: GetSymbolsArgs? = null
    ): SymbolInfo {
        return symbol {
            name(jsClass.name ?: "anonymous")
            qualifiedName(jsClass.qualifiedName)
            kind("class")

            location(
                jsClass.textRange.startOffset,
                jsClass.textRange.endOffset,
                getLineNumber(jsClass) + 1
            )

            visibility(getJSVisibility(jsClass))

            getDocComment(jsClass)?.let { docComment ->
                documentation(
                    present = true,
                    summary = extractJSDocSummary(docComment),
                    format = "jsdoc"
                )
            }

            val typeParametersFromGenerics = extractTypeParametersFromText(jsClass.text, "class")
            if (typeParametersFromGenerics != null) {
                languageData("typeParameters", typeParametersFromGenerics)
            }

            jsClass.superClasses.forEach { superClass ->
                languageData("extends", superClass.name ?: "unknown")
            }

            if (isReactComponent(jsClass)) {
                languageData("componentType", "class")
            }

            if (includeChildren && args != null) {
                val childSymbols = extractClassChildSymbols(jsClass, args)
                children(childSymbols)
            }

            if (hasDeprecatedAnnotation(jsClass)) {
                deprecated()
            }
        }
    }

    private fun extractClassChildSymbols(jsClass: JSClass, args: GetSymbolsArgs): List<SymbolInfo> {
        val childSymbols = mutableListOf<SymbolInfo>()

        jsClass.functions.forEach { method ->
            val methodKind = getFunctionKind(method)
            if (shouldIncludeSymbol(methodKind, args.symbolTypes)) {
                val methodSymbol = extractFunctionInfo(method)
                if (shouldIncludeSymbol(methodSymbol, args)) {
                    childSymbols.add(methodSymbol)
                }
            }
        }

        jsClass.fields.forEach { field ->
            if (shouldIncludeSymbol("field", args.symbolTypes)) {
                val fieldSymbol = extractFieldInfo(field)
                if (shouldIncludeSymbol(fieldSymbol, args)) {
                    childSymbols.add(fieldSymbol)
                }
            }
        }

        return childSymbols
    }

    private fun extractTypeScriptInterface(element: PsiElement): SymbolInfo {
        val interfaceText = element.text
        val interfaceName = extractNameFromPattern(interfaceText, """interface\s+(\w+)""") ?: "anonymous"

        return symbol {
            name(interfaceName)
            kind("interface")

            location(
                element.textRange.startOffset,
                element.textRange.endOffset,
                getLineNumber(element) + 1
            )

            visibility(getJSVisibility(element))

            val extendsClause = extractExtendsClause(interfaceText)
            if (extendsClause != null) {
                languageData("extends", extendsClause)
            }

            val typeParametersFromInterface = extractTypeParametersFromText(interfaceText, "interface")
            if (typeParametersFromInterface != null) {
                languageData("typeParameters", typeParametersFromInterface)
            }

            getDocComment(element)?.let { docComment ->
                documentation(
                    present = true,
                    summary = extractJSDocSummary(docComment),
                    format = "jsdoc"
                )
            }
        }
    }

    private fun extractTypeScriptEnum(element: PsiElement): SymbolInfo {
        val enumText = element.text
        val enumName = extractNameFromPattern(enumText, """enum\s+(\w+)""") ?: "anonymous"

        return symbol {
            name(enumName)
            kind("enum")

            location(
                element.textRange.startOffset,
                element.textRange.endOffset,
                getLineNumber(element) + 1
            )

            visibility(getJSVisibility(element))

            if (enumText.contains("const enum")) {
                modifier("const")
            }

            getDocComment(element)?.let { docComment ->
                documentation(
                    present = true,
                    summary = extractJSDocSummary(docComment),
                    format = "jsdoc"
                )
            }
        }
    }

    private fun extractTypeScriptTypeAlias(element: PsiElement): SymbolInfo {
        val typeAliasText = element.text
        val typeAliasName = extractNameFromPattern(typeAliasText, """type\s+(\w+)""") ?: "anonymous"

        return symbol {
            name(typeAliasName)
            kind("type_alias")

            location(
                element.textRange.startOffset,
                element.textRange.endOffset,
                getLineNumber(element) + 1
            )

            visibility(getJSVisibility(element))

            val typeParametersFromAlias = extractTypeParametersFromText(typeAliasText, "type")
            if (typeParametersFromAlias != null) {
                languageData("typeParameters", typeParametersFromAlias)
            }

            getDocComment(element)?.let { docComment ->
                documentation(
                    present = true,
                    summary = extractJSDocSummary(docComment),
                    format = "jsdoc"
                )
            }
        }
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

            val returnTypeFromSignature = getReturnTypeString(function)
            if (returnTypeFromSignature != null) {
                type(
                    TypeInfo(
                        displayName = returnTypeFromSignature,
                        baseType = returnTypeFromSignature,
                        typeParameters = null,
                        isArray = returnTypeFromSignature.contains("[]"),
                        rawType = returnTypeFromSignature
                    )
                )
            }

            signature(buildFunctionSignature(function))
            visibility(getJSVisibility(function))

            getDocComment(function)?.let { docComment ->
                documentation(
                    present = true,
                    summary = extractJSDocSummary(docComment),
                    format = "jsdoc"
                )
            }

            if (function.isAsync) modifier("async")
            if (function.isGenerator) modifier("generator")
            if (isArrowFunction(function)) modifier("arrow")

            val parameterInfoList = function.parameters.map { param ->
                val paramType = param.typeElement?.text ?: "any"
                "$paramType ${param.name}"
            }
            if (parameterInfoList.isNotEmpty()) {
                languageData("parameters", parameterInfoList.joinToString(", "))
            }
            languageData("parameterCount", function.parameters.size.toString())

            if (isReactComponent(function)) {
                languageData("componentType", "functional")
            }

            if (isReactHook(function)) {
                languageData("hookType", "custom")
            }

            if (hasDeprecatedAnnotation(function)) {
                deprecated()
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

            val variableTypeElement = variable.typeElement
            if (variableTypeElement != null) {
                type(
                    TypeInfo(
                        displayName = variableTypeElement.text,
                        baseType = variableTypeElement.text,
                        typeParameters = null,
                        isArray = variableTypeElement.text.contains("[]"),
                        rawType = variableTypeElement.text
                    )
                )
            }

            visibility(getJSVisibility(variable))

            getDocComment(variable)?.let { docComment ->
                documentation(
                    present = true,
                    summary = extractJSDocSummary(docComment),
                    format = "jsdoc"
                )
            }

            if (variable.isConst) {
                modifier("const")
            }

            if (variable.isConst && variable.hasInitializer()) {
                variable.initializer?.let { initializer ->
                    if (initializer is JSLiteralExpression) {
                        languageData("initialValue", initializer.text)
                    }
                }
            }

            val variableInitializer = variable.initializer
            if (variableInitializer != null && isReactComponent(variableInitializer)) {
                languageData("componentType", "variable")
            }

            if (isReactHook(variable)) {
                languageData("hookType", "custom")
            }

            if (hasDeprecatedAnnotation(variable)) {
                deprecated()
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

            field.typeElement?.let { fieldTypeElement ->
                type(
                    TypeInfo(
                        displayName = fieldTypeElement.text,
                        baseType = fieldTypeElement.text,
                        typeParameters = null,
                        isArray = fieldTypeElement.text.contains("[]"),
                        rawType = fieldTypeElement.text
                    )
                )
            }

            visibility(getJSFieldVisibility(field))

            getDocComment(field)?.let { docComment ->
                documentation(
                    present = true,
                    summary = extractJSDocSummary(docComment),
                    format = "jsdoc"
                )
            }

            field.initializer?.let { fieldInitializer ->
                if (fieldInitializer is JSLiteralExpression) {
                    languageData("initialValue", fieldInitializer.text)
                }
            }

            if (hasDeprecatedAnnotation(field)) {
                deprecated()
            }
        }
    }

    private fun extractImportInfo(importStatement: JSImportStatement): SymbolInfo {
        return symbol {
            val importStatementText = importStatement.text.trim()
            val extractedImportName = extractImportName(importStatementText)
            
            name(extractedImportName)
            kind("import")

            location(
                importStatement.textRange.startOffset,
                importStatement.textRange.endOffset,
                getLineNumber(importStatement) + 1
            )

            val importTypeFromText = when {
                importStatementText.contains("import * as") -> "namespace"
                importStatementText.contains("import {") -> "named"
                importStatementText.contains("import type") -> "type_only"
                else -> "default"
            }
            languageData("importType", importTypeFromText)

            val sourceModuleMatch = Regex("""from\s+['"]([^'"]+)['"]""").find(importStatementText)
            sourceModuleMatch?.groupValues?.get(1)?.let { sourceModule ->
                languageData("from", sourceModule)
            }

            visibility("public")
        }
    }

    private fun extractImportName(importText: String): String {
        return when {
            importText.contains("import * as") -> {
                val namespaceMatch = Regex("""import\s+\*\s+as\s+(\w+)""").find(importText)
                namespaceMatch?.groupValues?.get(1) ?: "namespace"
            }
            importText.contains("import {") -> {
                val namedImportsMatch = Regex("""import\s+\{([^}]+)\}""").find(importText)
                val namedImportsList = namedImportsMatch?.groupValues?.get(1)?.trim()?.split(",")?.map { it.trim() }
                namedImportsList?.joinToString(", ") ?: "named imports"
            }
            else -> {
                val defaultImportMatch = Regex("""import\s+(\w+)""").find(importText)
                defaultImportMatch?.groupValues?.get(1) ?: importText.take(50)
            }
        }
    }

    override fun supportsFile(psiFile: PsiFile): Boolean {
        val languageId = psiFile.language.id
        return languageId in setOf("JavaScript", "TypeScript", "JSX", "TSX", "ECMAScript 6")
    }

    override fun getSupportedLanguage(): String {
        return "JavaScript/TypeScript"
    }
    
    override fun getSupportedSymbolTypes(includeFrameworkTypes: Boolean): Set<String> {
        val coreSymbolTypes = mutableSetOf(
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

        if (includeFrameworkTypes) {
            val frameworkSpecificTypes = listOf(
                "component",
                "hook",
                "directive", 
                "mixin",
                "service",
                "pipe"
            )
            coreSymbolTypes.addAll(frameworkSpecificTypes)
        }
        
        return coreSymbolTypes
    }

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
        var currentParent = function.parent
        while (currentParent != null) {
            if (currentParent is JSClass) {
                return true
            }
            currentParent = currentParent.parent
        }
        return false
    }

    private fun isExported(element: PsiElement): Boolean {
        return element.text.startsWith("export") || element.parent?.text?.startsWith("export") == true
    }

    private fun getDocComment(element: PsiElement): JSDocComment? {
        return PsiTreeUtil.getChildOfType(element, JSDocComment::class.java)
    }

    private fun getReturnTypeString(function: JSFunction): String? {
        val functionText = function.text
        val returnTypeMatch = Regex(""":\s*([^{]+?)\s*[{=]""").find(functionText)
        return returnTypeMatch?.groupValues?.get(1)?.trim()
    }

    private fun isReactComponent(element: PsiElement): Boolean {
        return when (element) {
            is JSFunction -> {
                val functionName = element.name
                functionName != null && functionName.isNotEmpty() && functionName[0].isUpperCase() && hasJSXInBody(element)
            }

            is JSVariable -> {
                val variableName = element.name
                variableName != null && variableName.isNotEmpty() && variableName[0].isUpperCase() &&
                        element.initializer?.text?.contains("React") == true
            }

            is JSClass -> {
                val className = element.name
                className != null && className.isNotEmpty() && className[0].isUpperCase()
            }

            else -> false
        }
    }

    private fun isReactHook(element: PsiElement): Boolean {
        val elementName = when (element) {
            is JSFunction -> element.name
            is JSVariable -> element.name
            else -> null
        }
        return elementName != null && elementName.startsWith("use") && elementName.length > 3 && elementName[3].isUpperCase()
    }

    private fun hasJSXInBody(function: JSFunction): Boolean {
        val functionBodyText = function.block?.text ?: ""
        return functionBodyText.contains("<") && functionBodyText.contains("/>")
    }

    private fun hasDeprecatedAnnotation(element: PsiElement): Boolean {
        val elementDocComment = getDocComment(element)
        return elementDocComment?.text?.contains("@deprecated") == true
    }

    private fun extractJSDocSummary(docComment: JSDocComment): String {
        val docCommentText = docComment.text
        val firstSentenceFromDoc = docCommentText.substringBefore(". ").trim()
        return if (firstSentenceFromDoc.isNotEmpty()) "$firstSentenceFromDoc." else docCommentText.take(100)
    }

    private fun buildFunctionSignature(function: JSFunction): String {
        val parametersWithTypes = function.parameters.joinToString(", ") { param ->
            val parameterType = param.typeElement?.text ?: "any"
            "${param.name}: $parameterType"
        }
        val functionReturnType = getReturnTypeString(function) ?: "void"
        val functionName = function.name ?: "anonymous"
        return "$functionName($parametersWithTypes): $functionReturnType"
    }

    private fun extractNameFromPattern(text: String, pattern: String): String? {
        val patternMatch = Regex(pattern).find(text)
        return patternMatch?.groupValues?.get(1)
    }

    private fun extractExtendsClause(text: String): String? {
        val extendsMatch = Regex("""extends\s+([^{]+)""").find(text)
        return extendsMatch?.groupValues?.get(1)?.trim()
    }

    private fun extractTypeParametersFromText(text: String, keyword: String): String? {
        val typeParametersMatch = Regex("""$keyword\s+\w+<([^>]+)>""").find(text)
        return typeParametersMatch?.groupValues?.get(1)?.trim()
    }

    private fun shouldIncludeSymbol(symbolType: String, filterTypes: List<String>?): Boolean {
        return filterTypes == null || filterTypes.contains(symbolType)
    }

    private fun shouldIncludeSymbol(symbol: SymbolInfo, args: GetSymbolsArgs): Boolean {
        if (!args.includePrivate && symbol.visibility == Visibility.PRIVATE) {
            return false
        }

        if (!args.includeGenerated && symbol.isSynthetic) {
            return false
        }

        return true
    }
}
