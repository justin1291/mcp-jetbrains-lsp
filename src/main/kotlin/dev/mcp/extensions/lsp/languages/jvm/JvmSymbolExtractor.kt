package dev.mcp.extensions.lsp.languages.jvm

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
import org.jetbrains.uast.*

/**
 * UAST-based symbol extractor for JVM languages (Java, Kotlin, Scala, Groovy).
 * Phase 1 implementation with basic symbol extraction.
 */
@Service
class JvmSymbolExtractor : BaseLanguageHandler(), SymbolExtractor {

    override fun extractSymbolsFlat(psiFile: PsiFile, args: GetSymbolsArgs): List<SymbolInfo> {
        logger.debug("Extracting symbols (flat) from JVM file: ${psiFile.name}")
        
        val uFile = psiFile.toUElementOfType<UFile>() ?: run {
            logger.warn("Could not convert PSI file to UAST: ${psiFile.name}")
            return emptyList()
        }
        
        val symbols = mutableListOf<SymbolInfo>()
        
        // Process imports if requested
        if (args.includeImports && shouldIncludeSymbol("import", args.symbolTypes)) {
            uFile.imports.forEach { import ->
                symbols.add(extractImportSymbol(import))
            }
        }
        
        // Traverse all declarations using recursive visitor
        val visitor = object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val uElement = element.toUElement()
                when (uElement) {
                    is UClass -> {
                        val kind = getClassKind(uElement)
                        if (shouldIncludeSymbol(kind, args.symbolTypes)) {
                            val symbol = extractClassSymbol(uElement, includeChildren = false)
                            if (shouldIncludeSymbol(symbol, args)) {
                                symbols.add(symbol)
                            }
                        }
                    }
                    is UMethod -> {
                        val kind = if (uElement.isConstructor) "constructor" else "method"
                        if (shouldIncludeSymbol(kind, args.symbolTypes)) {
                            val symbol = extractMethodSymbol(uElement)
                            if (shouldIncludeSymbol(symbol, args)) {
                                symbols.add(symbol)
                            }
                        }
                    }
                    is UField -> {
                        val kind = getFieldKind(uElement)
                        if (shouldIncludeSymbol(kind, args.symbolTypes)) {
                            val symbol = extractFieldSymbol(uElement)
                            if (shouldIncludeSymbol(symbol, args)) {
                                symbols.add(symbol)
                            }
                        }
                    }
                }
                super.visitElement(element)
            }
        }
        psiFile.accept(visitor)
        
        logger.debug("Extracted ${symbols.size} flat symbols")
        return symbols
    }

    override fun extractSymbolsHierarchical(psiFile: PsiFile, args: GetSymbolsArgs): List<SymbolInfo> {
        logger.debug("Extracting symbols (hierarchical) from JVM file: ${psiFile.name}")
        
        val uFile = psiFile.toUElementOfType<UFile>() ?: run {
            logger.warn("Could not convert PSI file to UAST: ${psiFile.name}")
            return emptyList()
        }
        
        val symbols = mutableListOf<SymbolInfo>()
        
        // Process imports first if requested
        if (args.includeImports && shouldIncludeSymbol("import", args.symbolTypes)) {
            uFile.imports.forEach { import ->
                symbols.add(extractImportSymbol(import))
            }
        }
        
        // Process top-level classes hierarchically
        uFile.classes.forEach { uClass ->
            val kind = getClassKind(uClass)
            if (shouldIncludeSymbol(kind, args.symbolTypes)) {
                val symbol = extractClassSymbol(uClass, includeChildren = true, args = args)
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
        return languageId in setOf("JAVA", "kotlin", "Kotlin", "Scala", "Groovy")
    }

    override fun getSupportedLanguage(): String {
        return setOf("JAVA", "kotlin", "Kotlin", "Scala", "Groovy").joinToString(",")
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

    private fun extractImportSymbol(uImport: UImportStatement): SymbolInfo {
        return symbol {
            // Get the actual imported name properly using PSI fallback
            val importedName = run {
                // First try UAST
                val uastName = uImport.importReference?.let { ref ->
                    when (ref) {
                        is USimpleNameReferenceExpression -> ref.identifier
                        is UQualifiedReferenceExpression -> {
                            // Try to get from UAST directly, but this may fail
                            try {
                                ref.asRenderString()
                            } catch (e: Exception) {
                                null
                            }
                        }
                        else -> null
                    }
                }
                
                // If UAST fails, use PSI
                uastName ?: run {
                    val psiImport = uImport.sourcePsi as? PsiImportStatement
                    psiImport?.qualifiedName ?: "unknown"
                }
            }
            
            name(importedName)
            kind("import")

            // Location
            val sourcePsi = getSourcePsi(uImport)
            sourcePsi?.textRange?.let { range ->
                location(
                    range.startOffset,
                    range.endOffset,
                    getLineNumber(sourcePsi) + 1
                )
            }

            // Import type
            if (uImport.isOnDemand) {
                languageData("importType", "wildcard")
            }

            visibility("public") // imports are always public
        }
    }

    private fun extractClassSymbol(
        uClass: UClass,
        includeChildren: Boolean = false,
        args: GetSymbolsArgs? = null
    ): SymbolInfo {
        return symbol {
            name(uClass.name ?: "anonymous")
            qualifiedName(uClass.qualifiedName)
            kind(getClassKind(uClass))
            
            // Location from source PSI
            val classSourcePsi = getSourcePsi(uClass)
            classSourcePsi?.textRange?.let { range ->
                location(
                    range.startOffset,
                    range.endOffset,
                    getLineNumber(classSourcePsi) + 1
                )
            }
            
            // Modifiers
            extractModifiers(uClass).forEach { modifier(it) }
            
            // Visibility
            visibility(getVisibility(uClass).name.lowercase())
            
            // Type parameters (basic - extract from PSI)
            val javaPsi = uClass.javaPsi
            if (javaPsi is PsiClass) {
                val typeParams = javaPsi.typeParameters
                if (typeParams.isNotEmpty()) {
                    val paramNames = typeParams.joinToString(", ") { it.name ?: "?" }
                    languageData("typeParameters", paramNames)
                }
            }
            
            // Super types with enhanced inheritance information
            uClass.uastSuperTypes.forEach { superType ->
                val fullyQualifiedName = superType.getQualifiedName() ?: superType.type.canonicalText
                val shortName = superType.type.presentableText
                val psiType = superType.type
                
                if (psiType is PsiClassType && psiType.resolve()?.isInterface == true) {
                    // Use fully qualified name as primary (more precise)
                    implements(fullyQualifiedName)
                    // Also provide short name for compatibility
                    if (fullyQualifiedName != shortName) {
                        languageData("implementsShort", shortName)
                    }
                } else {
                    // Use fully qualified name as primary (more precise)
                    languageData("extends", fullyQualifiedName)
                    // Also provide short name for compatibility
                    if (fullyQualifiedName != shortName) {
                        languageData("extendsShort", shortName)
                    }
                }
            }
            
            // Language-specific detection using text analysis
            val classTextSource = uClass.sourcePsi
            val sourceText = classTextSource?.text ?: ""
            val languageId = classTextSource?.language?.id?.lowercase() ?: "java"
            
            // Kotlin-specific patterns
            if (languageId == "kotlin") {
                when {
                    sourceText.contains("data class") -> languageData("kotlinType", "data_class")
                    sourceText.contains("sealed class") -> languageData("kotlinType", "sealed_class")
                    sourceText.contains("object ") -> languageData("kotlinType", "object")
                    sourceText.contains("companion object") -> languageData("kotlinType", "companion_object")
                }
            }
            
            // Scala-specific patterns
            if (languageId == "scala") {
                when {
                    sourceText.contains("trait ") -> languageData("scalaType", "trait")
                    sourceText.contains("case class") -> languageData("scalaType", "case_class")
                    sourceText.contains("object ") -> languageData("scalaType", "object")
                }
            }
            
            // Groovy-specific patterns
            if (languageId == "groovy") {
                if (sourceText.contains("class ") && classTextSource?.containingFile?.name?.endsWith(".groovy") == true) {
                    languageData("groovyType", "script")
                }
            }
            
            // Add children if requested
            if (includeChildren && args != null) {
                val childSymbols = mutableListOf<SymbolInfo>()
                
                // Add inner classes
                uClass.innerClasses.forEach { innerClass ->
                    val innerKind = getClassKind(innerClass)
                    if (shouldIncludeSymbol(innerKind, args.symbolTypes)) {
                        val innerSymbol = extractClassSymbol(innerClass, includeChildren = true, args = args)
                        if (shouldIncludeSymbol(innerSymbol, args)) {
                            childSymbols.add(innerSymbol)
                        }
                    }
                }
                
                // Add methods (including language-specific ones)
                uClass.methods.forEach { method ->
                    val methodKind = if (method.isConstructor) "constructor" else "method"
                    if (shouldIncludeSymbol(methodKind, args.symbolTypes)) {
                        var methodSymbol = extractMethodSymbol(method)
                        if (shouldIncludeSymbol(methodSymbol, args)) {
                            // Add language-specific method annotations by rebuilding the symbol
                            val methodText = method.sourcePsi?.text ?: ""
                            if (languageId == "kotlin") {
                                methodSymbol = symbol {
                                    // Copy existing symbol properties
                                    name(methodSymbol.name)
                                    qualifiedName(methodSymbol.qualifiedName)
                                    kind(methodSymbol.kind.value)
                                    location(methodSymbol.location.startOffset, methodSymbol.location.endOffset, methodSymbol.location.lineNumber)
                                    modifiers(methodSymbol.modifiers)
                                    visibility(methodSymbol.visibility.name.lowercase())
                                    methodSymbol.typeInfo?.let { type(it) }
                                    methodSymbol.signature?.let { signature(it) }
                                    
                                    // Add existing language data
                                    methodSymbol.languageData?.forEach { (key, value) ->
                                        languageData(key, value)
                                    }
                                    
                                    // Add Kotlin-specific features
                                    when {
                                        methodText.contains("suspend ") -> languageData("kotlinFeature", "suspend_function")
                                        methodText.contains("inline ") -> languageData("kotlinFeature", "inline_function")
                                        methodText.contains("fun ") && methodText.contains(".") -> languageData("kotlinFeature", "extension_function")
                                    }
                                }
                            }
                            childSymbols.add(methodSymbol)
                        }
                    }
                }
                
                // Add fields (including language-specific ones)
                uClass.fields.forEach { field ->
                    val fieldKind = getFieldKind(field)
                    if (shouldIncludeSymbol(fieldKind, args.symbolTypes)) {
                        var fieldSymbol = extractFieldSymbol(field)
                        if (shouldIncludeSymbol(fieldSymbol, args)) {
                            // Add language-specific field annotations by rebuilding the symbol
                            val fieldText = field.sourcePsi?.text ?: ""
                            if (languageId == "kotlin") {
                                fieldSymbol = symbol {
                                    // Copy existing symbol properties
                                    name(fieldSymbol.name)
                                    qualifiedName(fieldSymbol.qualifiedName)
                                    kind(fieldSymbol.kind.value)
                                    location(fieldSymbol.location.startOffset, fieldSymbol.location.endOffset, fieldSymbol.location.lineNumber)
                                    modifiers(fieldSymbol.modifiers)
                                    visibility(fieldSymbol.visibility.name.lowercase())
                                    fieldSymbol.typeInfo?.let { type(it) }
                                    
                                    // Add existing language data
                                    fieldSymbol.languageData?.forEach { (key, value) ->
                                        languageData(key, value)
                                    }
                                    
                                    // Add Kotlin-specific features
                                    when {
                                        fieldText.contains("by lazy") -> languageData("kotlinFeature", "lazy_property")
                                        fieldText.contains("by ") -> languageData("kotlinFeature", "delegated_property")
                                        fieldText.contains("val ") || fieldText.contains("var ") -> languageData("kotlinFeature", "property")
                                    }
                                }
                            }
                            childSymbols.add(fieldSymbol)
                        }
                    }
                }
                
                children(childSymbols)
            }
            
            // Basic flags using PSI
            val psiClass = javaPsi as PsiClass
            if (psiClass.isInterface) {
                languageData("type", "interface")
            } else if (psiClass.isEnum) {
                languageData("type", "enum")
            } else if (psiClass.isAnnotationType) {
                languageData("type", "annotation")
            }
            
            // Check for abstract
            if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                abstract()
            }
            
            // Check for static (inner classes)
            if (psiClass.hasModifierProperty(PsiModifier.STATIC)) {
                static()
            }

            // Annotations as decorators
            psiClass.annotations.forEach { annotation ->
                val annotationName = annotation.nameReferenceElement?.referenceName ?: "Unknown"
                decorator(
                    annotationName,
                    builtin = isBuiltinAnnotation(annotationName)
                )
                // Set deprecated flag if @Deprecated annotation is present
                if (annotationName == "Deprecated") {
                    deprecated(true)
                }
            }
            
            // Documentation extraction using PSI with proper format
            val docComment = extractDocumentation(uClass, javaPsi)
            if (docComment != null) {
                documentation(present = true, summary = docComment, format = "javadoc")
            }
        }
    }

    private fun extractMethodSymbol(uMethod: UMethod): SymbolInfo {
        return symbol {
            name(uMethod.name)
            val containingClass = uMethod.uastParent as? UClass
            qualifiedName("${containingClass?.qualifiedName ?: "unknown"}.${uMethod.name}")
            kind(if (uMethod.isConstructor) "constructor" else "method")
            
            // Location
            val sourcePsi = getSourcePsi(uMethod)
            sourcePsi?.textRange?.let { range ->
                location(
                    range.startOffset,
                    range.endOffset,
                    getLineNumber(sourcePsi) + 1
                )
            }
            
            // Modifiers
            extractModifiers(uMethod).forEach { modifier(it) }
            
            // Return type
            uMethod.returnType?.let { returnType ->
                type(
                    TypeInfo(
                        displayName = returnType.presentableText,
                        baseType = extractBaseType(returnType),
                        isArray = returnType is PsiArrayType,
                        rawType = returnType.canonicalText
                    )
                )
            }
            
            // Basic signature
            signature(buildMethodSignature(uMethod))
            
            // Visibility
            visibility(getVisibility(uMethod).name.lowercase())
            
            // Parameters with enhanced metadata
            val params = uMethod.uastParameters.map { param ->
                "${param.type.presentableText} ${param.name}"
            }
            if (params.isNotEmpty()) {
                languageData("parameters", params.joinToString(", "))
            }
            languageData("parameterCount", uMethod.uastParameters.size.toString())
            
            // Type parameters using PSI
            val javaPsi = uMethod.javaPsi
            val psiMethod = javaPsi as PsiMethod
            
            // VarArgs detection using PSI method  
            languageData("hasVarArgs", psiMethod.isVarArgs.toString())
            
            // Throws clause using PSI method
            val throwsList = psiMethod.throwsList.referencedTypes
            if (throwsList.isNotEmpty()) {
                languageData("throws", throwsList.joinToString(", ") { it.presentableText })
            }
            
            val typeParams = psiMethod.typeParameters
            if (typeParams.isNotEmpty()) {
                val paramNames = typeParams.joinToString(", ") { it.name ?: "?" }
                languageData("typeParameters", paramNames)
            }
            
            // Override/implementation detection
            if (psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
                abstract()
            }
            if (psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
                static()
            }
            
            // Override detection matching Java implementation logic
            val explicitOverride = psiMethod.hasAnnotation("Override") || psiMethod.hasAnnotation("java.lang.Override")
            val superMethods = psiMethod.findSuperMethods()
            
            if (superMethods.isNotEmpty()) {
                val superMethod = superMethods.first()
                superMethod.containingClass?.let { superClass ->
                    overrides(
                        superClass.qualifiedName ?: superClass.name ?: "Unknown",
                        superMethod.name,
                        explicitOverride
                    )
                }
                // Also keep language data for backwards compatibility
                languageData("overrides", superMethods.mapNotNull { it.name }.joinToString(", "))
            } else if (explicitOverride) {
                // workaround for hidden java.lang.Object methods (matching Java implementation)
                overrides("java.lang.Object", uMethod.name, true)
            }
            if (superMethods.any { it.containingClass?.isInterface == true }) {
                languageData("implements", "true")
            }

            // Annotations as decorators
            psiMethod.annotations.forEach { annotation ->
                val annotationName = annotation.nameReferenceElement?.referenceName ?: "Unknown"
                decorator(
                    annotationName,
                    builtin = isBuiltinAnnotation(annotationName)
                )
                // Set deprecated flag if @Deprecated annotation is present
                if (annotationName == "Deprecated") {
                    deprecated(true)
                }
            }
            
            // Documentation extraction with proper format
            val docComment = extractDocumentation(uMethod, javaPsi)
            if (docComment != null) {
                documentation(present = true, summary = docComment, format = "javadoc")
            }
            
            // Check for Kotlin suspend functions
            if (uMethod.sourcePsi?.text?.contains("suspend") == true) {
                languageData("suspend", "true")
            }
        }
    }

    private fun extractFieldSymbol(uField: UField): SymbolInfo {
        return symbol {
            name(uField.name)
            val containingClass = uField.uastParent as? UClass
            qualifiedName("${containingClass?.qualifiedName ?: "unknown"}.${uField.name}")
            kind(getFieldKind(uField))
            
            // Location
            val sourcePsi = getSourcePsi(uField)
            sourcePsi?.textRange?.let { range ->
                location(
                    range.startOffset,
                    range.endOffset,
                    getLineNumber(sourcePsi) + 1
                )
            }
            
            // Type info
            uField.type.let { fieldType ->
                type(
                    TypeInfo(
                        displayName = fieldType.presentableText,
                        baseType = extractBaseType(fieldType),
                        isArray = fieldType is PsiArrayType,
                        rawType = fieldType.canonicalText
                    )
                )
            }
            
            // Modifiers
            extractModifiers(uField).forEach { modifier(it) }
            
            // Visibility
            visibility(getVisibility(uField).name.lowercase())
            
            // Basic flags
            val javaPsi = uField.javaPsi
            if (javaPsi is PsiField && javaPsi.hasModifierProperty(PsiModifier.STATIC)) {
                static()
            }
            
            // Check for constants
            if (javaPsi is PsiField && 
                javaPsi.hasModifierProperty(PsiModifier.FINAL) && 
                javaPsi.hasModifierProperty(PsiModifier.STATIC)) {
                // For constants, try to get the initial value
                uField.uastInitializer?.let { initializer ->
                    if (initializer is ULiteralExpression) {
                        languageData("initialValue", initializer.asRenderString())
                    }
                }
            }

            // Annotations as decorators
            if (javaPsi is PsiField) {
                javaPsi.annotations.forEach { annotation ->
                    val annotationName = annotation.nameReferenceElement?.referenceName ?: "Unknown"
                    decorator(
                        annotationName,
                        builtin = isBuiltinAnnotation(annotationName)
                    )
                    // Set deprecated flag if @Deprecated annotation is present
                    if (annotationName == "Deprecated") {
                        deprecated(true)
                    }
                }
            }
            
            // Documentation extraction with proper format
            val docComment = extractDocumentation(uField, javaPsi)
            if (docComment != null) {
                documentation(present = true, summary = docComment, format = "javadoc")
            }
        }
    }

    // Helper method to extract documentation using the same approach as HoverInfoProvider
    private fun extractDocumentation(uElement: UElement?, psiElement: PsiElement?): String? {
        // Try to get documentation from UAST comments first
        val uastDoc = uElement?.comments?.joinToString("\n") { it.text }?.trim()
        if (!uastDoc.isNullOrBlank()) {
            return parseDocumentationText(uastDoc)
        }

        // Fallback to PSI documentation
        val psiDocOwner = psiElement as? PsiDocCommentOwner
        val docComment = psiDocOwner?.docComment
        if (docComment != null) {
            return extractJavaDocSummary(docComment)
        }

        return null
    }

    private fun extractJavaDocSummary(docComment: PsiDocComment): String? {
        return try {
            // Extract description elements for structured JavaDoc
            val descriptionElements = docComment.descriptionElements
            if (descriptionElements.isNotEmpty()) {
                val summary = descriptionElements.joinToString("") { it.text }.trim()
                    .replace(Regex("\\s+"), " ") // Normalize whitespace
                    .let { text ->
                        // Get first sentence
                        val firstSentence = text.substringBefore(". ")
                        if (firstSentence.length < text.length) "$firstSentence." else firstSentence
                    }
                
                // Return summary if it's not blank
                summary.takeIf { it.isNotBlank() }
            } else {
                // Fallback to comment text
                val commentText = docComment.text
                if (commentText.isNotBlank()) {
                    // Extract text between /** and */
                    val cleanText = commentText
                        .removePrefix("/**")
                        .removeSuffix("*/")
                        .lines()
                        .joinToString(" ") { line ->
                            line.trim().removePrefix("*").trim()
                        }
                        .trim()
                    
                    if (cleanText.isNotBlank()) cleanText else null
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            logger.debug("Error extracting JavaDoc summary: ${e.message}")
            null
        }
    }

    private fun parseDocumentationText(docText: String): String? {
        val lines = docText.lines()
        val builder = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("@since") -> continue
                trimmed.startsWith("@see") -> continue
                else -> {
                    builder.append(line).append("\n")
                }
            }
        }

        return builder.toString().trim().takeIf { it.isNotEmpty() }
    }

    // Utility methods
    
    private fun getSourcePsi(uElement: UElement): PsiElement? {
        return uElement.sourcePsi ?: uElement.javaPsi
    }
    
    private fun getClassKind(uClass: UClass): String {
        return when {
            uClass.isInterface -> "interface"
            uClass.isEnum -> "enum"
            uClass.isAnnotationType -> "annotation"
            else -> "class"
        }
    }
    
    private fun getFieldKind(uField: UField): String {
        val javaPsi = uField.javaPsi
        return when {
            javaPsi is PsiEnumConstant -> "enum_member"
            javaPsi is PsiField && 
                javaPsi.hasModifierProperty(PsiModifier.FINAL) && 
                javaPsi.hasModifierProperty(PsiModifier.STATIC) -> "constant"
            else -> "field"
        }
    }
    
    private fun extractModifiers(uElement: UDeclaration): Set<String> {
        val modifiers = mutableSetOf<String>()
        val javaPsi = uElement.javaPsi
        
        if (javaPsi is PsiModifierListOwner) {
            if (javaPsi.hasModifierProperty(PsiModifier.PUBLIC)) modifiers.add("public")
            if (javaPsi.hasModifierProperty(PsiModifier.PRIVATE)) modifiers.add("private")
            if (javaPsi.hasModifierProperty(PsiModifier.PROTECTED)) modifiers.add("protected")
            if (javaPsi.hasModifierProperty(PsiModifier.STATIC)) modifiers.add("static")
            if (javaPsi.hasModifierProperty(PsiModifier.FINAL)) modifiers.add("final")
            if (javaPsi.hasModifierProperty(PsiModifier.ABSTRACT)) modifiers.add("abstract")
            if (javaPsi.hasModifierProperty(PsiModifier.SYNCHRONIZED)) modifiers.add("synchronized")
            if (javaPsi.hasModifierProperty(PsiModifier.NATIVE)) modifiers.add("native")
            if (javaPsi.hasModifierProperty(PsiModifier.VOLATILE)) modifiers.add("volatile")
            if (javaPsi.hasModifierProperty(PsiModifier.TRANSIENT)) modifiers.add("transient")
        }
        
        return modifiers
    }
    
    private fun getVisibility(uElement: UDeclaration): Visibility {
        val javaPsi = uElement.javaPsi
        
        return when {
            javaPsi is PsiModifierListOwner -> {
                when {
                    javaPsi.hasModifierProperty(PsiModifier.PUBLIC) -> Visibility.PUBLIC
                    javaPsi.hasModifierProperty(PsiModifier.PRIVATE) -> Visibility.PRIVATE
                    javaPsi.hasModifierProperty(PsiModifier.PROTECTED) -> Visibility.PROTECTED
                    // Kotlin internal modifier
                    uElement.sourcePsi?.text?.contains("internal") == true -> Visibility.INTERNAL
                    else -> Visibility.PACKAGE // Use PACKAGE instead of DEFAULT for Java package-private
                }
            }
            else -> Visibility.PACKAGE
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
    
    private fun buildMethodSignature(uMethod: UMethod): String {
        val params = uMethod.uastParameters.joinToString(", ") { param ->
            "${param.type?.presentableText ?: "?"} ${param.name}"
        }
        val returnType = uMethod.returnType?.presentableText ?: "void"
        return "${uMethod.name}($params): $returnType"
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

    private fun isBuiltinAnnotation(annotationName: String): Boolean {
        val builtin = setOf(
            "Override", "Deprecated", "SuppressWarnings", "FunctionalInterface", "SafeVarargs",
            "Generated", "Resource", "Nullable", "NotNull", "ThreadSafe", "Immutable"
        )
        return builtin.contains(annotationName)
    }
}
