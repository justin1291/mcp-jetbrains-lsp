package dev.mcp.extensions.lsp.languages.javascript

import com.intellij.lang.ecmascript6.psi.ES6ImportDeclaration
import com.intellij.lang.ecmascript6.psi.ES6ImportSpecifier
import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeListOwner
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.lang.javascript.psi.ecmal4.JSReferenceList
import com.intellij.lang.javascript.psi.jsdoc.JSDocComment
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import dev.mcp.extensions.lsp.core.interfaces.ReferenceFinder
import dev.mcp.extensions.lsp.core.models.FindReferencesArgs
import dev.mcp.extensions.lsp.core.models.GroupedReferencesResult
import dev.mcp.extensions.lsp.core.models.ReferenceInfo
import dev.mcp.extensions.lsp.core.models.ReferenceSummary
import dev.mcp.extensions.lsp.languages.base.BaseLanguageHandler

/**
 * Reference finder implementation for JavaScript and TypeScript languages.
 *
 * Finds all usages of symbols with categorization and insights.
 */
class JavaScriptReferenceFinder : BaseLanguageHandler(), ReferenceFinder {

    override fun findReferences(project: Project, element: PsiElement, args: FindReferencesArgs): List<ReferenceInfo> {
        logger.info("Finding references for element in JavaScript/TypeScript")

        val references = mutableListOf<ReferenceInfo>()

        // Search for references
        val searchResults = ReferencesSearch.search(element).findAll()

        // Process each reference
        searchResults.forEach { psiReference ->
            val refElement = psiReference.element
            val referenceInfo = createReferenceInfo(refElement, element)
            references.add(referenceInfo)
        }

        // Include declaration if requested
        if (args.includeDeclaration) {
            val declarationInfo = createReferenceInfo(element, element, "declaration")
            references.add(declarationInfo)
        }

        return references
    }

    override fun findTargetElement(project: Project, args: FindReferencesArgs): PsiElement? {
        logger.info("Finding target element for references")

        // If we have a file path and position, find element at that position
        if (args.filePath != null && args.position != null) {
            val psiFile = findPsiFileByPath(project, args.filePath) ?: return null
            val element = psiFile.findElementAt(args.position) ?: return null
            return findReferencedElement(element)
        }

        // If we have a symbol name, search for it
        if (args.symbolName != null) {
            // Would need proper project-wide search here
            // Simplified for now
            return null
        }

        return null
    }

    override fun createGroupedResult(references: List<ReferenceInfo>, element: PsiElement): GroupedReferencesResult {
        val groupedByType = references.groupBy { it.usageType }
        val insights = generateInsights(element, references, groupedByType)

        val summary = ReferenceSummary(
            totalReferences = references.size,
            fileCount = references.map { it.filePath }.distinct().size,
            hasTestUsages = references.any { it.isInTestCode },
            primaryUsageLocation = references.groupBy { it.filePath }
                .maxByOrNull { it.value.size }?.key,
            deprecatedUsageCount = references.count { it.isInDeprecatedCode }
        )

        return GroupedReferencesResult(
            summary = summary,
            usagesByType = groupedByType,
            insights = insights,
            allReferences = references
        )
    }

    override fun createReferenceInfo(element: PsiElement, target: PsiElement, overrideType: String?): ReferenceInfo {
        val containingFile = element.containingFile
        val virtualFile = containingFile.virtualFile
        val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
            .getDocument(virtualFile)

        val startOffset = element.textRange.startOffset
        val endOffset = element.textRange.endOffset
        val lineNumber = document?.getLineNumber(startOffset)?.plus(1) ?: 1

        // Determine usage type
        val usageType = overrideType ?: determineUsageType(element, target)

        // Get surrounding context
        val surroundingCode = extractSurroundingCode(element, document)

        // Determine data flow context
        val dataFlowContext = analyzeDataFlow(element)

        // Get containing method/class
        val containingMethod = getContainingMethod(element)
        val containingClass = getContainingClass(element)

        return ReferenceInfo(
            filePath = getRelativePath(element),
            startOffset = startOffset,
            endOffset = endOffset,
            lineNumber = lineNumber,
            usageType = usageType,
            elementText = element.text,
            preview = surroundingCode,
            containingMethod = containingMethod,
            containingClass = containingClass,
            isInTestCode = isInTestCode(virtualFile),
            isInComment = element is PsiComment,
            accessModifier = getAccessModifier(element),
            surroundingContext = surroundingCode,
            dataFlowContext = dataFlowContext,
            isInDeprecatedCode = isDeprecatedUsage(element, target)
        )
    }

    override fun supportsElement(element: PsiElement): Boolean {
        return element is JSFunction ||
                element is JSClass ||
                element is JSVariable ||
                element is JSField ||
                element is JSParameter ||
                (element is PsiFile && supportsFile(element))
    }

    override fun supportsFile(psiFile: PsiFile): Boolean {
        val languageId = psiFile.language.id
        return languageId in setOf("JavaScript", "TypeScript", "JSX", "TSX", "ECMAScript 6")
    }

    override fun getSupportedLanguage(): String {
        return "JavaScript/TypeScript"
    }

    private fun findPsiFileByPath(project: Project, filePath: String): PsiFile? {
        val basePath = project.basePath ?: return null
        val fullPath = if (filePath.startsWith("/")) filePath else "$basePath/$filePath"
        val virtualFile = com.intellij.openapi.vfs.VirtualFileManager.getInstance().findFileByUrl("file://$fullPath")
        return virtualFile?.let { com.intellij.psi.PsiManager.getInstance(project).findFile(it) }
    }

    private fun findReferencedElement(element: PsiElement): PsiElement? {
        // Try to find a reference from the element
        var current: PsiElement? = element
        while (current != null && current !is PsiFile) {
            // Check if we're at a declaration
            when (current) {
                is JSParameter -> return current
                is JSFunction -> return current
                is JSClass -> return current
                is JSVariable -> return current
                is JSField -> return current
            }

            // Check for references
            val reference = current.reference
            if (reference != null) {
                return reference.resolve()
            }

            current = current.parent
        }
        return null
    }

    private fun determineUsageType(element: PsiElement, target: PsiElement): String {
        // Determine the context of the usage
        val parent = element.parent

        return when {
            // Function/method calls
            parent is JSCallExpression && element == parent.methodExpression -> {
                when {
                    target is JSFunction && target.isConstructor -> "constructor_call"
                    target is JSClass -> "constructor_call"
                    else -> "method_call"
                }
            }

            // Property access
            parent is JSReferenceExpression && parent.qualifier != null -> {
                when {
                    isGetter(parent) -> "getter_call"
                    isSetter(parent) -> "setter_call"
                    isFieldWrite(parent) -> "field_write"
                    else -> "field_read"
                }
            }

            // Import statements
            parent is ES6ImportDeclaration || parent is ES6ImportSpecifier -> "import"

            // Class extension
            parent is JSReferenceList && parent.parent is JSClass -> "type_reference"

            // Variable assignment
            isAssignmentTarget(element) -> "field_write"

            else -> "other"
        }
    }

    private fun isGetter(reference: JSReferenceExpression): Boolean {
        // Check if this is a getter call
        val resolved = reference.resolve()
        return resolved is JSFunction && resolved.isGetProperty
    }

    private fun isSetter(reference: JSReferenceExpression): Boolean {
        // Check if this is a setter call
        val parent = reference.parent
        if (parent is JSAssignmentExpression && parent.lOperand == reference) {
            val resolved = reference.resolve()
            return resolved is JSFunction && resolved.isSetProperty
        }
        return false
    }

    private fun isFieldWrite(reference: JSReferenceExpression): Boolean {
        val parent = reference.parent
        return parent is JSAssignmentExpression && parent.lOperand == reference
    }

    private fun isAssignmentTarget(element: PsiElement): Boolean {
        val parent = element.parent
        return parent is JSAssignmentExpression && parent.lOperand == element
    }

    private fun extractSurroundingCode(element: PsiElement, document: com.intellij.openapi.editor.Document?): String {
        if (document == null) return element.text

        val lineNumber = document.getLineNumber(element.textRange.startOffset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)

        return document.text.substring(lineStart, lineEnd).trim()
    }

    private fun analyzeDataFlow(element: PsiElement): String? {
        val parent = element.parent

        return when {
            // Return statement
            PsiTreeUtil.getParentOfType(element, JSReturnStatement::class.java) != null -> {
                "returned from function"
            }

            // Function argument
            parent is JSArgumentList -> {
                val callExpression = parent.parent as? JSCallExpression
                val methodName = (callExpression?.methodExpression as? JSReferenceExpression)?.referenceName
                "passed as argument to $methodName"
            }

            // Assignment
            parent is JSAssignmentExpression -> {
                when {
                    parent.lOperand == element -> "assigned to"
                    parent.rOperand == element -> "assigned from"
                    else -> null
                }
            }

            // Condition
            PsiTreeUtil.getParentOfType(element, JSIfStatement::class.java) != null ||
                    PsiTreeUtil.getParentOfType(element, JSWhileStatement::class.java) != null -> {
                "used in condition"
            }

            else -> null
        }
    }

    private fun getContainingMethod(element: PsiElement): String? {
        val function = PsiTreeUtil.getParentOfType(element, JSFunction::class.java)
        return function?.name
    }

    private fun getContainingClass(element: PsiElement): String? {
        val jsClass = PsiTreeUtil.getParentOfType(element, JSClass::class.java)
        return jsClass?.name
    }

    private fun getAccessModifier(element: PsiElement): String? {
        // For JavaScript, we use simplified approach with text-based analysis
        val parent = PsiTreeUtil.getParentOfType(element, JSAttributeListOwner::class.java)
        if (parent?.attributeList != null) {
            val attributeText = parent.attributeList?.text ?: ""
            return when {
                attributeText.contains("private") -> "private"
                attributeText.contains("protected") -> "protected"
                attributeText.contains("public") -> "public"
                else -> null
            }
        }
        return null
    }

    private fun isDeprecatedUsage(element: PsiElement, target: PsiElement): Boolean {
        // Check if the target is marked as deprecated
        if (target is JSAttributeListOwner) {
            target.attributeList?.decorators?.forEach { decorator ->
                if (decorator.decoratorName == "deprecated" || decorator.decoratorName == "Deprecated") {
                    return true
                }
            }
        }

        // Check JSDoc comments using visitor pattern
        var hasDeprecatedTag = false
        target.accept(object : com.intellij.psi.PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is JSDocComment) {
                    element.tags.forEach { tag ->
                        if (tag.name == "@deprecated") {
                            hasDeprecatedTag = true
                            return
                        }
                    }
                }
                super.visitElement(element)
            }
        })

        return hasDeprecatedTag
    }

    private fun generateInsights(
        target: PsiElement,
        references: List<ReferenceInfo>,
        groupedByType: Map<String, List<ReferenceInfo>>
    ): List<String> {
        val insights = mutableListOf<String>()

        // Primary usage pattern
        val primaryUsageType = groupedByType.maxByOrNull { it.value.size }?.key
        val primaryUsageFile = references
            .groupBy { it.filePath }
            .maxByOrNull { it.value.size }
            ?.key

        if (primaryUsageType != null && primaryUsageFile != null) {
            val count = groupedByType[primaryUsageType]?.size ?: 0
            insights.add("Primary usage as $primaryUsageType in $primaryUsageFile ($count calls)")
        }

        // Test coverage
        val testUsages = references.count { it.isInTestCode }
        if (testUsages == 0 && target is JSFunction) {
            insights.add("No test usage found - consider adding tests")
        } else if (testUsages > 0) {
            insights.add("Used in $testUsages test file(s)")
        }

        // Deprecated usage warnings
        val deprecatedUsages = references.count { it.isInDeprecatedCode }
        if (deprecatedUsages > 0) {
            insights.add("$deprecatedUsages usage(s) of deprecated API")
        }

        // Complexity insights
        if (target is JSFunction) {
            val methodCalls = groupedByType["method_call"]?.size ?: 0
            when {
                methodCalls == 0 -> insights.add("Unused function - consider removing")
                methodCalls == 1 -> insights.add("Used only once - consider inlining")
                methodCalls > 10 -> insights.add("Heavily used ($methodCalls times) - changes have wide impact")
            }
        }

        return insights
    }
}
