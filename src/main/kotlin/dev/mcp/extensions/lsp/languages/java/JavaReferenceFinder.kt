package dev.mcp.extensions.lsp.languages.java

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.*
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import dev.mcp.extensions.lsp.core.interfaces.ReferenceFinder
import dev.mcp.extensions.lsp.core.models.FindReferencesArgs
import dev.mcp.extensions.lsp.core.models.GroupedReferencesResult
import dev.mcp.extensions.lsp.core.models.ReferenceInfo
import dev.mcp.extensions.lsp.core.models.ReferenceSummary
import dev.mcp.extensions.lsp.core.utils.PsiUtils
import dev.mcp.extensions.lsp.languages.base.BaseLanguageHandler

/**
 * Reference finder implementation for Java and Kotlin languages.
 * 
 * Registered as a service in mcp-lsp-java.xml when Java module is available.
 */
class JavaReferenceFinder : BaseLanguageHandler(), ReferenceFinder {
    
    override fun findReferences(project: Project, element: PsiElement, args: FindReferencesArgs): List<ReferenceInfo> {
        logger.info("Finding references for element: ${(element as? PsiNamedElement)?.name}")
        
        val references = mutableListOf<ReferenceInfo>()
        val scope = GlobalSearchScope.projectScope(project)
        
        // Find all references to the element
        val query = ReferencesSearch.search(element, scope)
        
        query.forEach { reference ->
            val refElement = reference.element
            val location = createReferenceInfo(refElement, element)
            references.add(location)
        }
        
        // Special handling for constructors - find new expressions
        if (element is PsiMethod && element.isConstructor) {
            findConstructorCalls(element, scope).forEach { newExpr ->
                references.add(createReferenceInfo(newExpr, element))
            }
        }
        
        // Find overrides if it's a method
        if (element is PsiMethod) {
            findOverrides(element, scope).forEach { override ->
                references.add(createReferenceInfo(override, element, "method_override"))
            }
        }
        
        // Optionally include the declaration itself
        if (args.includeDeclaration) {
            references.add(0, createReferenceInfo(element, element, "declaration"))
        }
        
        logger.debug("Found ${references.size} references")
        return references
    }
    
    override fun findTargetElement(project: Project, args: FindReferencesArgs): PsiElement? {
        // If position is provided, find element at that position
        if (args.filePath != null && args.position != null) {
            val psiFile = PsiUtils.getPsiFile(project, args.filePath) ?: return null
            
            val element = psiFile.findElementAt(args.position) ?: return null
            
            // Try to resolve reference at this position
            val reference = element.parent?.reference ?: element.reference
            if (reference != null) {
                return reference.resolve()
            }
            
            // Try to find a named element at this position
            return PsiTreeUtil.getParentOfType(element, PsiNamedElement::class.java)
        }
        
        // Otherwise, search by name
        if (args.symbolName == null) return null
        
        val scope = GlobalSearchScope.projectScope(project)
        val cache = PsiShortNamesCache.getInstance(project)
        
        // Try to find as a class first
        JavaPsiFacade.getInstance(project).findClass(args.symbolName, scope)?.let { return it }
        
        // Try methods
        cache.getMethodsByName(args.symbolName, scope).firstOrNull()?.let { return it }
        
        // Try fields using cache
        cache.getFieldsByName(args.symbolName, scope).firstOrNull()?.let { return it }
        
        // Fallback: manually search through all Java files in project (for test scenarios)
        return findElementByManualSearch(project, args.symbolName, scope)
    }
    
    override fun createGroupedResult(references: List<ReferenceInfo>, element: PsiElement): GroupedReferencesResult {
        // Group references by usage type
        val usagesByType = references.groupBy { it.usageType }
        
        // Calculate summary statistics
        val fileCount = references.map { it.filePath }.distinct().size
        val hasTestUsages = references.any { it.isInTestCode }
        val deprecatedUsageCount = countDeprecatedUsages(references, element)
        
        // Find primary usage location
        val primaryUsageLocation = findPrimaryUsageLocation(references)
        
        val summary = ReferenceSummary(
            totalReferences = references.size,
            fileCount = fileCount,
            hasTestUsages = hasTestUsages,
            primaryUsageLocation = primaryUsageLocation,
            deprecatedUsageCount = deprecatedUsageCount
        )
        
        // Generate insights
        val insights = generateInsights(references, element, summary)
        
        return GroupedReferencesResult(
            summary = summary,
            usagesByType = usagesByType,
            insights = insights,
            allReferences = references
        )
    }
    
    override fun createReferenceInfo(element: PsiElement, target: PsiElement, overrideType: String?): ReferenceInfo {
        val containingFile = element.containingFile
        val virtualFile = containingFile.virtualFile
        val project = element.project
        val basePath = project.basePath ?: ""
        val relativePath = virtualFile.path.removePrefix(basePath).removePrefix("/")
        
        val textRange = element.textRange
        val document = PsiDocumentManager.getInstance(project).getDocument(containingFile)
        val lineNumber = document?.getLineNumber(textRange.startOffset) ?: 0
        
        // Get the line containing the reference for preview
        val lineStartOffset = document?.getLineStartOffset(lineNumber) ?: 0
        val lineEndOffset = document?.getLineEndOffset(lineNumber) ?: textRange.endOffset
        val lineText = document?.text?.substring(lineStartOffset, lineEndOffset)?.trim() ?: element.text
        
        // Find containing method and class
        val containingMethod = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
        val containingClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
        
        // Check if in test code
        val isInTest = isInTestCode(virtualFile) || isInTestMethod(element)
        
        // Check if in comment or JavaDoc
        val isInComment = isInJavaDoc(element) || PsiTreeUtil.getParentOfType(element, PsiComment::class.java) != null
        
        // Check if in deprecated code
        val isInDeprecatedCode = containingMethod?.hasAnnotation("java.lang.Deprecated") == true ||
                                 containingClass?.hasAnnotation("java.lang.Deprecated") == true
        
        // Get access context
        val accessModifier = when (element.parent) {
            is PsiReferenceExpression -> {
                val qualifier = (element.parent as PsiReferenceExpression).qualifierExpression
                when {
                    qualifier == null -> "implicit"
                    qualifier.text == "this" -> "this"
                    qualifier.text == "super" -> "super"
                    else -> "qualified"
                }
            }
            else -> null
        }
        
        // Get surrounding context (2 lines before and after)
        val surroundingContext = if (document != null && lineNumber > 0) {
            val startLine = (lineNumber - 2).coerceAtLeast(0)
            val endLine = (lineNumber + 2).coerceAtMost(document.lineCount - 1)
            val contextLines = (startLine..endLine).map { line ->
                val start = document.getLineStartOffset(line)
                val end = document.getLineEndOffset(line)
                val prefix = if (line == lineNumber) ">>> " else "    "
                prefix + document.text.substring(start, end).trim()
            }
            contextLines.joinToString("\n")
        } else null
        
        // Get data flow context
        val dataFlowContext = getDataFlowContext(element, target)
        
        return ReferenceInfo(
            filePath = relativePath,
            startOffset = textRange.startOffset,
            endOffset = textRange.endOffset,
            lineNumber = lineNumber + 1, // Convert to 1-based
            usageType = overrideType ?: determineUsageType(element, target),
            elementText = element.text?.trim(),
            preview = lineText,
            containingMethod = containingMethod?.name,
            containingClass = containingClass?.qualifiedName,
            isInTestCode = isInTest,
            isInComment = isInComment,
            accessModifier = accessModifier,
            surroundingContext = surroundingContext,
            dataFlowContext = dataFlowContext,
            isInDeprecatedCode = isInDeprecatedCode
        )
    }
    
    override fun supportsElement(element: PsiElement): Boolean {
        val languageId = element.language.id
        return languageId == "JAVA" || languageId == "kotlin" || languageId == "Kotlin"
    }
    
    override fun getSupportedLanguage(): String {
        return "Java/Kotlin"
    }
    
    private fun findPrimaryUsageLocation(references: List<ReferenceInfo>): String? {
        if (references.isEmpty()) return null
        
        return references
            .filter { it.containingClass != null && !it.isInTestCode }
            .groupBy { it.containingClass }
            .maxByOrNull { it.value.size }
            ?.key
    }
    
    private fun countDeprecatedUsages(references: List<ReferenceInfo>, element: PsiElement): Int {
        return references.count { it.isInDeprecatedCode }
    }
    
    // Continue in next part...
    
    private fun generateInsights(references: List<ReferenceInfo>, element: PsiElement, summary: ReferenceSummary): List<String> {
        val insights = mutableListOf<String>()
        
        // Primary usage insight
        if (summary.primaryUsageLocation != null) {
            val primaryCount = references.count { it.containingClass == summary.primaryUsageLocation }
            insights.add("Primary usage is in ${summary.primaryUsageLocation.substringAfterLast('.')} ($primaryCount ${if (primaryCount == 1) "call" else "calls"})")
        }
        
        // Test coverage insight
        if (!summary.hasTestUsages && references.isNotEmpty()) {
            insights.add("No usage found in test code - consider adding tests")
        } else if (summary.hasTestUsages) {
            val testCount = references.count { it.isInTestCode }
            val testPercentage = (testCount * 100) / references.size
            if (testPercentage < 20 && references.size > 5) {
                insights.add("Only $testPercentage% of usages are in tests - consider increasing test coverage")
            }
        }
        
        // Deprecated usage insight
        if (summary.deprecatedUsageCount > 0) {
            insights.add("${summary.deprecatedUsageCount} deprecated ${if (summary.deprecatedUsageCount == 1) "usage" else "usages"} found - consider updating")
        }
        
        // Method-specific insights
        if (element is PsiMethod) {
            val overrideCount = references.count { it.usageType == "method_override" }
            if (overrideCount > 0) {
                insights.add("Method is overridden $overrideCount ${if (overrideCount == 1) "time" else "times"} - changes will affect subclasses")
            }
            
            if (element.name?.startsWith("get") == true || element.name?.startsWith("set") == true) {
                val directFieldAccess = findDirectFieldAccess(element)
                if (directFieldAccess > 0) {
                    insights.add("Field has $directFieldAccess direct ${if (directFieldAccess == 1) "access" else "accesses"} bypassing this ${if (element.name?.startsWith("get") == true) "getter" else "setter"}")
                }
            }
        }
        
        // Field-specific insights
        if (element is PsiField) {
            val writeCount = references.count { it.usageType == "field_write" }
            val readCount = references.count { it.usageType == "field_read" || it.usageType == "field_as_argument" }
            
            if (writeCount == 0 && readCount > 0) {
                insights.add("Field is never modified after initialization - consider making it final")
            } else if (writeCount > readCount && readCount > 0) {
                insights.add("Field is written more often than read ($writeCount writes, $readCount reads)")
            }
            
            if (element.hasModifierProperty(PsiModifier.PUBLIC)) {
                insights.add("Public field has ${references.size} direct ${if (references.size == 1) "access" else "accesses"} - consider using getter/setter")
            }
        }
        
        // Usage pattern insights
        val methodCallTypes = references.filter { it.usageType.endsWith("_call") }
        if (methodCallTypes.size > 10) {
            val mostCommonCaller = references
                .filter { it.containingClass != null }
                .groupBy { it.containingClass }
                .maxByOrNull { it.value.size }
            
            if (mostCommonCaller != null && mostCommonCaller.value.size > references.size / 2) {
                insights.add("Heavily coupled to ${mostCommonCaller.key?.substringAfterLast('.')} - consider refactoring")
            }
        }
        
        // Comment/JavaDoc references
        val commentRefs = references.count { it.isInComment }
        if (commentRefs > 0) {
            insights.add("$commentRefs ${if (commentRefs == 1) "reference" else "references"} in comments/JavaDoc - update documentation if changing")
        }
        
        return insights
    }
    
    private fun findDirectFieldAccess(method: PsiMethod): Int {
        // Simple heuristic: if it's a getter/setter, find the corresponding field
        val fieldName = when {
            method.name?.startsWith("get") == true -> method.name?.substring(3)?.decapitalize()
            method.name?.startsWith("set") == true -> method.name?.substring(3)?.decapitalize()
            else -> return 0
        }
        
        val field = method.containingClass?.findFieldByName(fieldName ?: return 0, false)
        if (field != null) {
            // Count direct accesses to this field (this is a simplified check)
            val scope = GlobalSearchScope.projectScope(method.project)
            var count = 0
            ReferencesSearch.search(field, scope).forEach { _ ->
                count++
            }
            return count
        }
        
        return 0
    }
    
    private fun String.decapitalize(): String = 
        if (isEmpty()) this else this[0].lowercaseChar() + substring(1)

    private fun findElementByManualSearch(project: Project, symbolName: String, scope: GlobalSearchScope): PsiElement? {
        val psiManager = PsiManager.getInstance(project)
        val fileIndex = ProjectFileIndex.getInstance(project)
        
        var foundElement: PsiElement? = null
        var javaFileCount = 0
        
        logger.debug("Starting manual search for '$symbolName'")
        
        // Search through all Java files in the scope
        fileIndex.iterateContent { virtualFile ->
            if (foundElement != null) return@iterateContent false // Stop if found
            
            if (virtualFile.extension == "java" && scope.contains(virtualFile)) {
                javaFileCount++
                logger.debug("Searching file: ${virtualFile.path}")
                val psiFile = psiManager.findFile(virtualFile) as? PsiJavaFile
                psiFile?.classes?.forEach { psiClass ->
                    logger.debug("Searching class: ${psiClass.qualifiedName}")
                    logger.debug("Class has ${psiClass.fields.size} fields: ${psiClass.fields.map { it.name }}")
                    
                    // Search for fields
                    psiClass.fields.find { it.name == symbolName }?.let { 
                        logger.debug("Found field: ${it.name}")
                        foundElement = it
                        return@forEach
                    }
                    // Search for methods
                    psiClass.methods.find { it.name == symbolName }?.let { 
                        foundElement = it
                        return@forEach
                    }
                    // Search for inner classes
                    psiClass.innerClasses.find { it.name == symbolName }?.let { 
                        foundElement = it
                        return@forEach
                    }
                }
            }
            true // Continue iteration
        }
        
        logger.debug("Manual search complete. Searched $javaFileCount Java files. Found: $foundElement")
        return foundElement
    }

    private fun getDataFlowContext(element: PsiElement, target: PsiElement): String? {
        val parent = element.parent
        
        return when {
            // Assignment contexts
            parent is PsiAssignmentExpression && parent.lExpression == element -> "assigned to"
            parent is PsiAssignmentExpression && parent.rExpression == element -> "assigned from"
            
            // Method contexts
            parent is PsiReturnStatement -> "returned from method"
            parent is PsiExpressionList && parent.parent is PsiMethodCallExpression -> "passed as argument"
            
            // Control flow contexts
            isInCondition(element) -> "used in condition"
            parent is PsiIfStatement -> "condition check"
            parent is PsiWhileStatement -> "loop condition"
            parent is PsiForStatement -> "loop initialization"
            
            // Field initialization
            parent is PsiField -> "field initializer"
            parent is PsiNewExpression -> "constructor argument"
            
            // Type contexts
            parent is PsiTypeElement && parent.parent is PsiLocalVariable -> "variable declaration"
            parent is PsiTypeElement && parent.parent is PsiParameter -> "parameter declaration"
            parent is PsiTypeElement && parent.parent is PsiMethod -> "return type declaration"
            
            // Expression contexts
            parent is PsiPrefixExpression -> "prefix operation"
            parent is PsiPostfixExpression -> "postfix operation"
            parent is PsiBinaryExpression -> "binary operation"
            
            else -> null
        }
    }

    private fun determineUsageType(element: PsiElement, target: PsiElement): String {
        val parent = element.parent
        
        return when {
            element == target -> "declaration"
            
            // Method calls - be more specific
            parent is PsiMethodCallExpression -> {
                val method = (target as? PsiMethod)
                when {
                    method?.name == "equals" -> "equals_call"
                    method?.name == "toString" -> "toString_call"
                    method?.name == "hashCode" -> "hashCode_call"
                    method?.name?.startsWith("get") == true -> "getter_call"
                    method?.name?.startsWith("set") == true -> "setter_call"
                    method?.name?.startsWith("is") == true -> "boolean_check"
                    method?.hasModifierProperty(PsiModifier.STATIC) == true -> "static_method_call"
                    isInTestMethod(element) -> "test_method_call"
                    else -> "method_call"
                }
            }
            
            // Constructor calls
            parent is PsiNewExpression -> {
                when {
                    parent.arrayInitializer != null -> "array_creation"
                    isInTestMethod(element) -> "test_instantiation"
                    else -> "constructor_call"
                }
            }
            
            // Field access - distinguish between different contexts
            parent is PsiReferenceExpression -> {
                val grandParent = parent.parent
                when {
                    grandParent is PsiAssignmentExpression && grandParent.lExpression == parent -> "field_write"
                    grandParent is PsiAssignmentExpression -> "field_read"
                    grandParent is PsiPrefixExpression -> "field_increment"
                    grandParent is PsiPostfixExpression -> "field_increment"
                    isInCondition(parent) -> "field_condition_check"
                    isInMethodCall(parent) -> "field_as_argument"
                    else -> "field_read"
                }
            }
            
            // Type references - be more specific
            parent is PsiTypeElement -> {
                val context = PsiTreeUtil.getParentOfType(parent, 
                    PsiLocalVariable::class.java,
                    PsiParameter::class.java,
                    PsiMethod::class.java,
                    PsiField::class.java,
                    PsiCatchSection::class.java,
                    PsiInstanceOfExpression::class.java,
                    PsiTypeCastExpression::class.java
                )
                when (context) {
                    is PsiLocalVariable -> "local_variable_type"
                    is PsiParameter -> "parameter_type"
                    is PsiMethod -> "return_type"
                    is PsiField -> "field_type"
                    is PsiCatchSection -> "catch_type"
                    is PsiInstanceOfExpression -> "instanceof_check"
                    is PsiTypeCastExpression -> "type_cast"
                    else -> "type_reference"
                }
            }
            
            parent is PsiImportStatement -> {
                when {
                    parent.isOnDemand -> "wildcard_import"
                    else -> "import"
                }
            }
            
            // Method overrides
            element is PsiMethod && target is PsiMethod && isOverride(element, target) -> "method_override"
            
            // Annotations
            parent is PsiAnnotation -> "annotation_use"
            
            // JavaDoc references
            isInJavaDoc(element) -> "javadoc_reference"
            
            else -> "reference"
        }
    }
    
    private fun isInCondition(element: PsiElement): Boolean {
        return PsiTreeUtil.getParentOfType(element, 
            PsiIfStatement::class.java, 
            PsiWhileStatement::class.java,
            PsiConditionalExpression::class.java,
            PsiSwitchStatement::class.java
        ) != null
    }
    
    private fun isInTestMethod(element: PsiElement): Boolean {
        val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
        return method?.hasAnnotation("org.junit.Test") == true ||
               method?.hasAnnotation("org.junit.jupiter.api.Test") == true ||
               method?.name?.startsWith("test") == true
    }
    
    private fun isInMethodCall(element: PsiElement): Boolean {
        return PsiTreeUtil.getParentOfType(element, PsiExpressionList::class.java) != null
    }
    
    private fun isInJavaDoc(element: PsiElement): Boolean {
        return PsiTreeUtil.getParentOfType(element, PsiDocComment::class.java) != null
    }

    private fun findConstructorCalls(constructor: PsiMethod, scope: GlobalSearchScope): List<PsiNewExpression> {
        val containingClass = constructor.containingClass ?: return emptyList()
        val result = mutableListOf<PsiNewExpression>()
        
        ReferencesSearch.search(containingClass, scope).forEach { ref ->
            val element = ref.element
            val parent = element.parent
            if (parent is PsiNewExpression) {
                result.add(parent)
            }
        }
        
        return result
    }

    private fun findOverrides(method: PsiMethod, scope: GlobalSearchScope): List<PsiMethod> {
        val result = mutableListOf<PsiMethod>()
        val containingClass = method.containingClass ?: return result
        
        // Find all subclasses
        val allClasses = PsiShortNamesCache.getInstance(method.project)
            .getClassesByName("*", scope)
            .toList()
        
        for (psiClass in allClasses) {
            if (psiClass.isInheritor(containingClass, true)) {
                val overrideMethod = psiClass.findMethodBySignature(method, false)
                if (overrideMethod != null && overrideMethod != method) {
                    result.add(overrideMethod)
                }
            }
        }
        
        return result
    }

    private fun isOverride(method: PsiMethod, baseMethod: PsiMethod): Boolean {
        val methodClass = method.containingClass ?: return false
        val baseClass = baseMethod.containingClass ?: return false
        
        return methodClass != baseClass && 
               methodClass.isInheritor(baseClass, true) &&
               method.findSuperMethods().contains(baseMethod)
    }
}

