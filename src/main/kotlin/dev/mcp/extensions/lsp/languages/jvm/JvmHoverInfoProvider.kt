package dev.mcp.extensions.lsp.languages.jvm

import com.intellij.openapi.components.Service
import com.intellij.psi.*
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import dev.mcp.extensions.lsp.core.interfaces.HoverInfoProvider
import dev.mcp.extensions.lsp.core.models.HoverInfo
import dev.mcp.extensions.lsp.languages.base.BaseLanguageHandler
import org.jetbrains.uast.*

/**
 * UAST-based hover info provider for JVM languages (Java, Kotlin, Scala, Groovy).
 * Provides comprehensive hover information with cross-language support.
 */
@Service
class JvmHoverInfoProvider : BaseLanguageHandler(), HoverInfoProvider {

    override fun getHoverInfoAtPosition(psiFile: PsiFile, position: Int): HoverInfo? {
        logger.info("Getting hover info at position $position in ${psiFile.name}")

        val element = psiFile.findElementAt(position) ?: return null

        // Try to resolve reference first
        val reference = element.parent?.reference ?: element.reference
        val resolved = reference?.resolve()

        // Get the most relevant element
        val targetElement = resolved
            ?: PsiTreeUtil.getParentOfType(element, PsiNamedElement::class.java)
            ?: element

        return getHoverInfo(targetElement)
    }

    override fun getHoverInfo(element: PsiElement): HoverInfo {
        logger.info("Getting hover info for element: ${(element as? PsiNamedElement)?.name}")

        // Convert to UAST element if possible
        val uElement = element.toUElement()

        return when {
            uElement is UClass -> createClassHoverInfo(uElement, element as? PsiClass)
            uElement is UMethod -> createMethodHoverInfo(uElement, element as? PsiMethod)
            uElement is UField -> createFieldHoverInfo(uElement, element as? PsiField)
            uElement is UVariable -> createVariableHoverInfo(uElement, element as? PsiVariable)
            uElement is UAnnotation -> createAnnotationHoverInfo(uElement, element as? PsiAnnotation)
            element is PsiClass -> createClassHoverInfo(element.toUElementOfType<UClass>(), element)
            element is PsiMethod -> createMethodHoverInfo(element.toUElementOfType<UMethod>(), element)
            element is PsiField -> createFieldHoverInfo(element.toUElementOfType<UField>(), element)
            element is PsiVariable -> createVariableHoverInfo(element.toUElementOfType<UVariable>(), element)
            element is PsiAnnotation -> createAnnotationHoverInfo(element.toUElementOfType<UAnnotation>(), element)
            else -> createGenericHoverInfo(element)
        }
    }

    private fun createClassHoverInfo(uClass: UClass?, psiClass: PsiClass?): HoverInfo {
        val effectiveClass = psiClass ?: uClass?.javaPsi as? PsiClass ?: return createGenericHoverInfo(uClass?.sourcePsi ?: psiClass)
        val effectiveUClass = uClass ?: effectiveClass.toUElementOfType<UClass>()

        val type = when {
            effectiveClass.isInterface -> "interface"
            effectiveClass.isEnum -> "enum"
            effectiveClass.isAnnotationType -> "annotation"
            else -> "class"
        }

        // Enhanced type information with UAST
        val superTypes = mutableListOf<String>()
        effectiveUClass?.uastSuperTypes?.forEach { superType ->
            superTypes.add(superType.getQualifiedName() ?: superType.type.presentableText)
        }

        // Fallback to PSI if UAST doesn't provide super types
        if (superTypes.isEmpty()) {
            effectiveClass.extendsList?.referencedTypes?.forEach {
                superTypes.add(it.presentableText)
            }
            effectiveClass.implementsList?.referencedTypes?.forEach {
                superTypes.add(it.presentableText)
            }
        }

        // Find implementors for interfaces
        val implementedBy = if (effectiveClass.isInterface) {
            findImplementors(effectiveClass)
        } else {
            emptyList()
        }

        val deprecationInfo = getDeprecationInfo(effectiveClass)
        val docInfo = extractEnhancedDocumentation(effectiveUClass, effectiveClass)

        // Language-specific information
        val languageData = mutableMapOf<String, String>()
        val sourcePsi = effectiveUClass?.sourcePsi
        val isKotlin = sourcePsi?.language?.id?.lowercase() == "kotlin"
        val isScala = sourcePsi?.language?.id?.lowercase() == "scala"
        val isGroovy = sourcePsi?.language?.id?.lowercase() == "groovy"

        if (isKotlin) {
            addKotlinClassInfo(effectiveUClass, languageData)
        }
        if (isScala) {
            addScalaClassInfo(effectiveUClass, languageData)
        }
        if (isGroovy) {
            addGroovyClassInfo(effectiveUClass, languageData)
        }

        return HoverInfo(
            elementName = effectiveClass.name ?: "anonymous",
            elementType = type,
            type = effectiveClass.qualifiedName,
            presentableText = buildClassPresentableText(effectiveClass),
            javaDoc = docInfo.fullDoc,
            signature = buildClassSignature(effectiveClass, effectiveUClass),
            modifiers = extractModifiers(effectiveClass.modifierList),
            superTypes = superTypes,
            implementedBy = implementedBy,
            isDeprecated = deprecationInfo.isDeprecated,
            deprecationMessage = deprecationInfo.message,
            since = docInfo.since,
            seeAlso = docInfo.seeAlso
        )
    }

    private fun createMethodHoverInfo(uMethod: UMethod?, psiMethod: PsiMethod?): HoverInfo {
        val effectiveMethod = psiMethod ?: uMethod?.javaPsi as? PsiMethod ?: return createGenericHoverInfo(uMethod?.sourcePsi ?: psiMethod)
        val effectiveUMethod = uMethod ?: effectiveMethod.toUElementOfType<UMethod>()

        // Enhanced parameter information with UAST
        val params = if (effectiveUMethod != null) {
            effectiveUMethod.uastParameters.joinToString(", ") { param ->
                "${param.type.presentableText} ${param.name}"
            }
        } else {
            effectiveMethod.parameterList.parameters.joinToString(", ") {
                "${it.type.presentableText} ${it.name}"
            }
        }

        // Get throws exceptions
        val throwsExceptions = effectiveMethod.throwsList.referencedTypes.map { it.presentableText }

        // Find overriding methods
        val overriddenBy = findOverridingMethods(effectiveMethod)

        // Count method calls
        val calledByCount = countMethodCalls(effectiveMethod)

        // Calculate complexity
        val complexity = calculateCyclomaticComplexity(effectiveMethod)

        // Get deprecation info
        val deprecationInfo = getDeprecationInfo(effectiveMethod)

        // Extract enhanced documentation
        val docInfo = extractEnhancedDocumentation(effectiveUMethod, effectiveMethod)

        // Language-specific information
        val languageData = mutableMapOf<String, String>()
        val sourcePsi = effectiveUMethod?.sourcePsi
        val isKotlin = sourcePsi?.language?.id?.lowercase() == "kotlin"
        val isScala = sourcePsi?.language?.id?.lowercase() == "scala"
        val isGroovy = sourcePsi?.language?.id?.lowercase() == "groovy"

        if (isKotlin) {
            addKotlinMethodInfo(effectiveUMethod, languageData)
        }
        if (isScala) {
            addScalaMethodInfo(effectiveUMethod, languageData)
        }
        if (isGroovy) {
            addGroovyMethodInfo(effectiveUMethod, languageData)
        }

        // Type parameters
        val typeParams = effectiveUMethod?.typeParameters?.takeIf { it.isNotEmpty() }?.let { params ->
            params.joinToString(", ") { param -> param.name ?: "?" }
        }
        if (typeParams != null) {
            languageData["typeParameters"] = typeParams
        }

        return HoverInfo(
            elementName = effectiveMethod.name,
            elementType = if (effectiveMethod.isConstructor) "constructor" else "method",
            type = effectiveMethod.returnType?.presentableText ?: if (effectiveMethod.isConstructor) "" else "void",
            presentableText = "${effectiveMethod.name}($params): ${effectiveMethod.returnType?.presentableText ?: "void"}",
            javaDoc = docInfo.fullDoc,
            signature = buildMethodSignature(effectiveMethod, effectiveUMethod),
            modifiers = extractModifiers(effectiveMethod.modifierList),
            throwsExceptions = throwsExceptions,
            overriddenBy = overriddenBy,
            calledByCount = calledByCount,
            complexity = complexity,
            isDeprecated = deprecationInfo.isDeprecated,
            deprecationMessage = deprecationInfo.message,
            since = docInfo.since,
            seeAlso = docInfo.seeAlso
        )
    }

    private fun createFieldHoverInfo(uField: UField?, psiField: PsiField?): HoverInfo {
        val effectiveField = psiField ?: uField?.javaPsi as? PsiField ?: return createGenericHoverInfo(uField?.sourcePsi ?: psiField)
        val effectiveUField = uField ?: effectiveField.toUElementOfType<UField>()

        val deprecationInfo = getDeprecationInfo(effectiveField)
        val docInfo = extractEnhancedDocumentation(effectiveUField, effectiveField)
        val calledByCount = countFieldUsage(effectiveField)

        // Language-specific information
        val languageData = mutableMapOf<String, String>()
        val sourcePsi = effectiveUField?.sourcePsi
        val isKotlin = sourcePsi?.language?.id?.lowercase() == "kotlin"

        if (isKotlin) {
            addKotlinFieldInfo(effectiveUField, languageData)
        }

        return HoverInfo(
            elementName = effectiveField.name,
            elementType = "field",
            type = effectiveField.type.presentableText,
            presentableText = "${effectiveField.name}: ${effectiveField.type.presentableText}",
            javaDoc = docInfo.fullDoc,
            signature = "${effectiveField.type.presentableText} ${effectiveField.name}",
            modifiers = extractModifiers(effectiveField.modifierList),
            calledByCount = calledByCount,
            isDeprecated = deprecationInfo.isDeprecated,
            deprecationMessage = deprecationInfo.message,
            since = docInfo.since,
            seeAlso = docInfo.seeAlso
        )
    }

    private fun createVariableHoverInfo(uVariable: UVariable?, psiVariable: PsiVariable?): HoverInfo {
        val effectiveVariable = psiVariable ?: uVariable?.javaPsi as? PsiVariable ?: return createGenericHoverInfo(uVariable?.sourcePsi ?: psiVariable)
        val effectiveUVariable = uVariable ?: effectiveVariable.toUElementOfType<UVariable>()

        val varType = when (effectiveVariable) {
            is PsiParameter -> "parameter"
            is PsiLocalVariable -> "variable"
            else -> "variable"
        }

        val languageData = mutableMapOf<String, String>()
        val sourcePsi = effectiveUVariable?.sourcePsi
        val isKotlin = sourcePsi?.language?.id?.lowercase() == "kotlin"

        if (isKotlin) {
            addKotlinVariableInfo(effectiveUVariable, languageData)
        }

        return HoverInfo(
            elementName = effectiveVariable.name ?: "anonymous",
            elementType = varType,
            type = effectiveVariable.type.presentableText,
            presentableText = "${effectiveVariable.name}: ${effectiveVariable.type.presentableText}",
            javaDoc = null,
            signature = "${effectiveVariable.type.presentableText} ${effectiveVariable.name}",
            modifiers = extractModifiers(effectiveVariable.modifierList)
        )
    }

    private fun createAnnotationHoverInfo(uAnnotation: UAnnotation?, psiAnnotation: PsiAnnotation?): HoverInfo {
        val effectiveAnnotation = psiAnnotation ?: uAnnotation?.javaPsi as? PsiAnnotation ?: return createGenericHoverInfo(uAnnotation?.sourcePsi ?: psiAnnotation)
        val qualifiedName = effectiveAnnotation.qualifiedName ?: "unknown"

        return HoverInfo(
            elementName = qualifiedName.substringAfterLast('.'),
            elementType = "annotation",
            type = qualifiedName,
            presentableText = "@${qualifiedName.substringAfterLast('.')}",
            javaDoc = null,
            signature = effectiveAnnotation.text,
            modifiers = emptyList()
        )
    }

    private fun createGenericHoverInfo(element: PsiElement?): HoverInfo {
        if (element == null) return HoverInfo("unknown", "unknown", null, null, null, null, emptyList())

        val namedElement = element as? PsiNamedElement
        return HoverInfo(
            elementName = namedElement?.name ?: element.text?.take(50) ?: "unknown",
            elementType = element.javaClass.simpleName.lowercase().replace("psi", "").replace("impl", ""),
            type = null,
            presentableText = element.text?.lines()?.firstOrNull()?.trim(),
            javaDoc = null,
            signature = null,
            modifiers = emptyList()
        )
    }

    // Language-specific helper methods
    private fun addKotlinClassInfo(uClass: UClass?, languageData: MutableMap<String, String>) {
        val sourcePsi = uClass?.sourcePsi
        val text = sourcePsi?.text ?: ""

        when {
            text.contains("data class") -> languageData["kotlinType"] = "data_class"
            text.contains("sealed class") -> languageData["kotlinType"] = "sealed_class"
            text.contains("object") -> languageData["kotlinType"] = "object"
            text.contains("companion object") -> languageData["kotlinType"] = "companion_object"
        }
    }

    private fun addKotlinMethodInfo(uMethod: UMethod?, languageData: MutableMap<String, String>) {
        val sourcePsi = uMethod?.sourcePsi
        val text = sourcePsi?.text ?: ""

        if (text.contains("suspend")) {
            languageData["kotlinFeature"] = "suspend_function"
        }
        if (text.contains("inline")) {
            languageData["kotlinFeature"] = "inline_function"
        }
        if (text.contains("extension")) {
            languageData["kotlinFeature"] = "extension_function"
        }
    }

    private fun addKotlinFieldInfo(uField: UField?, languageData: MutableMap<String, String>) {
        val sourcePsi = uField?.sourcePsi
        val text = sourcePsi?.text ?: ""

        if (text.contains("by lazy")) {
            languageData["kotlinFeature"] = "lazy_property"
        }
        if (text.contains("by ")) {
            languageData["kotlinFeature"] = "delegated_property"
        }
    }

    private fun addKotlinVariableInfo(uVariable: UVariable?, languageData: MutableMap<String, String>) {
        val sourcePsi = uVariable?.sourcePsi
        val text = sourcePsi?.text ?: ""

        if (text.contains("val ")) {
            languageData["kotlinType"] = "immutable"
        } else if (text.contains("var ")) {
            languageData["kotlinType"] = "mutable"
        }
    }

    private fun addScalaClassInfo(uClass: UClass?, languageData: MutableMap<String, String>) {
        val sourcePsi = uClass?.sourcePsi
        val text = sourcePsi?.text ?: ""

        when {
            text.contains("case class") -> languageData["scalaType"] = "case_class"
            text.contains("trait") -> languageData["scalaType"] = "trait"
            text.contains("object") -> languageData["scalaType"] = "object"
        }
    }

    private fun addScalaMethodInfo(uMethod: UMethod?, languageData: MutableMap<String, String>) {
        val sourcePsi = uMethod?.sourcePsi
        val text = sourcePsi?.text ?: ""

        if (text.contains("implicit")) {
            languageData["scalaFeature"] = "implicit_method"
        }
    }

    private fun addGroovyClassInfo(uClass: UClass?, languageData: MutableMap<String, String>) {
        val sourcePsi = uClass?.sourcePsi
        val text = sourcePsi?.text ?: ""

        if (text.contains("@Singleton")) {
            languageData["groovyFeature"] = "singleton"
        }
    }

    private fun addGroovyMethodInfo(uMethod: UMethod?, languageData: MutableMap<String, String>) {
        val sourcePsi = uMethod?.sourcePsi
        val text = sourcePsi?.text ?: ""

        if (text.contains("def ")) {
            languageData["groovyFeature"] = "dynamic_method"
        }
    }

    // Documentation extraction
    private data class DocumentationInfo(
        val fullDoc: String?,
        val since: String? = null,
        val seeAlso: List<String> = emptyList()
    )

    private fun extractEnhancedDocumentation(uElement: UElement?, psiElement: PsiElement?): DocumentationInfo {
        // Try to get documentation from UAST comments
        val uastDoc = uElement?.comments?.joinToString("\n") { it.text }?.trim()
        if (!uastDoc.isNullOrBlank()) {
            return parseDocumentation(uastDoc)
        }

        // Fallback to PSI documentation
        val psiDocOwner = psiElement as? PsiDocCommentOwner
        val docComment = psiDocOwner?.docComment
        if (docComment != null) {
            return extractJavaDocInfo(docComment)
        }

        return DocumentationInfo(null)
    }

    private fun extractJavaDocInfo(docComment: PsiDocComment): DocumentationInfo {
        val builder = StringBuilder()
        builder.append("/**\n")

        // Add description
        val descriptionElements = docComment.descriptionElements
        if (descriptionElements.isNotEmpty()) {
            builder.append(" * ")
            builder.append(descriptionElements.joinToString("") { it.text }.trim())
            builder.append("\n")
        }

        var since: String? = null
        val seeAlso = mutableListOf<String>()

        // Add tags and extract special ones
        docComment.tags.forEach { tag ->
            when (tag.name) {
                "since" -> {
                    since = tag.dataElements.joinToString("") { it.text }.trim()
                }
                "see" -> {
                    val seeText = tag.dataElements.joinToString("") { it.text }.trim()
                    if (seeText.isNotEmpty()) {
                        seeAlso.add(seeText)
                    }
                }
            }

            builder.append(" * @${tag.name}")
            tag.dataElements.forEach { data ->
                builder.append(" ${data.text}")
            }
            builder.append("\n")
        }

        builder.append(" */")

        return DocumentationInfo(
            fullDoc = builder.toString(),
            since = since,
            seeAlso = seeAlso
        )
    }

    private fun parseDocumentation(docText: String): DocumentationInfo {
        val lines = docText.lines()
        val builder = StringBuilder()
        var since: String? = null
        val seeAlso = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("@since") -> {
                    since = trimmed.removePrefix("@since").trim()
                }
                trimmed.startsWith("@see") -> {
                    seeAlso.add(trimmed.removePrefix("@see").trim())
                }
                else -> {
                    builder.append(line).append("\n")
                }
            }
        }

        return DocumentationInfo(
            fullDoc = builder.toString().trim().takeIf { it.isNotEmpty() },
            since = since,
            seeAlso = seeAlso
        )
    }

    // Signature building with UAST support
    private fun buildClassSignature(psiClass: PsiClass, uClass: UClass?): String {
        val modifiers = psiClass.modifierList?.text ?: ""
        val type = when {
            psiClass.isInterface -> "interface"
            psiClass.isEnum -> "enum"
            psiClass.isAnnotationType -> "@interface"
            else -> "class"
        }

        // Try to get extends/implements from UAST first
        val extends = uClass?.uastSuperTypes?.firstOrNull { superType ->
            val psiType = superType.type
            psiType is PsiClassType && psiType.resolve()?.isInterface == false
        }?.let { " extends ${it.getQualifiedName() ?: it.type.presentableText}" }
            ?: psiClass.extendsList?.referencedTypes?.firstOrNull()?.presentableText?.let { " extends $it" }
            ?: ""

        val implements = uClass?.uastSuperTypes?.filter { superType ->
            val psiType = superType.type
            psiType is PsiClassType && psiType.resolve()?.isInterface == true
        }?.joinToString(", ") { it.getQualifiedName() ?: it.type.presentableText }
            ?: psiClass.implementsList?.referencedTypes?.joinToString(", ") { it.presentableText }
            ?: ""

        val implementsClause = if (implements.isNotEmpty()) " implements $implements" else ""

        return "$modifiers $type ${psiClass.name}$extends$implementsClause".trim()
    }

    private fun buildMethodSignature(psiMethod: PsiMethod, uMethod: UMethod?): String {
        val params = if (uMethod != null) {
            uMethod.uastParameters.joinToString(", ") { param ->
                "${param.type.presentableText} ${param.name}"
            }
        } else {
            psiMethod.parameterList.parameters.joinToString(", ") {
                "${it.type.presentableText} ${it.name}"
            }
        }

        val returnType = if (psiMethod.isConstructor) "" else "${psiMethod.returnType?.presentableText ?: "void"} "
        val modifiers = psiMethod.modifierList.text
        val throws = psiMethod.throwsList.referencedTypes.takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { it.presentableText }
            ?.let { " throws $it" } ?: ""

        return "$modifiers $returnType${psiMethod.name}($params)$throws".trim()
    }

    // Utility methods from JavaHoverInfoProvider
    private fun buildClassPresentableText(psiClass: PsiClass): String {
        val type = when {
            psiClass.isInterface -> "interface"
            psiClass.isEnum -> "enum"
            psiClass.isAnnotationType -> "@interface"
            else -> "class"
        }
        return "$type ${psiClass.name}"
    }

    private data class DeprecationInfo(
        val isDeprecated: Boolean,
        val message: String? = null
    )

    private fun getDeprecationInfo(element: PsiModifierListOwner): DeprecationInfo {
        val isDeprecated = hasAnnotation(element, "Deprecated")
        if (!isDeprecated) {
            return DeprecationInfo(false)
        }

        // Try to extract deprecation message from JavaDoc
        val docComment = (element as? PsiDocCommentOwner)?.docComment
        val deprecatedTag = docComment?.findTagByName("deprecated")
        val message = deprecatedTag?.dataElements?.joinToString("") { it.text }?.trim()

        return DeprecationInfo(true, message)
    }

    private fun findImplementors(psiClass: PsiClass): List<String> {
        val scope = GlobalSearchScope.projectScope(psiClass.project)
        return try {
            ClassInheritorsSearch.search(psiClass, scope, false)
                .findAll()
                .take(10) // Limit to prevent performance issues
                .mapNotNull { it.qualifiedName }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun findOverridingMethods(method: PsiMethod): List<String> {
        val scope = GlobalSearchScope.projectScope(method.project)
        return try {
            OverridingMethodsSearch.search(method, scope, false)
                .findAll()
                .take(10) // Limit to prevent performance issues
                .map { "${it.containingClass?.qualifiedName}.${it.name}" }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun countMethodCalls(method: PsiMethod): Int {
        val scope = GlobalSearchScope.projectScope(method.project)
        return try {
            var count = 0
            ReferencesSearch.search(method, scope).forEach { _ ->
                count++
                if (count > 100) return 100 // Cap at 100 for performance
            }
            count
        } catch (_: Exception) {
            0
        }
    }

    private fun countFieldUsage(field: PsiField): Int {
        val scope = GlobalSearchScope.projectScope(field.project)
        return try {
            var count = 0
            ReferencesSearch.search(field, scope).forEach { _ ->
                count++
                if (count > 100) return 100 // Cap at 100 for performance
            }
            count
        } catch (_: Exception) {
            0
        }
    }

    private fun calculateCyclomaticComplexity(method: PsiMethod): Int? {
        val body = method.body ?: return null
        var complexity = 1 // Base complexity

        body.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitIfStatement(statement: PsiIfStatement) {
                complexity++
                super.visitIfStatement(statement)
            }

            override fun visitWhileStatement(statement: PsiWhileStatement) {
                complexity++
                super.visitWhileStatement(statement)
            }

            override fun visitForStatement(statement: PsiForStatement) {
                complexity++
                super.visitForStatement(statement)
            }

            override fun visitForeachStatement(statement: PsiForeachStatement) {
                complexity++
                super.visitForeachStatement(statement)
            }

            override fun visitSwitchLabelStatement(statement: PsiSwitchLabelStatement) {
                if (statement.isDefaultCase == false) {
                    complexity++
                }
                super.visitSwitchLabelStatement(statement)
            }

            override fun visitConditionalExpression(expression: PsiConditionalExpression) {
                complexity++
                super.visitConditionalExpression(expression)
            }

            override fun visitCatchSection(section: PsiCatchSection) {
                complexity++
                super.visitCatchSection(section)
            }

            override fun visitPolyadicExpression(expression: PsiPolyadicExpression) {
                if (expression.operationTokenType == JavaTokenType.ANDAND ||
                    expression.operationTokenType == JavaTokenType.OROR
                ) {
                    complexity += expression.operands.size - 1
                }
                super.visitPolyadicExpression(expression)
            }
        })

        return complexity
    }

    override fun supportsElement(element: PsiElement): Boolean {
        val languageId = element.language.id
        return languageId in setOf("JAVA", "kotlin", "Kotlin", "Scala", "Groovy")
    }

    override fun supportsFile(psiFile: PsiFile): Boolean {
        return supportsElement(psiFile)
    }

    override fun getSupportedLanguage(): String {
        return "Java/Kotlin"
    }
}
