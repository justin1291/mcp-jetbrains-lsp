package dev.mcp.extensions.lsp.languages.java

import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import dev.mcp.extensions.lsp.core.interfaces.HoverInfoProvider
import dev.mcp.extensions.lsp.core.models.HoverInfo
import dev.mcp.extensions.lsp.languages.base.BaseLanguageHandler

/**
 * Hover info provider implementation for Java and Kotlin languages.
 */
class JavaHoverInfoProvider : BaseLanguageHandler(), HoverInfoProvider {
    
    override fun getHoverInfo(element: PsiElement): HoverInfo {
        logger.info("Getting hover info for element: ${(element as? PsiNamedElement)?.name}")
        
        return when (element) {
            is PsiClass -> createClassHoverInfo(element)
            is PsiMethod -> createMethodHoverInfo(element)
            is PsiField -> createFieldHoverInfo(element)
            is PsiVariable -> createVariableHoverInfo(element)
            is PsiAnnotation -> createAnnotationHoverInfo(element)
            else -> createGenericHoverInfo(element)
        }
    }
    
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
    
    override fun supportsElement(element: PsiElement): Boolean {
        val languageId = element.language.id
        return languageId == "JAVA" || languageId == "kotlin" || languageId == "Kotlin"
    }
    
    override fun getSupportedLanguage(): String {
        return "Java/Kotlin"
    }
    
    private fun createClassHoverInfo(psiClass: PsiClass): HoverInfo {
        val type = when {
            psiClass.isInterface -> "interface"
            psiClass.isEnum -> "enum"
            psiClass.isAnnotationType -> "annotation"
            else -> "class"
        }

        val superTypes = mutableListOf<String>()
        psiClass.extendsList?.referencedTypes?.forEach {
            superTypes.add(it.presentableText)
        }
        psiClass.implementsList?.referencedTypes?.forEach {
            superTypes.add(it.presentableText)
        }

        // Find implementors for interfaces
        val implementedBy = if (psiClass.isInterface) {
            findImplementors(psiClass)
        } else {
            emptyList()
        }

        val deprecationInfo = getDeprecationInfo(psiClass)
        val javaDocInfo = extractEnhancedJavaDoc(psiClass)

        return HoverInfo(
            elementName = psiClass.name ?: "anonymous",
            elementType = type,
            type = psiClass.qualifiedName,
            presentableText = buildClassPresentableText(psiClass),
            javaDoc = javaDocInfo.fullDoc,
            signature = buildClassSignature(psiClass),
            modifiers = extractModifiers(psiClass.modifierList),
            superTypes = superTypes,
            implementedBy = implementedBy,
            isDeprecated = deprecationInfo.isDeprecated,
            deprecationMessage = deprecationInfo.message,
            since = javaDocInfo.since,
            seeAlso = javaDocInfo.seeAlso
        )
    }

    private fun createMethodHoverInfo(method: PsiMethod): HoverInfo {
        val params = method.parameterList.parameters.joinToString(", ") {
            "${it.type.presentableText} ${it.name}"
        }

        // Get throws exceptions
        val throwsExceptions = method.throwsList.referencedTypes.map { it.presentableText }

        // Find overriding methods
        val overriddenBy = findOverridingMethods(method)

        // Count method calls
        val calledByCount = countMethodCalls(method)

        // Calculate complexity
        val complexity = calculateCyclomaticComplexity(method)

        // Get deprecation info
        val deprecationInfo = getDeprecationInfo(method)

        // Extract enhanced JavaDoc
        val javaDocInfo = extractEnhancedJavaDoc(method)

        return HoverInfo(
            elementName = method.name,
            elementType = if (method.isConstructor) "constructor" else "method",
            type = method.returnType?.presentableText ?: if (method.isConstructor) "" else "void",
            presentableText = "${method.name}($params): ${method.returnType?.presentableText ?: "void"}",
            javaDoc = javaDocInfo.fullDoc,
            signature = buildMethodSignature(method),
            modifiers = extractModifiers(method.modifierList),
            throwsExceptions = throwsExceptions,
            overriddenBy = overriddenBy,
            calledByCount = calledByCount,
            complexity = complexity,
            isDeprecated = deprecationInfo.isDeprecated,
            deprecationMessage = deprecationInfo.message,
            since = javaDocInfo.since,
            seeAlso = javaDocInfo.seeAlso
        )
    }

    private fun createFieldHoverInfo(field: PsiField): HoverInfo {
        val deprecationInfo = getDeprecationInfo(field)
        val javaDocInfo = extractEnhancedJavaDoc(field)
        val calledByCount = countFieldUsage(field)

        return HoverInfo(
            elementName = field.name ?: "anonymous",
            elementType = "field",
            type = field.type.presentableText,
            presentableText = "${field.name}: ${field.type.presentableText}",
            javaDoc = javaDocInfo.fullDoc,
            signature = "${field.type.presentableText} ${field.name}",
            modifiers = extractModifiers(field.modifierList),
            calledByCount = calledByCount,
            isDeprecated = deprecationInfo.isDeprecated,
            deprecationMessage = deprecationInfo.message,
            since = javaDocInfo.since,
            seeAlso = javaDocInfo.seeAlso
        )
    }

    private fun createVariableHoverInfo(variable: PsiVariable): HoverInfo {
        val varType = when (variable) {
            is PsiParameter -> "parameter"
            is PsiLocalVariable -> "variable"
            else -> "variable"
        }

        return HoverInfo(
            elementName = variable.name ?: "anonymous",
            elementType = varType,
            type = variable.type.presentableText,
            presentableText = "${variable.name}: ${variable.type.presentableText}",
            javaDoc = null,
            signature = "${variable.type.presentableText} ${variable.name}",
            modifiers = extractModifiers(variable.modifierList)
        )
    }

    private fun createAnnotationHoverInfo(annotation: PsiAnnotation): HoverInfo {
        val qualifiedName = annotation.qualifiedName ?: "unknown"
        return HoverInfo(
            elementName = qualifiedName.substringAfterLast('.'),
            elementType = "annotation",
            type = qualifiedName,
            presentableText = "@${qualifiedName.substringAfterLast('.')}",
            javaDoc = null,
            signature = annotation.text,
            modifiers = emptyList()
        )
    }

    private fun createGenericHoverInfo(element: PsiElement): HoverInfo {
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

    private fun buildClassPresentableText(psiClass: PsiClass): String {
        val type = when {
            psiClass.isInterface -> "interface"
            psiClass.isEnum -> "enum"
            psiClass.isAnnotationType -> "@interface"
            else -> "class"
        }
        return "$type ${psiClass.name}"
    }

    private fun buildClassSignature(psiClass: PsiClass): String {
        val modifiers = psiClass.modifierList?.text ?: ""
        val type = when {
            psiClass.isInterface -> "interface"
            psiClass.isEnum -> "enum"
            psiClass.isAnnotationType -> "@interface"
            else -> "class"
        }
        val extends =
            psiClass.extendsList?.referencedTypes?.firstOrNull()?.presentableText?.let { " extends $it" } ?: ""
        val implements = psiClass.implementsList?.referencedTypes?.joinToString(", ") { it.presentableText }
            ?.let { " implements $it" } ?: ""

        return "$modifiers $type ${psiClass.name}$extends$implements".trim()
    }

    private fun buildMethodSignature(method: PsiMethod): String {
        val params = method.parameterList.parameters.joinToString(", ") {
            "${it.type.presentableText} ${it.name}"
        }
        val returnType = if (method.isConstructor) "" else "${method.returnType?.presentableText ?: "void"} "
        val modifiers = method.modifierList.text
        val throws = method.throwsList.referencedTypes.takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { it.presentableText }
            ?.let { " throws $it" } ?: ""

        return "$modifiers $returnType${method.name}($params)$throws".trim()
    }

    private data class JavaDocInfo(
        val fullDoc: String?,
        val since: String? = null,
        val seeAlso: List<String> = emptyList()
    )

    private data class DeprecationInfo(
        val isDeprecated: Boolean,
        val message: String? = null
    )

    private fun findImplementors(psiClass: PsiClass): List<String> {
        val scope = GlobalSearchScope.projectScope(psiClass.project)
        return try {
            ClassInheritorsSearch.search(psiClass, scope, false)
                .findAll()
                .take(10) // Limit to prevent performance issues
                .mapNotNull { it.qualifiedName }
        } catch (e: Exception) {
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
        } catch (e: Exception) {
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
        } catch (e: Exception) {
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
        } catch (e: Exception) {
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

    private fun getDeprecationInfo(element: PsiModifierListOwner): DeprecationInfo {
        val isDeprecated = hasAnnotation(element, "java.lang.Deprecated")
        if (!isDeprecated) {
            return DeprecationInfo(false)
        }

        // Try to extract deprecation message from JavaDoc
        val docComment = (element as? PsiDocCommentOwner)?.docComment
        val deprecatedTag = docComment?.findTagByName("deprecated")
        val message = deprecatedTag?.dataElements?.joinToString("") { it.text }?.trim()

        return DeprecationInfo(true, message)
    }

    private fun extractEnhancedJavaDoc(element: PsiDocCommentOwner): JavaDocInfo {
        val docComment = element.docComment ?: return JavaDocInfo(null)

        // Build JavaDoc string
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

        return JavaDocInfo(
            fullDoc = builder.toString(),
            since = since,
            seeAlso = seeAlso
        )
    }
}
