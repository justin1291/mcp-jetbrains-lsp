package dev.mcp.extensions.lsp.languages.jvm

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import dev.mcp.extensions.lsp.core.interfaces.ReferenceFinder
import dev.mcp.extensions.lsp.core.models.FindReferencesArgs
import dev.mcp.extensions.lsp.core.models.GroupedReferencesResult
import dev.mcp.extensions.lsp.core.models.ReferenceInfo
import dev.mcp.extensions.lsp.core.models.ReferenceSummary
import dev.mcp.extensions.lsp.core.utils.PsiUtils
import dev.mcp.extensions.lsp.languages.base.BaseLanguageHandler
import org.jetbrains.uast.*

/**
 * Enhanced UAST-based reference finder for JVM languages with improved static reference tracking.
 * 
 * **IMPROVEMENTS:**
 * 1. **Static field reference tracking**: Properly captures Class.FIELD usage patterns
 * 2. **Import statement tracking**: Includes import statements as references
 * 3. **Enhanced qualified reference handling**: Better support for nested member access
 * 4. **Cross-file reference detection**: Improved project-wide reference search
 */
@Service
class JvmReferenceFinder : BaseLanguageHandler(), ReferenceFinder {

    override fun findReferences(project: Project, element: PsiElement, args: FindReferencesArgs): List<ReferenceInfo> {
        logger.info("Finding references for element: ${(element as? PsiNamedElement)?.name}")

        return measureOperation("findReferences") {
            val references = mutableListOf<ReferenceInfo>()
            val scope = GlobalSearchScope.projectScope(project)

            // Standard reference search
            val query = ReferencesSearch.search(element, scope)
            query.forEach { reference ->
                val refElement = reference.element
                references.add(createReferenceInfo(refElement, element))
            }

            // **IMPROVEMENT 1**: Enhanced static field reference tracking
            if (element is PsiField && element.hasModifierProperty(PsiModifier.STATIC)) {
                findStaticFieldReferences(element, project, scope).forEach { ref ->
                    references.add(ref)
                }
            }

            // **IMPROVEMENT 2**: Track import statements
            if (element is PsiClass || element is PsiMethod || element is PsiField) {
                findImportReferences(element, project, scope).forEach { ref ->
                    references.add(ref)
                }
            }

            // Special handling for constructors
            if (element is PsiMethod && element.isConstructor) {
                findConstructorCalls(element, scope).forEach { newExpr ->
                    references.add(createReferenceInfo(newExpr, element))
                }
            }

            // Find overrides
            if (element is PsiMethod) {
                findOverrides(element, scope).forEach { override ->
                    references.add(createReferenceInfo(override, element, "method_override"))
                }
            }

            // Find implementations
            if (element is PsiMethod && element.containingClass?.isInterface == true) {
                findImplementations(element, scope).forEach { implementation ->
                    references.add(createReferenceInfo(implementation, element, "method_implementation"))
                }
            }

            // Find subclasses
            if (element is PsiClass) {
                findSubclasses(element, scope).forEach { subclass ->
                    references.add(createReferenceInfo(subclass, element, "class_inheritance"))
                }
            }

            // Include declaration if requested
            if (args.includeDeclaration) {
                references.add(0, createReferenceInfo(element, element, "declaration"))
            }

            logger.debug("Found ${references.size} references")
            references
        }
    }

    /**
     * **NEW**: Find static field references including qualified access
     */
    private fun findStaticFieldReferences(
        field: PsiField,
        project: Project,
        scope: GlobalSearchScope
    ): List<ReferenceInfo> {
        val references = mutableListOf<ReferenceInfo>()
        val containingClass = field.containingClass ?: return references
        val fieldName = field.name ?: return references

        try {
            // Search for qualified references (ClassName.FIELD_NAME)
            val classReferences = ReferencesSearch.search(containingClass, scope)
            
            classReferences.forEach { classRef ->
                val element = classRef.element
                val parent = element.parent
                
                // Check if this is a qualified expression accessing our field
                if (parent is PsiReferenceExpression) {
                    val grandParent = parent.parent
                    if (grandParent is PsiReferenceExpression && 
                        grandParent.referenceName == fieldName) {
                        references.add(createReferenceInfo(grandParent, field, "static_field_access"))
                    }
                }
            }

            // Also search for direct field references (in case of static import)
            val directRefs = ReferencesSearch.search(field, scope)
            directRefs.forEach { ref ->
                val refElement = ref.element
                if (!references.any { it.startOffset == refElement.textRange.startOffset }) {
                    references.add(createReferenceInfo(refElement, field, "static_field_access"))
                }
            }

        } catch (e: Exception) {
            logger.warn("Error finding static field references", e)
        }

        return references
    }

    /**
     * **NEW**: Find import statements that reference this element
     */
    private fun findImportReferences(
        element: PsiElement,
        project: Project,
        scope: GlobalSearchScope
    ): List<ReferenceInfo> {
        val references = mutableListOf<ReferenceInfo>()
        val qualifiedName = when (element) {
            is PsiClass -> element.qualifiedName
            is PsiMethod -> {
                val containingClass = element.containingClass
                containingClass?.qualifiedName?.let { "$it.${element.name}" }
            }
            is PsiField -> {
                val containingClass = element.containingClass
                containingClass?.qualifiedName?.let { "$it.${element.name}" }
            }
            else -> null
        } ?: return references

        try {
            // Search through all files in the project
            val psiManager = PsiManager.getInstance(project)
            val fileIndex = com.intellij.openapi.roots.ProjectFileIndex.getInstance(project)
            
            fileIndex.iterateContent { virtualFile ->
                if (virtualFile.extension in setOf("java", "kt", "scala", "groovy") && 
                    scope.contains(virtualFile)) {
                    
                    val psiFile = psiManager.findFile(virtualFile)
                    if (psiFile is PsiJavaFile) {
                        // Check import statements
                        psiFile.importList?.allImportStatements?.forEach { importStmt ->
                            val importedQName = when (importStmt) {
                                is PsiImportStatement -> importStmt.qualifiedName
                                is PsiImportStaticStatement -> importStmt.referenceName?.let {
                                    "${importStmt.importReference?.qualifiedName}.$it"
                                }
                                else -> null
                            }
                            
                            if (importedQName == qualifiedName || 
                                (importStmt.isOnDemand && qualifiedName.startsWith(importedQName ?: ""))) {
                                references.add(createReferenceInfo(importStmt, element, "import"))
                            }
                        }
                    }
                }
                true
            }
        } catch (e: Exception) {
            logger.warn("Error finding import references", e)
        }

        return references
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

        // Find containing method and class using UAST when possible
        val uElement = element.toUElement()
        val containingUMethod = uElement?.getParentOfType<UMethod>()
        val containingUClass = uElement?.getParentOfType<UClass>()

        // Fallback to PSI if UAST doesn't work
        val containingMethod = containingUMethod?.javaPsi as? PsiMethod
            ?: PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
        val containingClass = containingUClass?.javaPsi as? PsiClass
            ?: PsiTreeUtil.getParentOfType(element, PsiClass::class.java)

        // Check if in test code
        val isInTest = isInTestCode(virtualFile) || isInTestMethod(element)

        // Check if in comment or JavaDoc
        val isInComment = isInJavaDoc(element) || PsiTreeUtil.getParentOfType(element, PsiComment::class.java) != null

        // Check if in deprecated code
        val isInDeprecatedCode = containingMethod?.hasAnnotation("java.lang.Deprecated") == true ||
                                containingClass?.hasAnnotation("java.lang.Deprecated") == true ||
                                containingMethod?.hasAnnotation("kotlin.Deprecated") == true ||
                                containingClass?.hasAnnotation("kotlin.Deprecated") == true

        // Get access context using UAST
        val accessModifier = getAccessModifier(element, uElement)

        // Get surrounding context (2 lines before and after)
        val surroundingContext = getSurroundingContext(document, lineNumber)

        // Get data flow context using UAST
        val dataFlowContext = getDataFlowContext(element, target)

        // Detect language-specific features
        val languageFeatures = detectLanguageFeatures(element, uElement)

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
            isInDeprecatedCode = isInDeprecatedCode,
            languageFeatures = languageFeatures
        )
    }

    /**
     * **IMPROVED**: Enhanced usage type determination with better static member detection
     */
    private fun determineUsageType(element: PsiElement, target: PsiElement): String {
        val parent = element.parent
        val uElement = element.toUElement()
        val uParent = uElement?.uastParent

        return when {
            element == target -> "declaration"
            
            // **IMPROVEMENT**: Enhanced static field access detection
            parent is PsiReferenceExpression && target is PsiField && 
            target.hasModifierProperty(PsiModifier.STATIC) -> {
                val grandParent = parent.parent
                when {
                    grandParent is PsiAssignmentExpression && grandParent.lExpression == parent -> "static_field_write"
                    grandParent is PsiAssignmentExpression -> "static_field_read"
                    else -> "static_field_access"
                }
            }

            // Import statements
            element is PsiImportStatement || element is PsiImportStaticStatement -> {
                when {
                    element is PsiImportStaticStatement -> "static_import"
                    (element as? PsiImportStatement)?.isOnDemand == true -> "wildcard_import"
                    else -> "import"
                }
            }

            // UAST-based method call detection
            uParent is UCallExpression -> {
                val method = (target as? PsiMethod)
                when {
                    method?.hasModifierProperty(PsiModifier.STATIC) == true -> "static_method_call"
                    method?.name == "equals" -> "equals_call"
                    method?.name == "toString" -> "toString_call"
                    method?.name == "hashCode" -> "hashCode_call"
                    method?.name?.startsWith("get") == true -> "getter_call"
                    method?.name?.startsWith("set") == true -> "setter_call"
                    method?.name?.startsWith("is") == true -> "boolean_check"
                    isInTestMethod(element) -> "test_method_call"
                    isKotlinExtensionFunction(method) -> "extension_function_call"
                    isKotlinSuspendFunction(method) -> "suspend_function_call"
                    else -> "method_call"
                }
            }

            // Regular PSI method calls
            parent is PsiMethodCallExpression -> {
                val method = (target as? PsiMethod)
                when {
                    method?.hasModifierProperty(PsiModifier.STATIC) == true -> "static_method_call"
                    method?.name == "equals" -> "equals_call"
                    method?.name == "toString" -> "toString_call"
                    method?.name == "hashCode" -> "hashCode_call"
                    method?.name?.startsWith("get") == true -> "getter_call"
                    method?.name?.startsWith("set") == true -> "setter_call"
                    method?.name?.startsWith("is") == true -> "boolean_check"
                    isInTestMethod(element) -> "test_method_call"
                    else -> "method_call"
                }
            }

            // Constructor calls
            parent is PsiNewExpression || uParent is UCallExpression && 
            (target as? PsiMethod)?.isConstructor == true -> {
                when {
                    parent is PsiNewExpression && parent.arrayInitializer != null -> "array_creation"
                    isInTestMethod(element) -> "test_instantiation"
                    else -> "constructor_call"
                }
            }

            // Field access with enhanced context detection
            parent is PsiReferenceExpression || uParent is UReferenceExpression -> {
                val grandParent = parent.parent
                when {
                    grandParent is PsiAssignmentExpression && grandParent.lExpression == parent -> "field_write"
                    grandParent is PsiAssignmentExpression -> "field_read"
                    grandParent is PsiPrefixExpression -> "field_increment"
                    grandParent is PsiPostfixExpression -> "field_increment"
                    isInCondition(parent) -> "field_condition_check"
                    isInMethodCall(parent) -> "field_as_argument"
                    isKotlinPropertyAccess(element) -> "property_access"
                    else -> "field_read"
                }
            }

            // Type references
            parent is PsiTypeElement || uParent is UTypeReferenceExpression -> {
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

            // Method overrides
            element is PsiMethod && target is PsiMethod && isOverride(element, target) -> "method_override"

            // Annotations
            parent is PsiAnnotation -> "annotation_use"

            // JavaDoc references
            isInJavaDoc(element) -> "javadoc_reference"

            // Kotlin-specific patterns
            isKotlinLambdaUsage(element) -> "lambda_usage"
            isKotlinDelegateUsage(element) -> "delegate_usage"

            else -> "reference"
        }
    }

    /**
     * **IMPROVED**: Enhanced data flow context with static member awareness
     */
    private fun getDataFlowContext(element: PsiElement, target: PsiElement): String? {
        val parent = element.parent
        val uElement = element.toUElement()
        val uParent = uElement?.uastParent

        return when {
            // Static field specific contexts
            parent is PsiReferenceExpression && target is PsiField && 
            target.hasModifierProperty(PsiModifier.STATIC) -> {
                val grandParent = parent.parent
                when {
                    grandParent is PsiAssignmentExpression && grandParent.lExpression == parent -> 
                        "static field assignment"
                    grandParent is PsiMethodCallExpression -> 
                        "static field as method argument"
                    grandParent is PsiReturnStatement -> 
                        "static field returned"
                    else -> "static field access"
                }
            }

            // UAST-based assignment contexts
            uParent is UBinaryExpression && uParent.operator.text == "=" -> {
                if (uParent.leftOperand == uElement) "assigned to" else "assigned from"
            }

            // PSI-based assignment contexts
            parent is PsiAssignmentExpression && parent.lExpression == element -> "assigned to"
            parent is PsiAssignmentExpression && parent.rExpression == element -> "assigned from"

            // Method contexts
            uParent is UReturnExpression -> "returned from method"
            uParent is UCallExpression -> "passed as argument"
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

            // Import contexts
            element is PsiImportStatement || element is PsiImportStaticStatement -> "import declaration"

            else -> null
        }
    }

    /**
     * **IMPROVED**: Enhanced insights with static member usage awareness
     */
    override fun createGroupedResult(references: List<ReferenceInfo>, element: PsiElement): GroupedReferencesResult {
        return measureOperation("createGroupedResult") {
            val usagesByType = references.groupBy { it.usageType }
            val fileCount = references.map { it.filePath }.distinct().size
            val hasTestUsages = references.any { it.isInTestCode }
            val deprecatedUsageCount = countDeprecatedUsages(references, element)

            val primaryUsageLocation = findPrimaryUsageLocation(references)

            val summary = ReferenceSummary(
                totalReferences = references.size,
                fileCount = fileCount,
                hasTestUsages = hasTestUsages,
                primaryUsageLocation = primaryUsageLocation,
                deprecatedUsageCount = deprecatedUsageCount
            )

            val insights = generateEnhancedInsights(references, element, summary)

            GroupedReferencesResult(
                summary = summary,
                usagesByType = usagesByType,
                insights = insights,
                allReferences = references
            )
        }
    }

    /**
     * **IMPROVED**: Enhanced insights generation with static usage patterns
     */
    private fun generateEnhancedInsights(
        references: List<ReferenceInfo>, 
        element: PsiElement, 
        summary: ReferenceSummary
    ): List<String> {
        val insights = mutableListOf<String>()

        // Primary usage insight
        if (summary.primaryUsageLocation != null) {
            val primaryCount = references.count { it.containingClass == summary.primaryUsageLocation }
            insights.add("Primary usage is in ${summary.primaryUsageLocation.substringAfterLast('.')} ($primaryCount ${if (primaryCount == 1) "call" else "calls"})")
        }

        // Static member usage insights
        if (element is PsiField && element.hasModifierProperty(PsiModifier.STATIC)) {
            val staticAccesses = references.count { it.usageType.startsWith("static_field") }
            if (staticAccesses > 0) {
                insights.add("Static field accessed $staticAccesses ${if (staticAccesses == 1) "time" else "times"}")
            }
            
            val staticWrites = references.count { it.usageType == "static_field_write" }
            if (staticWrites > 0 && element.hasModifierProperty(PsiModifier.FINAL)) {
                insights.add("Warning: Final static field has $staticWrites write ${if (staticWrites == 1) "attempt" else "attempts"}")
            }
        }

        // Import usage insights
        val importCount = references.count { it.usageType.endsWith("import") }
        if (importCount > 0) {
            val wildcardImports = references.count { it.usageType == "wildcard_import" }
            if (wildcardImports > 0) {
                insights.add("Used in $wildcardImports wildcard ${if (wildcardImports == 1) "import" else "imports"} - consider specific imports")
            }
        }

        // Cross-language usage insights
        val languageUsage = references.groupBy { ref ->
            ref.languageFeatures?.get("language") ?: "java"
        }
        if (languageUsage.size > 1) {
            val langs = languageUsage.keys.joinToString(", ")
            insights.add("Used across multiple JVM languages: $langs")
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
            val implementationCount = references.count { it.usageType == "method_implementation" }
            val staticCallCount = references.count { it.usageType == "static_method_call" }

            if (overrideCount > 0) {
                insights.add("Method is overridden $overrideCount ${if (overrideCount == 1) "time" else "times"} - changes will affect subclasses")
            }

            if (implementationCount > 0) {
                insights.add("Interface method has $implementationCount ${if (implementationCount == 1) "implementation" else "implementations"}")
            }

            if (staticCallCount > 0 && !element.hasModifierProperty(PsiModifier.STATIC)) {
                insights.add("Warning: Non-static method called as static $staticCallCount ${if (staticCallCount == 1) "time" else "times"}")
            }
        }

        // Field-specific insights
        if (element is PsiField) {
            val writeCount = references.count { it.usageType.endsWith("_write") }
            val readCount = references.count { it.usageType.endsWith("_read") || it.usageType == "field_as_argument" }

            if (writeCount == 0 && readCount > 0 && !element.hasModifierProperty(PsiModifier.FINAL)) {
                insights.add("Field is never modified after initialization - consider making it final")
            } else if (writeCount > readCount && readCount > 0) {
                insights.add("Field is written more often than read ($writeCount writes, $readCount reads)")
            }

            if (element.hasModifierProperty(PsiModifier.PUBLIC) && !element.hasModifierProperty(PsiModifier.STATIC)) {
                insights.add("Public field has ${references.size} direct ${if (references.size == 1) "access" else "accesses"} - consider using getter/setter")
            }
        }

        // Class-specific insights
        if (element is PsiClass) {
            val inheritanceCount = references.count { it.usageType == "class_inheritance" }
            val instantiationCount = references.count { it.usageType == "constructor_call" }
            
            if (inheritanceCount > 0) {
                insights.add("Class has $inheritanceCount ${if (inheritanceCount == 1) "subclass" else "subclasses"}")
            }

            if (instantiationCount == 0 && inheritanceCount == 0 && !element.isInterface) {
                insights.add("Class is never instantiated or extended - consider if it's needed")
            }
        }

        return insights
    }

    // Include all the helper methods from the original JvmReferenceFinder...
    
    private fun detectLanguageFeatures(element: PsiElement, uElement: UElement?): Map<String, String> {
        val features = mutableMapOf<String, String>()
        val languageId = element.language.id.lowercase()
        
        features["language"] = languageId

        when (languageId) {
            "kotlin" -> {
                addKotlinFeatures(element, uElement, features)
            }
            "scala" -> {
                addScalaFeatures(element, uElement, features)
            }
            "groovy" -> {
                addGroovyFeatures(element, uElement, features)
            }
        }

        return features
    }

    private fun addKotlinFeatures(element: PsiElement, uElement: UElement?, features: MutableMap<String, String>) {
        val text = element.text
        if (text?.contains("suspend") == true) {
            features["kotlinFeature"] = "suspend_function"
        }
        if (text?.contains("inline") == true) {
            features["kotlinFeature"] = "inline_function"
        }
        if (text?.contains("extension") == true) {
            features["kotlinFeature"] = "extension_function"
        }
        if (text?.contains("?.") == true) {
            features["kotlinFeature"] = "safe_call"
        }
        if (text?.contains("!!") == true) {
            features["kotlinFeature"] = "non_null_assertion"
        }
    }

    private fun addScalaFeatures(element: PsiElement, uElement: UElement?, features: MutableMap<String, String>) {
        val text = element.text
        if (text?.contains("implicit") == true) {
            features["scalaFeature"] = "implicit"
        }
        if (text?.contains("case") == true) {
            features["scalaFeature"] = "case_class"
        }
        if (text?.contains("trait") == true) {
            features["scalaFeature"] = "trait"
        }
    }

    private fun addGroovyFeatures(element: PsiElement, uElement: UElement?, features: MutableMap<String, String>) {
        val text = element.text
        if (text?.contains("def") == true) {
            features["groovyFeature"] = "dynamic_method"
        }
        if (text?.contains("@") == true) {
            features["groovyFeature"] = "annotation"
        }
    }

    private fun getAccessModifier(element: PsiElement, uElement: UElement?): String? {
        val parent = element.parent

        return when {
            parent is PsiReferenceExpression -> {
                val qualifier = parent.qualifierExpression
                when {
                    qualifier == null -> "implicit"
                    qualifier.text == "this" -> "this"
                    qualifier.text == "super" -> "super"
                    else -> "qualified"
                }
            }
            uElement is UReferenceExpression -> {
                val receiverText = uElement.sourcePsi?.text
                when {
                    receiverText == null -> "implicit"
                    receiverText.contains("this") -> "this"
                    receiverText.contains("super") -> "super"
                    else -> "qualified"
                }
            }
            else -> null
        }
    }

    private fun getSurroundingContext(document: com.intellij.openapi.editor.Document?, lineNumber: Int): String? {
        if (document == null || lineNumber < 0) return null

        val startLine = (lineNumber - 2).coerceAtLeast(0)
        val endLine = (lineNumber + 2).coerceAtMost(document.lineCount - 1)

        val contextLines = (startLine..endLine).map { line ->
            val start = document.getLineStartOffset(line)
            val end = document.getLineEndOffset(line)
            val prefix = if (line == lineNumber) ">>> " else "    "
            prefix + document.text.substring(start, end).trim()
        }

        return contextLines.joinToString("\n")
    }

    override fun findTargetElement(project: Project, args: FindReferencesArgs): PsiElement? {
        return measureOperation("findTargetElement") {
            // If position is provided, find element at that position
            if (args.filePath != null && args.position != null) {
                val psiFile = PsiUtils.getPsiFile(project, args.filePath) ?: return@measureOperation null
                val element = psiFile.findElementAt(args.position) ?: return@measureOperation null

                // **IMPROVEMENT**: Better handling of qualified references
                val parent = element.parent
                if (parent is PsiReferenceExpression) {
                    // Check if this is part of a qualified reference (e.g., Class.FIELD)
                    val grandParent = parent.parent
                    if (grandParent is PsiReferenceExpression) {
                        // This might be the CLASS part of CLASS.FIELD
                        val resolved = grandParent.resolve()
                        if (resolved != null) {
                            return@measureOperation resolved
                        }
                    }
                    
                    // Try to resolve the reference
                    val resolved = parent.resolve()
                    if (resolved != null) {
                        return@measureOperation resolved
                    }
                }

                // Try direct reference
                val reference = element.reference
                if (reference != null) {
                    return@measureOperation reference.resolve()
                }

                // Try to find a named element at this position
                return@measureOperation PsiTreeUtil.getParentOfType(element, PsiNamedElement::class.java)
            }

            // Otherwise, search by name
            if (args.symbolName == null) return@measureOperation null

            val scope = GlobalSearchScope.projectScope(project)
            val cache = com.intellij.psi.search.PsiShortNamesCache.getInstance(project)

            // **IMPROVEMENT**: Handle qualified names (e.g., "UserService.DEFAULT_ROLE")
            val parts = args.symbolName.split(".")
            if (parts.size == 2) {
                val className = parts[0]
                val memberName = parts[1]
                
                JavaPsiFacade.getInstance(project).findClass(className, scope)?.let { psiClass ->
                    // Try fields
                    psiClass.findFieldByName(memberName, true)?.let { return@measureOperation it }
                    // Try methods
                    psiClass.findMethodsByName(memberName, true).firstOrNull()?.let { return@measureOperation it }
                }
            }

            // Try to find as a class first
            JavaPsiFacade.getInstance(project).findClass(args.symbolName, scope)?.let {
                return@measureOperation it
            }

            // Try methods
            cache.getMethodsByName(args.symbolName, scope).firstOrNull()?.let {
                return@measureOperation it
            }

            // Try fields
            cache.getFieldsByName(args.symbolName, scope).firstOrNull()?.let {
                return@measureOperation it
            }

            return@measureOperation null
        }
    }

    // Include all the other helper methods from the original implementation...
    
    private fun findConstructorCalls(constructor: PsiMethod, scope: GlobalSearchScope): List<PsiNewExpression> {
        val containingClass = constructor.containingClass ?: return emptyList()
        val result = mutableListOf<PsiNewExpression>()

        try {
            ReferencesSearch.search(containingClass, scope).forEach { ref ->
                val element = ref.element
                val parent = element.parent
                if (parent is PsiNewExpression) {
                    result.add(parent)
                }
            }
        } catch (e: Exception) {
            logger.warn("Error finding constructor calls", e)
        }

        return result
    }

    private fun findOverrides(method: PsiMethod, scope: GlobalSearchScope): List<PsiMethod> {
        return try {
            com.intellij.psi.search.searches.OverridingMethodsSearch.search(method, scope, true)
                .findAll()
                .take(20)
                .toList()
        } catch (e: Exception) {
            logger.warn("Error finding overrides", e)
            emptyList()
        }
    }

    private fun findImplementations(method: PsiMethod, scope: GlobalSearchScope): List<PsiMethod> {
        val result = mutableListOf<PsiMethod>()
        val containingClass = method.containingClass ?: return result

        try {
            com.intellij.psi.search.searches.ClassInheritorsSearch.search(containingClass, scope, true)
                .findAll()
                .take(20)
                .forEach { implementor ->
                    val implementation = implementor.findMethodBySignature(method, false)
                    if (implementation != null && implementation != method) {
                        result.add(implementation)
                    }
                }
        } catch (e: Exception) {
            logger.warn("Error finding implementations", e)
        }

        return result
    }

    private fun findSubclasses(psiClass: PsiClass, scope: GlobalSearchScope): List<PsiClass> {
        return try {
            com.intellij.psi.search.searches.ClassInheritorsSearch.search(psiClass, scope, true)
                .findAll()
                .take(20)
                .toList()
        } catch (e: Exception) {
            logger.warn("Error finding subclasses", e)
            emptyList()
        }
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
               method?.hasAnnotation("kotlin.test.Test") == true ||
               method?.name?.startsWith("test") == true
    }

    private fun isInMethodCall(element: PsiElement): Boolean {
        return PsiTreeUtil.getParentOfType(element, PsiExpressionList::class.java) != null
    }

    private fun isInJavaDoc(element: PsiElement): Boolean {
        return PsiTreeUtil.getParentOfType(element, com.intellij.psi.javadoc.PsiDocComment::class.java) != null
    }

    private fun isOverride(method: PsiMethod, baseMethod: PsiMethod): Boolean {
        val methodClass = method.containingClass ?: return false
        val baseClass = baseMethod.containingClass ?: return false

        return methodClass != baseClass &&
               methodClass.isInheritor(baseClass, true) &&
               method.findSuperMethods().contains(baseMethod)
    }

    private fun isKotlinExtensionFunction(method: PsiMethod?): Boolean {
        return method?.text?.contains("extension") == true
    }

    private fun isKotlinSuspendFunction(method: PsiMethod?): Boolean {
        return method?.text?.contains("suspend") == true
    }

    private fun isKotlinPropertyAccess(element: PsiElement): Boolean {
        return element.language.id.lowercase() == "kotlin" &&
               element.text?.let { !it.contains("()") } == true
    }

    private fun isKotlinLambdaUsage(element: PsiElement): Boolean {
        return element.language.id.lowercase() == "kotlin" &&
               element.text?.contains("->") == true
    }

    private fun isKotlinDelegateUsage(element: PsiElement): Boolean {
        return element.language.id.lowercase() == "kotlin" &&
               element.text?.contains("by ") == true
    }

    override fun supportsElement(element: PsiElement): Boolean {
        return dev.mcp.extensions.lsp.core.utils.LanguageUtils.isJvmLanguage(element.language)
    }

    override fun supportsFile(psiFile: PsiFile): Boolean {
        return dev.mcp.extensions.lsp.core.utils.LanguageUtils.isJvmLanguage(psiFile.language)
    }

    override fun getSupportedLanguage(): String {
        return "Java/Kotlin"
    }
}
