package dev.mcp.extensions.lsp.languages.java

import com.intellij.openapi.components.Service
import com.intellij.psi.*
import com.intellij.psi.javadoc.PsiDocComment
import dev.mcp.extensions.lsp.core.interfaces.SymbolExtractor
import dev.mcp.extensions.lsp.core.models.GetSymbolsArgs
import dev.mcp.extensions.lsp.core.models.SymbolInfo
import dev.mcp.extensions.lsp.core.models.TypeInfo
import dev.mcp.extensions.lsp.core.models.Visibility
import dev.mcp.extensions.lsp.core.utils.symbol
import dev.mcp.extensions.lsp.languages.base.BaseLanguageHandler

/**
 * Symbol extractor implementation for Java and Kotlin languages.
 *
 * Registered as a service in mcp-lsp-java.xml when Java module is available.
 */
@Service
class JavaSymbolExtractor : BaseLanguageHandler(), SymbolExtractor {

    override fun extractSymbolsFlat(psiFile: PsiFile, args: GetSymbolsArgs): List<SymbolInfo> {
        logger.debug("Extracting symbols (flat) from Java/Kotlin file: ${psiFile.name}")

        val symbols = mutableListOf<SymbolInfo>()

        // Visit all PSI elements and extract symbols
        psiFile.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    is PsiClass -> {
                        if (shouldIncludeSymbol(getClassKind(element), args.symbolTypes)) {
                            val symbol = extractClassInfo(element, includeChildren = false)
                            if (shouldIncludeSymbol(symbol, args)) {
                                symbols.add(symbol)
                            }
                        }
                    }

                    is PsiMethod -> {
                        val kind = if (element.isConstructor) "constructor" else "method"
                        if (shouldIncludeSymbol(kind, args.symbolTypes)) {
                            val symbol = extractMethodInfo(element)
                            if (shouldIncludeSymbol(symbol, args)) {
                                symbols.add(symbol)
                            }
                        }
                    }

                    is PsiField -> {
                        // Determine the actual kind before filtering
                        val actualKind = when {
                            element is PsiEnumConstant -> "enum_member"
                            element.hasModifierProperty(PsiModifier.FINAL) &&
                                    element.hasModifierProperty(PsiModifier.STATIC) -> "constant"

                            else -> "field"
                        }
                        if (shouldIncludeSymbol(actualKind, args.symbolTypes)) {
                            val symbol = extractFieldInfo(element)
                            if (shouldIncludeSymbol(symbol, args)) {
                                symbols.add(symbol)
                            }
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
        logger.debug("Extracting symbols (hierarchical) from Java/Kotlin file: ${psiFile.name}")

        val symbols = mutableListOf<SymbolInfo>()

        // Process imports first if requested
        if (args.includeImports && shouldIncludeSymbol("import", args.symbolTypes)) {
            psiFile.children.filterIsInstance<PsiImportList>().forEach { importList ->
                importList.importStatements.forEach { import ->
                    symbols.add(extractImportInfo(import))
                }
            }
        }

        // Process top-level classes directly from file children
        // In Java files, classes can be direct children or wrapped in other PSI elements
        val classes = mutableListOf<PsiClass>()
        psiFile.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is PsiClass) {
                    // Check if this is a top-level class (not inner/nested)
                    var parent = element.parent
                    var isTopLevel = false

                    // Walk up the parent chain to see if we reach the PsiFile
                    while (parent != null && parent !is PsiClass) {
                        if (parent is PsiFile) {
                            isTopLevel = true
                            break
                        }
                        parent = parent.parent
                    }

                    if (isTopLevel && !classes.contains(element)) {
                        classes.add(element)
                        logger.debug("Found top-level class: ${element.name}")
                    }
                }
                super.visitElement(element)
            }
        })

        // Process each top-level class
        classes.forEach { psiClass ->
            val kind = getClassKind(psiClass)
            logger.debug("Hierarchical: Processing class ${psiClass.name} (${kind})")
            if (shouldIncludeSymbol(kind, args.symbolTypes)) {
                logger.debug("Hierarchical: Symbol type matches, extracting...")
                val symbol = extractClassInfo(psiClass, includeChildren = true, args = args)
                if (shouldIncludeSymbol(symbol, args)) {
                    logger.debug("Hierarchical: Adding ${symbol.name} with ${symbol.children?.size ?: 0} children")
                    symbols.add(symbol)
                }
            }
        }

        logger.debug("Extracted ${symbols.size} Hierarchical symbols total")
        return symbols
    }

    override fun supportsFile(psiFile: PsiFile): Boolean {
        val languageId = psiFile.language.id
        return languageId == "JAVA" || languageId == "kotlin" || languageId == "Kotlin"
    }

    override fun getSupportedLanguage(): String {
        return "Java/Kotlin"
    }
    
    override fun getSupportedSymbolTypes(includeFrameworkTypes: Boolean): Set<String> {
        return setOf(
            "class",
            "interface", 
            "enum",
            "annotation",
            "method",
            "constructor",
            "field",
            "constant",
            "enum_member",
            "variable",
            "parameter",
            "property",
            "import",
            "package",
            "module",
            "type_parameter"
        )
    }

    private fun extractClassInfo(
        psiClass: PsiClass,
        includeChildren: Boolean = false,
        args: GetSymbolsArgs? = null
    ): SymbolInfo {
        return symbol {
            name(psiClass.name ?: "anonymous")
            qualifiedName(psiClass.qualifiedName)
            kind(getClassKind(psiClass))

            location(
                psiClass.textRange.startOffset,
                psiClass.textRange.endOffset,
                getLineNumber(psiClass) + 1
            )

            // Add modifiers
            psiClass.modifierList?.let { modifiers ->
                extractModifiers(modifiers).forEach { modifier(it) }
            }

            // Visibility
            visibility(getVisibility(psiClass.modifierList))

            // Documentation
            psiClass.docComment?.let {
                documentation(
                    present = true,
                    summary = extractDocSummary(it),
                    format = "javadoc"
                )
            }

            // Annotations as decorators
            getAnnotations(psiClass).forEach { annotation ->
                decorator(
                    annotation.removePrefix("@"),
                    builtin = isBuiltinAnnotation(annotation)
                )
            }

            // Type parameters
            if (psiClass.hasTypeParameters()) {
                val typeParams = psiClass.typeParameters.joinToString(", ") { it.name ?: "?" }
                languageData("typeParameters", typeParams)
            }

            // Extends/implements
            psiClass.extendsList?.referencedTypes?.forEach { superType ->
                languageData("extends", superType.presentableText)
            }

            psiClass.implementsList?.referencedTypes?.forEach { interfaceType ->
                implements(interfaceType.presentableText)
            }

            // Add children if requested
            if (includeChildren && args != null) {
                val childSymbols = mutableListOf<SymbolInfo>()

                // Add inner classes
                println("DEBUG extractClassInfo: Building children for ${psiClass.name}, inner classes: ${psiClass.innerClasses.size}")
                psiClass.innerClasses.forEach { innerClass ->
                    val innerKind = getClassKind(innerClass)
                    if (shouldIncludeSymbol(innerKind, args.symbolTypes)) {
                        val innerSymbol = extractClassInfo(innerClass, includeChildren = true, args = args)
                        if (shouldIncludeSymbol(innerSymbol, args)) {
                            childSymbols.add(innerSymbol)
                        }
                    }
                }

                // Add methods
                println("DEBUG extractClassInfo: Methods: ${psiClass.methods.size}")
                psiClass.methods.forEach { method ->
                    val methodKind = if (method.isConstructor) "constructor" else "method"
                    if (shouldIncludeSymbol(methodKind, args.symbolTypes)) {
                        val methodSymbol = extractMethodInfo(method)
                        if (shouldIncludeSymbol(methodSymbol, args)) {
                            childSymbols.add(methodSymbol)
                        }
                    }
                }

                // Add fields
                println("DEBUG extractClassInfo: Fields: ${psiClass.fields.size}")
                psiClass.fields.forEach { field ->
                    // Determine the actual kind before filtering
                    val actualKind = when {
                        field is PsiEnumConstant -> "enum_member"
                        field.hasModifierProperty(PsiModifier.FINAL) &&
                                field.hasModifierProperty(PsiModifier.STATIC) -> "constant"

                        else -> "field"
                    }
                    if (shouldIncludeSymbol(actualKind, args.symbolTypes)) {
                        val fieldSymbol = extractFieldInfo(field)
                        if (shouldIncludeSymbol(fieldSymbol, args)) {
                            childSymbols.add(fieldSymbol)
                        }
                    }
                }

                println("DEBUG extractClassInfo: Total children for ${psiClass.name}: ${childSymbols.size}")
                children(childSymbols)
            } else {
                println("DEBUG extractClassInfo: Not including children for ${psiClass.name}: includeChildren=$includeChildren, args=${args != null}")
            }

            // Mark as deprecated if needed
            if (hasAnnotation(psiClass, "java.lang.Deprecated")) {
                deprecated()
            }
        }
    }

    private fun extractMethodInfo(psiMethod: PsiMethod): SymbolInfo {
        return symbol {
            name(psiMethod.name)
            qualifiedName("${psiMethod.containingClass?.qualifiedName ?: "unknown"}.${psiMethod.name}")
            kind(if (psiMethod.isConstructor) "constructor" else "method")

            location(
                psiMethod?.textRange?.startOffset ?: 0,
                psiMethod?.textRange?.endOffset ?: 0,
                getLineNumber(psiMethod) + 1
            )

            // Modifiers
            extractModifiers(psiMethod.modifierList).forEach { modifier(it) }

            // Type info for return type
            psiMethod.returnType?.let { returnType ->
                type(
                    TypeInfo(
                        displayName = returnType.presentableText,
                        baseType = extractBaseType(returnType),
                        typeParameters = extractTypeParameters(returnType),
                        isArray = returnType is PsiArrayType,
                        rawType = returnType.canonicalText
                    )
                )
            }

            // Signature
            signature(buildMethodSignature(psiMethod))

            // Visibility
            visibility(getVisibility(psiMethod.modifierList))

            // Documentation
            psiMethod.docComment?.let {
                documentation(
                    present = true,
                    summary = extractDocSummary(it),
                    format = "javadoc"
                )
            }

            val explictOverride = hasAnnotation(psiMethod, "Override")
            val superMethods = psiMethod.findSuperMethods()
            if (superMethods.isNotEmpty()) {
                val superMethod = superMethods.first()
                superMethod.containingClass?.let { superClass ->
                    overrides(
                        superClass.qualifiedName ?: superClass.name ?: "Unknown",
                        superMethod.name,
                        explictOverride
                    )
                }
            } else if (explictOverride) {
                // workaround for hidden java.lang.Object methods
                overrides("java.lang.Object", "toString", true)
            }

            // Annotations as decorators
            getAnnotations(psiMethod).forEach { annotation ->
                decorator(
                    annotation.removePrefix("@"),
                    builtin = isBuiltinAnnotation(annotation)
                )
            }

            // Parameters as language data
            val params = psiMethod.parameterList.parameters.map { param ->
                "${param.type.presentableText} ${param.name}"
            }
            if (params.isNotEmpty()) {
                languageData("parameters", params.joinToString(", "))
            }
            languageData("parameterCount", psiMethod.parameterList.parametersCount.toString())
            languageData("hasVarArgs", psiMethod.isVarArgs.toString())

            // Throws clause
            val throwsList = psiMethod.throwsList.referencedTypes
            if (throwsList.isNotEmpty()) {
                languageData("throws", throwsList.joinToString(", ") { it.presentableText })
            }

            // Mark as deprecated if needed
            if (psiMethod.isDeprecated || hasAnnotation(psiMethod, "Deprecated")) {
                deprecated()
            }
        }
    }

    private fun extractFieldInfo(psiField: PsiField): SymbolInfo {
        return symbol {
            name(psiField.name ?: "anonymous")
            qualifiedName("${psiField.containingClass?.qualifiedName}.${psiField.name}")

            // Determine kind
            kind(
                when {
                    psiField is PsiEnumConstant -> "enum_member"
                    psiField.hasModifierProperty(PsiModifier.FINAL) &&
                            psiField.hasModifierProperty(PsiModifier.STATIC) -> "constant" // LSP 14: static finals are constants
                    else -> "field"
                }
            )

            location(
                psiField.textRange.startOffset,
                psiField.textRange.endOffset,
                getLineNumber(psiField) + 1
            )

            // Type info
            type(
                TypeInfo(
                    displayName = psiField.type.presentableText,
                    baseType = extractBaseType(psiField.type),
                    typeParameters = extractTypeParameters(psiField.type),
                    isArray = psiField.type is PsiArrayType,
                    rawType = psiField.type.canonicalText
                )
            )

            // Modifiers
            psiField.modifierList?.let { modifiers ->
                extractModifiers(modifiers).forEach { modifier(it) }
                if (modifiers.hasModifierProperty(PsiModifier.VOLATILE)) modifier("volatile")
                if (modifiers.hasModifierProperty(PsiModifier.TRANSIENT)) modifier("transient")
            }

            // Visibility
            visibility(getVisibility(psiField.modifierList))

            // Documentation
            psiField.docComment?.let {
                documentation(
                    present = true,
                    summary = extractDocSummary(it),
                    format = "javadoc"
                )
            }

            // Annotations as decorators
            getAnnotations(psiField).forEach { annotation ->
                decorator(
                    annotation.removePrefix("@"),
                    builtin = isBuiltinAnnotation(annotation)
                )
            }

            // Initial value for constants
            if (psiField.hasModifierProperty(PsiModifier.FINAL) && psiField.hasInitializer()) {
                psiField.initializer?.let { initializer ->
                    if (initializer is PsiLiteralExpression) {
                        languageData("initialValue", initializer.text)
                    }
                }
            }

            // Mark as deprecated if needed
            if (psiField.isDeprecated || hasAnnotation(psiField, "Deprecated")) {
                deprecated()
            }
        }
    }

    private fun extractImportInfo(psiImport: PsiImportStatement): SymbolInfo {
        return symbol {
            name(psiImport.qualifiedName ?: "unknown")
            kind("import")

            location(
                psiImport.textRange.startOffset,
                psiImport.textRange.endOffset,
                getLineNumber(psiImport) + 1
            )

            if (psiImport.isOnDemand) {
                languageData("importType", "wildcard")
            }

            visibility("public") // imports are always public
        }
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

    private fun getClassKind(psiClass: PsiClass): String {
        return when {
            psiClass.isInterface -> "interface"
            psiClass.isEnum -> "enum"
            psiClass.isAnnotationType -> "annotation"
            psiClass is PsiAnonymousClass -> "class" // or "anonymous_class"
            else -> "class"
        }
    }

    private fun extractBaseType(type: PsiType): String {
        return when (type) {
            is PsiArrayType -> extractBaseType(type.componentType)
            is PsiClassType -> type.rawType().presentableText
            is PsiPrimitiveType -> type.presentableText
            else -> type.presentableText
        }
    }

    private fun extractTypeParameters(type: PsiType): List<TypeInfo>? {
        return when (type) {
            is PsiClassType -> {
                val params = type.parameters
                if (params.isNotEmpty()) {
                    params.map { param ->
                        TypeInfo(
                            displayName = param.presentableText,
                            baseType = extractBaseType(param),
                            typeParameters = extractTypeParameters(param),
                            isArray = param is PsiArrayType
                        )
                    }
                } else null
            }

            else -> null
        }
    }

    private fun extractDocSummary(docComment: PsiDocComment): String {
        val descriptionElements = docComment.descriptionElements
        val text = descriptionElements.joinToString("") { it.text }.trim()
        val firstSentence = text.substringBefore(". ").trim()
        return if (firstSentence.isNotEmpty()) "$firstSentence." else text.take(100)
    }

    private fun buildMethodSignature(method: PsiMethod): String {
        val params = method.parameterList.parameters.joinToString(", ") { param ->
            "${param.type.presentableText} ${param.name}"
        }
        val returnType = method.returnType?.presentableText ?: "void"
        return "${method.name}($params): $returnType"
    }

    private fun isBuiltinAnnotation(annotation: String): Boolean {
        val builtin = setOf(
            "@Override", "@Deprecated", "@SuppressWarnings",
            "@FunctionalInterface", "@SafeVarargs"
        )
        return builtin.contains(annotation)
    }
}
