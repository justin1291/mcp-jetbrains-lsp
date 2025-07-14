package dev.mcp.extensions.lsp.languages.python

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import dev.mcp.extensions.lsp.core.interfaces.HoverInfoProvider
import dev.mcp.extensions.lsp.core.models.HoverInfo

/**
 * Hover info provider implementation for Python language.
 *
 * Registered as a service in mcp-lsp-python.xml when Python module is available.
 */
class PythonHoverInfoProvider : PythonBaseHandler(), HoverInfoProvider {

    override fun getHoverInfo(element: PsiElement): HoverInfo {
        logger.info("Getting hover info for Python element: ${when (element) { is PyClass -> element.name; is PyFunction -> element.name; else -> "unknown" }}")

        return when (element) {
            is PyClass -> createClassHoverInfo(element)
            is PyFunction -> createFunctionHoverInfo(element)
            is PyNamedParameter -> createParameterHoverInfo(element)
            is PyTargetExpression -> createVariableHoverInfo(element)
            is PyDecorator -> createDecoratorHoverInfo(element)
            is PyImportElement -> createImportHoverInfo(element)
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
            ?: PsiTreeUtil.getParentOfType(element, PyClass::class.java, PyFunction::class.java, PyTargetExpression::class.java)
            ?: element

        return getHoverInfo(targetElement)
    }

    override fun supportsElement(element: PsiElement): Boolean {
        val languageId = element.language.id
        return languageId == "Python" || languageId == "PythonCore"
    }

    override fun getSupportedLanguage(): String {
        return "Python"
    }

    private fun createClassHoverInfo(pyClass: PyClass): HoverInfo {
        val docstring = extractDocstring(pyClass)
        val superClasses = extractSuperClasses(pyClass)
        val implementedBy = findSubclasses(pyClass)
        val isAbstract = hasAbstractMethods(pyClass)
        val isDataclass = isDataclass(pyClass)

        val elementType = when {
            isDataclass -> "dataclass"
            isAbstract -> "abstract class"
            else -> "class"
        }

        val signature = buildClassSignature(pyClass)
        val modifiers = getPythonModifiers(pyClass) +
                listOf(getPythonVisibility(pyClass.name))

        return HoverInfo(
            elementName = pyClass.name ?: "anonymous",
            elementType = elementType,
            type = pyClass.qualifiedName,
            presentableText = signature,
            javaDoc = formatDocstring(docstring),
            signature = signature,
            modifiers = modifiers.distinct(),
            superTypes = superClasses,
            implementedBy = implementedBy,
            isDeprecated = isDeprecated(pyClass),
            deprecationMessage = getDeprecationMessage(pyClass),
            module = getModuleName(pyClass)
        )
    }

    private fun createFunctionHoverInfo(function: PyFunction): HoverInfo {
        val docstring = extractDocstring(function)
        val parameters = extractFunctionParameters(function)
        val returnType = extractReturnTypeHint(function)
        val signature = buildFunctionSignature(function)
        val overriddenBy = findOverridingMethods(function)
        val calledByCount = countFunctionCalls(function)
        val complexity = calculateCyclomaticComplexity(function)

        val elementType = when {
            function.name == "__init__" -> "constructor"
            isProperty(function) -> "property"
            getMethodType(function) == "static" -> "static method"
            getMethodType(function) == "class" -> "class method"
            isAsync(function) -> "async function"
            function.isGenerator -> "generator"
            else -> "function"
        }

        val modifiers = getPythonModifiers(function) +
                listOf(getPythonVisibility(function.name))

        return HoverInfo(
            elementName = function.name ?: "anonymous",
            elementType = elementType,
            type = returnType ?: (if (isAsync(function)) "Coroutine" else "Any"),
            presentableText = "${function.name}($parameters)${returnType?.let { " -> $it" } ?: ""}",
            javaDoc = formatDocstring(docstring),
            signature = signature,
            modifiers = modifiers.distinct(),
            overriddenBy = overriddenBy,
            calledByCount = calledByCount,
            complexity = complexity,
            isDeprecated = isDeprecated(function),
            deprecationMessage = getDeprecationMessage(function),
            module = getModuleName(function)
        )
    }

    private fun createParameterHoverInfo(parameter: PyNamedParameter): HoverInfo {
        val typeHint: String? = try {
            parameter.annotationValue?.toString()
        } catch (e: Exception) {
            null
        }
        val defaultValue = parameter.defaultValue?.text
        val docstring = extractParameterDocstring(parameter)

        val presentableText = buildString {
            append(parameter.name)
            if (typeHint != null) append(": $typeHint")
            if (defaultValue != null) append(" = $defaultValue")
        }

        return HoverInfo(
            elementName = parameter.name ?: "parameter",
            elementType = "parameter",
            type = typeHint ?: "Any",
            presentableText = presentableText,
            javaDoc = docstring,
            signature = presentableText,
            modifiers = emptyList()
        )
    }

    private fun createVariableHoverInfo(variable: PyTargetExpression): HoverInfo {
        val typeHint: String? = try {
            variable.annotationValue?.toString()
        } catch (e: Exception) {
            null
        }
        val docstring = extractVariableDocstring(variable)
        val isConstant = isConstant(variable.name ?: "")
        val usageCount = countVariableUsage(variable)

        val elementType = when {
            isConstant -> "constant"
            else -> "variable"
        }

        val modifiers = listOfNotNull(
            getPythonVisibility(variable.name),
            if (isConstant) "constant" else null
        )

        return HoverInfo(
            elementName = variable.name ?: "variable",
            elementType = elementType,
            type = typeHint ?: inferVariableType(variable),
            presentableText = "${variable.name}${if (typeHint != null) ": $typeHint" else ""}",
            javaDoc = docstring,
            signature = "${variable.name}${if (typeHint != null) ": $typeHint" else ""}",
            modifiers = modifiers,
            calledByCount = usageCount
        )
    }

    private fun createDecoratorHoverInfo(decorator: PyDecorator): HoverInfo {
        val decoratorName = decorator.name ?: "decorator"
        val decoratorText = decorator.text

        return HoverInfo(
            elementName = decoratorName,
            elementType = "decorator",
            type = "decorator",
            presentableText = decoratorText,
            javaDoc = null,
            signature = decoratorText,
            modifiers = emptyList()
        )
    }

    private fun createImportHoverInfo(importElement: PyImportElement): HoverInfo {
        val importedName = importElement.importedQName?.toString() ?: "unknown"
        val asName = importElement.asName

        val presentableText = if (asName != null) {
            "$importedName as $asName"
        } else {
            importedName
        }

        return HoverInfo(
            elementName = asName ?: importedName,
            elementType = "import",
            type = "module",
            presentableText = presentableText,
            javaDoc = null,
            signature = "import $presentableText",
            modifiers = emptyList()
        )
    }

    private fun createGenericHoverInfo(element: PsiElement): HoverInfo {
        val elementName = when (element) {
            is PyClass -> element.name
            is PyFunction -> element.name
            is PyTargetExpression -> element.name
            else -> element.text?.take(50)
        } ?: "unknown"
        
        return HoverInfo(
            elementName = elementName,
            elementType = element.javaClass.simpleName.lowercase()
                .replace("py", "").replace("impl", ""),
            type = null,
            presentableText = element.text?.lines()?.firstOrNull()?.trim(),
            javaDoc = null,
            signature = null,
            modifiers = emptyList()
        )
    }

    // Helper methods for extracting Python-specific information

    private fun extractSuperClasses(pyClass: PyClass): List<String> {
        return pyClass.superClassExpressions.mapNotNull { expr ->
            when (expr) {
                is PyReferenceExpression -> expr.name
                else -> expr.text
            }
        }
    }

    private fun buildClassSignature(pyClass: PyClass): String {
        val decorators = extractDecorators(pyClass)
        val superClasses = extractSuperClasses(pyClass)

        val decoratorText = if (decorators.isNotEmpty()) {
            "${decorators.joinToString("\n")}\n"
        } else ""

        val inheritance = if (superClasses.isNotEmpty()) {
            "(${superClasses.joinToString(", ")})"
        } else ""

        return "${decoratorText}class ${pyClass.name}$inheritance"
    }

    private fun buildFunctionSignature(function: PyFunction): String {
        val decorators = extractDecorators(function)
        val parameters = extractFunctionParameters(function)
        val returnType = extractReturnTypeHint(function)
        val asyncKeyword = if (isAsync(function)) "async " else ""

        val decoratorText = if (decorators.isNotEmpty()) {
            "${decorators.joinToString("\n")}\n"
        } else ""

        val returnTypeText = returnType?.let { " -> $it" } ?: ""

        return "${decoratorText}${asyncKeyword}def ${function.name}($parameters)$returnTypeText"
    }

    private fun extractFunctionParameters(function: PyFunction): String {
        return function.parameterList.parameters.joinToString(", ") { param ->
            buildString {
                append(param.name ?: "")
                // Skip type annotation for now due to API instability
                param.defaultValue?.text?.let { append(" = $it") }
            }
        }
    }

    private fun findSubclasses(@Suppress("UNUSED_PARAMETER") pyClass: PyClass): List<String> {
        return try {
            // Note: This is a simplified implementation
            // For a complete implementation, we'd need to search for classes that inherit from this one
            // This would require more complex PSI traversal
            emptyList()
        } catch (e: Exception) {
            logger.debug("Error finding subclasses: ${e.message}")
            emptyList()
        }
    }

    private fun findOverridingMethods(@Suppress("UNUSED_PARAMETER") function: PyFunction): List<String> {
        // Simplified implementation - would need more complex logic for Python
        return try {
            emptyList()
        } catch (e: Exception) {
            logger.debug("Error finding overriding methods: ${e.message}")
            emptyList()
        }
    }

    private fun countFunctionCalls(function: PyFunction): Int {
        val scope = GlobalSearchScope.projectScope(function.project)
        return try {
            var count = 0
            ReferencesSearch.search(function, scope).forEach { _ ->
                count++
                if (count > 100) return 100 // Cap at 100 for performance
            }
            count
        } catch (e: Exception) {
            logger.debug("Error counting function calls: ${e.message}")
            0
        }
    }

    private fun countVariableUsage(variable: PyTargetExpression): Int {
        val scope = GlobalSearchScope.projectScope(variable.project)
        return try {
            var count = 0
            ReferencesSearch.search(variable, scope).forEach { _ ->
                count++
                if (count > 100) return 100 // Cap at 100 for performance
            }
            count
        } catch (e: Exception) {
            logger.debug("Error counting variable usage: ${e.message}")
            0
        }
    }

    private fun calculateCyclomaticComplexity(function: PyFunction): Int? {
        val statements = function.statementList
        var complexity = 1 // Base complexity

        statements.accept(object : PyRecursiveElementVisitor() {
            override fun visitPyIfStatement(node: PyIfStatement) {
                complexity++
                super.visitPyIfStatement(node)
            }

            override fun visitPyWhileStatement(node: PyWhileStatement) {
                complexity++
                super.visitPyWhileStatement(node)
            }

            override fun visitPyForStatement(node: PyForStatement) {
                complexity++
                super.visitPyForStatement(node)
            }

            override fun visitPyConditionalExpression(node: PyConditionalExpression) {
                complexity++
                super.visitPyConditionalExpression(node)
            }

            override fun visitPyBinaryExpression(node: PyBinaryExpression) {
                // Try to detect logical operators
                val opText = node.operator?.toString()
                if (opText?.contains("and") == true || opText?.contains("or") == true) {
                    complexity++
                }
                super.visitPyBinaryExpression(node)
            }
        })

        return complexity
    }

    private fun formatDocstring(docstring: String?): String? {
        if (docstring.isNullOrBlank()) return null

        // Format Python docstring with proper indentation
        return "\"\"\"${docstring.trim()}\"\"\""
    }

    private fun extractParameterDocstring(parameter: PyNamedParameter): String? {
        // Try to extract parameter documentation from function docstring
        val function = PsiTreeUtil.getParentOfType(parameter, PyFunction::class.java)
        val docstring = function?.let { extractDocstring(it) }

        if (docstring != null && parameter.name != null) {
            // Simple extraction - look for parameter documentation
            val lines = docstring.lines()
            val paramName = parameter.name!!
            for (i in lines.indices) {
                val line = lines[i].trim()
                if (line.contains(paramName) && (line.contains("param") || line.contains(":"))) {
                    return line
                }
            }
        }

        return null
    }

    private fun extractVariableDocstring(variable: PyTargetExpression): String? {
        // Look for comment above the variable or inline comment
        val prevSibling = variable.parent?.prevSibling
        if (prevSibling is PsiComment) {
            return prevSibling.text.removePrefix("#").trim()
        }

        // Look for inline comment
        val nextSibling = variable.parent?.nextSibling
        if (nextSibling is PsiComment) {
            return nextSibling.text.removePrefix("#").trim()
        }

        return null
    }

    private fun inferVariableType(variable: PyTargetExpression): String {
        // Simple type inference based on assigned value
        val assignedValue = variable.findAssignedValue()

        return when (assignedValue) {
            is PyStringLiteralExpression -> "str"
            is PyNumericLiteralExpression -> {
                when {
                    assignedValue.text.contains(".") -> "float"
                    else -> "int"
                }
            }

            is PyBoolLiteralExpression -> "bool"
            is PyListLiteralExpression -> "list"
            is PyDictLiteralExpression -> "dict"
            is PySetLiteralExpression -> "set"
            is PyTupleExpression -> "tuple"
            else -> "Any"
        }
    }

    private fun isDeprecated(element: PyElement): Boolean {
        // Check for @deprecated decorator or deprecation in docstring
        if (element is PyDecoratable) {
            val decorators = extractDecorators(element)
            if (decorators.any { it.contains("deprecated", ignoreCase = true) }) {
                return true
            }
        }

        // Check docstring for deprecation notice
        if (element is PyDocStringOwner) {
            val docstring = extractDocstring(element)
            if (docstring?.contains("deprecated", ignoreCase = true) == true) {
                return true
            }
        }

        return false
    }

    private fun getDeprecationMessage(element: PyElement): String? {
        // Extract deprecation message from docstring
        if (element is PyDocStringOwner) {
            val docstring = extractDocstring(element)
            if (docstring?.contains("deprecated", ignoreCase = true) == true) {
                // Extract the line containing "deprecated"
                val lines = docstring.lines()
                return lines.find { it.contains("deprecated", ignoreCase = true) }?.trim()
            }
        }

        return null
    }

    private fun getModuleName(element: PyElement): String? {
        val containingFile = element.containingFile as? PyFile
        return containingFile?.name?.removeSuffix(".py")
    }
}
