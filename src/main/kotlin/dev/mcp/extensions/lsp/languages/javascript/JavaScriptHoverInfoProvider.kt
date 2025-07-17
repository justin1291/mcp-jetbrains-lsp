package dev.mcp.extensions.lsp.languages.javascript

import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeListOwner
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.lang.javascript.psi.jsdoc.JSDocComment
import com.intellij.lang.javascript.psi.jsdoc.JSDocTag
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import dev.mcp.extensions.lsp.core.interfaces.HoverInfoProvider
import dev.mcp.extensions.lsp.core.models.HoverInfo
import dev.mcp.extensions.lsp.languages.base.BaseLanguageHandler

/**
 * Hover info provider implementation for JavaScript and TypeScript languages.
 *
 * Provides rich documentation and type information for symbols.
 */
class JavaScriptHoverInfoProvider : BaseLanguageHandler(), HoverInfoProvider {

    override fun getHoverInfo(element: PsiElement): HoverInfo {
        logger.info("Getting hover info for JavaScript/TypeScript element")

        val target = findHoverTarget(element) ?: return createDefaultHoverInfo(element)
        return createHoverInfo(target)
    }

    override fun getHoverInfoAtPosition(psiFile: PsiFile, position: Int): HoverInfo? {
        logger.info("Getting hover info at position $position in JavaScript/TypeScript file: ${psiFile.name}")

        val element = psiFile.findElementAt(position) ?: return null
        val target = findHoverTarget(element) ?: return null

        return createHoverInfo(target)
    }

    override fun supportsElement(element: PsiElement): Boolean {
        return element is JSFunction ||
                element is JSClass ||
                element is JSVariable ||
                element is JSField
    }

    override fun getSupportedLanguage(): String {
        return "JavaScript/TypeScript"
    }

    private fun findHoverTarget(element: PsiElement): PsiElement? {
        // Try to resolve reference first
        var current: PsiElement? = element
        while (current != null && current !is PsiFile) {
            // Check if we're at a declaration
            when (current) {
                is JSFunction -> return current
                is JSClass -> return current
                is JSVariable -> return current
                is JSField -> return current
            }

            // Check for references
            val reference = current.reference
            if (reference != null) {
                val resolved = reference.resolve()
                if (resolved != null) {
                    return resolved
                }
            }

            current = current.parent
        }
        return null
    }

    private fun createHoverInfo(element: PsiElement): HoverInfo {
        return when (element) {
            is JSFunction -> createFunctionHoverInfo(element)
            is JSClass -> createClassHoverInfo(element)
            is JSVariable -> createVariableHoverInfo(element)
            is JSField -> createFieldHoverInfo(element)
            else -> createDefaultHoverInfo(element)
        }
    }

    private fun createFunctionHoverInfo(function: JSFunction): HoverInfo {
        val name = function.name ?: "anonymous"
        val signature = buildFunctionSignature(function)
        val returnType = getReturnTypeString(function) ?: "void"

        // Documentation
        val docComment = getDocComment(function)
        val javaDoc = formatJSDoc(docComment)

        // Modifiers
        val modifiers = mutableListOf<String>()
        if (function.isAsync) modifiers.add("async")
        if (function.isGenerator) modifiers.add("generator")
        if (isArrowFunction(function)) modifiers.add("arrow")
        if (isStaticMember(function)) modifiers.add("static")

        // Extract thrown exceptions from JSDoc
        val throwsExceptions = extractThrows(docComment)

        return HoverInfo(
            elementName = name,
            elementType = "function",
            type = returnType,
            presentableText = signature,
            javaDoc = javaDoc,
            signature = signature,
            modifiers = modifiers,
            superTypes = emptyList(),
            implementedBy = emptyList(),
            overriddenBy = emptyList(),
            calledByCount = 0, // Would need index search
            complexity = null,
            throwsExceptions = throwsExceptions,
            deprecationMessage = getDeprecationMessage(docComment),
            since = extractSince(docComment),
            seeAlso = extractSeeAlso(docComment),
            isDeprecated = isDeprecated(function),
            module = function.containingFile.name
        )
    }

    private fun createClassHoverInfo(jsClass: JSClass): HoverInfo {
        val name = jsClass.name ?: "anonymous"
        val type = if (isReactComponent(jsClass)) "React.Component" else "class"

        // Documentation
        val docComment = getDocComment(jsClass)
        val javaDoc = formatJSDoc(docComment)

        // Modifiers
        val modifiers = mutableListOf<String>()
        if (isExported(jsClass)) modifiers.add("export")

        // Super types
        val superTypes = jsClass.extendsList?.members?.map { it.text } ?: emptyList()

        return HoverInfo(
            elementName = name,
            elementType = type,
            type = null,
            presentableText = name,
            javaDoc = javaDoc,
            signature = null,
            modifiers = modifiers,
            superTypes = superTypes,
            implementedBy = emptyList(),
            overriddenBy = emptyList(),
            calledByCount = 0,
            complexity = null,
            throwsExceptions = emptyList(),
            deprecationMessage = getDeprecationMessage(docComment),
            since = extractSince(docComment),
            seeAlso = extractSeeAlso(docComment),
            isDeprecated = isDeprecated(jsClass),
            module = jsClass.containingFile.name
        )
    }

    private fun createVariableHoverInfo(variable: JSVariable): HoverInfo {
        val name = variable.name ?: "anonymous"
        val type = variable.typeElement?.text ?: inferType(variable)

        // Documentation
        val docComment = getDocComment(variable)
        val javaDoc = formatJSDoc(docComment)

        // Modifiers
        val modifiers = mutableListOf<String>()
        if (variable.isConst) modifiers.add("const")

        return HoverInfo(
            elementName = name,
            elementType = "variable",
            type = type,
            presentableText = "$name: $type",
            javaDoc = javaDoc,
            signature = null,
            modifiers = modifiers,
            superTypes = emptyList(),
            implementedBy = emptyList(),
            overriddenBy = emptyList(),
            calledByCount = 0,
            complexity = null,
            throwsExceptions = emptyList(),
            deprecationMessage = getDeprecationMessage(docComment),
            since = extractSince(docComment),
            seeAlso = extractSeeAlso(docComment),
            isDeprecated = isDeprecated(variable),
            module = variable.containingFile.name
        )
    }

    private fun createFieldHoverInfo(field: JSField): HoverInfo {
        val name = field.name ?: "anonymous"
        val type = field.typeElement?.text ?: "any"

        // Documentation
        val docComment = getDocComment(field)
        val javaDoc = formatJSDoc(docComment)

        // Modifiers
        val modifiers = mutableListOf<String>()
        if (isStaticMember(field)) modifiers.add("static")
        if (field.name?.startsWith("#") == true) modifiers.add("private")

        return HoverInfo(
            elementName = name,
            elementType = "field",
            type = type,
            presentableText = "$name: $type",
            javaDoc = javaDoc,
            signature = null,
            modifiers = modifiers,
            superTypes = emptyList(),
            implementedBy = emptyList(),
            overriddenBy = emptyList(),
            calledByCount = 0,
            complexity = null,
            throwsExceptions = emptyList(),
            deprecationMessage = getDeprecationMessage(docComment),
            since = extractSince(docComment),
            seeAlso = extractSeeAlso(docComment),
            isDeprecated = isDeprecated(field),
            module = field.containingFile.name
        )
    }

    private fun createDefaultHoverInfo(element: PsiElement): HoverInfo {
        return HoverInfo(
            elementName = element.text.take(50),
            elementType = element.javaClass.simpleName,
            type = null,
            presentableText = element.text.take(50),
            javaDoc = null,
            signature = null,
            modifiers = emptyList(),
            superTypes = emptyList(),
            implementedBy = emptyList(),
            overriddenBy = emptyList(),
            calledByCount = 0,
            complexity = null,
            throwsExceptions = emptyList(),
            deprecationMessage = null,
            since = null,
            seeAlso = emptyList(),
            isDeprecated = false,
            module = element.containingFile?.name
        )
    }

    private fun getDocComment(element: PsiElement): JSDocComment? {
        // Search for JSDoc comment as a previous sibling or child
        var docComment: JSDocComment? = null

        // First try as a child
        element.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(child: PsiElement) {
                if (child is JSDocComment && docComment == null) {
                    docComment = child
                    return
                }
                super.visitElement(child)
            }
        })

        // If not found as child, try as previous sibling
        if (docComment == null) {
            var sibling = element.prevSibling
            while (sibling != null && docComment == null) {
                if (sibling is JSDocComment) {
                    docComment = sibling
                }
                sibling = sibling.prevSibling
            }
        }

        return docComment
    }

    private fun formatJSDoc(docComment: JSDocComment?): String? {
        if (docComment == null) return null

        val parts = mutableListOf<String>()

        // Add description - extract text that's not part of tags
        val fullText = docComment.text
        val descriptionLines = fullText.lines()
            .filter { line ->
                val trimmed = line.trim()
                trimmed.isNotEmpty() &&
                        !trimmed.startsWith("/**") &&
                        !trimmed.startsWith("*/") &&
                        !trimmed.startsWith("* @") &&
                        !trimmed.startsWith("@")
            }
            .map { it.trim().removePrefix("*").trim() }
            .filter { it.isNotEmpty() }

        if (descriptionLines.isNotEmpty()) {
            parts.add(descriptionLines.joinToString(" "))
        }

        // Add parameters
        val paramTags = docComment.tags.filter { it.name == "@param" || it.name == "@parameter" }
        if (paramTags.isNotEmpty()) {
            parts.add("")
            parts.add("Parameters:")
            paramTags.forEach { tag ->
                val tagText = extractTagText(tag)
                if (tagText.isNotEmpty()) {
                    parts.add("  $tagText")
                }
            }
        }

        // Add return value
        val returnTag = docComment.tags.find { it.name == "@returns" || it.name == "@return" }
        if (returnTag != null) {
            parts.add("")
            val returnText = extractTagText(returnTag)
            parts.add("Returns: $returnText")
        }

        return if (parts.isNotEmpty()) parts.joinToString("\n") else null
    }

    private fun extractTagText(tag: JSDocTag): String {
        // Extract the text after the tag name
        val tagText = tag.text
        val tagName = tag.name
        return tagText.substringAfter(tagName).trim()
    }

    private fun extractThrows(docComment: JSDocComment?): List<String> {
        return docComment?.tags
            ?.filter { it.name == "@throws" || it.name == "@exception" }
            ?.map { extractTagText(it) }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
    }

    private fun extractSince(docComment: JSDocComment?): String? {
        return docComment?.tags
            ?.firstOrNull { it.name == "@since" }
            ?.let { extractTagText(it) }
            ?.takeIf { it.isNotEmpty() }
    }

    private fun extractSeeAlso(docComment: JSDocComment?): List<String> {
        return docComment?.tags
            ?.filter { it.name == "@see" }
            ?.map { extractTagText(it) }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
    }

    private fun getDeprecationMessage(docComment: JSDocComment?): String? {
        return docComment?.tags
            ?.firstOrNull { it.name == "@deprecated" }
            ?.let { extractTagText(it) }
            ?.takeIf { it.isNotEmpty() }
    }

    private fun buildFunctionSignature(function: JSFunction): String {
        val params = function.parameters.joinToString(", ") { param ->
            val paramName = param.name ?: "_"
            val paramType = param.typeElement?.text ?: "any"
            val optional = if (param.isOptional) "?" else ""
            val rest = if (param.isRest) "..." else ""
            "$rest$paramName$optional: $paramType"
        }

        val returnType = getReturnTypeString(function) ?: "void"
        val name = function.name ?: "anonymous"

        return "$name($params): $returnType"
    }

    private fun getReturnTypeString(function: JSFunction): String? {
        // Try to extract return type from function signature
        val functionText = function.text
        val returnTypeMatch = Regex(""":\s*([^{]+?)\s*[{=]""").find(functionText)
        return returnTypeMatch?.groupValues?.get(1)?.trim()
    }

    private fun inferType(variable: JSVariable): String {
        val initializer = variable.initializer ?: return "any"

        return when (initializer) {
            is JSLiteralExpression -> {
                when {
                    initializer.isStringLiteral -> "string"
                    initializer.isNumericLiteral -> "number"
                    initializer.isBooleanLiteral -> "boolean"
                    initializer.isNullLiteral -> "null"
                    else -> "any"
                }
            }

            is JSArrayLiteralExpression -> "Array"
            is JSObjectLiteralExpression -> "Object"
            is JSFunction -> "Function"
            is JSNewExpression -> {
                (initializer.methodExpression as? JSReferenceExpression)?.referenceName ?: "Object"
            }

            else -> "any"
        }
    }

    private fun isDeprecated(element: PsiElement): Boolean {
        // Check decorators
        if (element is JSAttributeListOwner) {
            element.attributeList?.decorators?.forEach { decorator ->
                if (decorator.decoratorName == "deprecated" || decorator.decoratorName == "Deprecated") {
                    return true
                }
            }
        }

        // Check JSDoc
        val docComment = getDocComment(element)
        if (docComment != null) {
            return docComment.tags.any { it.name == "@deprecated" }
        }

        return false
    }

    private fun isArrowFunction(function: JSFunction): Boolean {
        return function.text.contains("=>")
    }

    private fun isStaticMember(element: PsiElement): Boolean {
        // Check if element has static modifier in text
        return when (element) {
            is JSAttributeListOwner -> {
                element.attributeList?.text?.contains("static") == true
            }

            else -> {
                // Check parent's text for static keyword
                element.parent?.text?.startsWith("static ") == true
            }
        }
    }

    private fun isExported(element: PsiElement): Boolean {
        // Check if element has export modifier or is in an export statement
        return when {
            element is JSAttributeListOwner && element.attributeList?.text?.contains("export") == true -> true
            element.text.startsWith("export ") -> true
            element.parent?.text?.startsWith("export ") == true -> true
            else -> false
        }
    }

    private fun isReactComponent(element: PsiElement): Boolean {
        return when (element) {
            is JSClass -> {
                val name = element.name
                name != null && name[0].isUpperCase()
            }

            is JSVariable -> {
                val name = element.name
                name != null && name[0].isUpperCase() && element.initializer is JSFunction
            }

            else -> false
        }
    }
}
