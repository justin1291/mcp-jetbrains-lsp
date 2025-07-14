package dev.mcp.extensions.lsp.languages.python

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import dev.mcp.extensions.lsp.core.interfaces.ReferenceFinder
import dev.mcp.extensions.lsp.core.models.FindReferencesArgs
import dev.mcp.extensions.lsp.core.models.GroupedReferencesResult
import dev.mcp.extensions.lsp.core.models.ReferenceInfo
import dev.mcp.extensions.lsp.core.models.ReferenceSummary
import dev.mcp.extensions.lsp.core.utils.PsiUtils


/**
 * Reference finder implementation for Python language.
 * 
 * This service finds all references/usages of Python symbols including:
 * - Functions and methods
 * - Classes and class methods
 * - Variables and attributes
 * - Imports and modules
 * - Properties and decorators
 * 
 * Provides intelligent grouping and insights about usage patterns.
 * 
 * Registered as a service in mcp-lsp-python.xml when Python module is available.
 * 
 * Note: Uses some experimental Python PSI APIs which may change in future IntelliJ versions.
 * The @ApiStatus.Experimental warnings are expected and acceptable for this implementation.
 */
class PythonReferenceFinder : PythonBaseHandler(), ReferenceFinder {
    
    override fun findReferences(project: Project, element: PsiElement, args: FindReferencesArgs): List<ReferenceInfo> {
        logger.info("Finding references for Python element: ${getElementName(element)}")
        
        val references = mutableListOf<ReferenceInfo>()
        val scope = GlobalSearchScope.projectScope(project)
        
        // Find all references to the element
        val query = ReferencesSearch.search(element, scope)
        
        query.forEach { reference ->
            val refElement = reference.element
            val location = createReferenceInfo(refElement, element)
            references.add(location)
        }
        
        // Special handling for Python-specific cases
        when (element) {
            is PyFunction -> {
                // Find method overrides in subclasses
                findMethodOverrides(element, scope).forEach { override ->
                    references.add(createReferenceInfo(override, element, "method_override"))
                }
                
                // If it's a property, find direct attribute access
                if (isProperty(element)) {
                    findPropertyUsages(element, scope).forEach { usage ->
                        references.add(createReferenceInfo(usage, element, "property_access"))
                    }
                }
            }
            
            is PyClass -> {
                // Find subclass instantiations
                findClassInstantiations(element, scope).forEach { instantiation ->
                    references.add(createReferenceInfo(instantiation, element, "class_instantiation"))
                }
                
                // Find inheritance usage
                findInheritanceUsages(element, scope).forEach { inheritance ->
                    references.add(createReferenceInfo(inheritance, element, "inheritance"))
                }
            }
            
            is PyTargetExpression -> {
                // For variables, distinguish between reads and writes
                findVariableUsages(element, scope).forEach { usage ->
                    references.add(createReferenceInfo(usage.first, element, usage.second))
                }
            }
        }
        
        // Optionally include the declaration itself
        if (args.includeDeclaration) {
            references.add(0, createReferenceInfo(element, element, "declaration"))
        }
        
        logger.debug("Found ${references.size} references for Python element")
        return references
    }
    
    override fun findTargetElement(project: Project, args: FindReferencesArgs): PsiElement? {
        // If position is provided, find element at that position
        if (args.filePath != null && args.position != null) {
            val psiFile = PsiUtils.getPsiFile(project, args.filePath) as? PyFile ?: return null
            
            val element = psiFile.findElementAt(args.position) ?: return null
            
            // Try to resolve reference at this position
            val reference = element.parent?.reference ?: element.reference
            if (reference != null) {
                return reference.resolve()
            }
            
            // Try to find a named element at this position
            return PsiTreeUtil.getParentOfType(element, 
                PyTargetExpression::class.java,
                PyFunction::class.java,
                PyClass::class.java,
                PyParameter::class.java
            )
        }
        
        // Otherwise, search by name
        if (args.symbolName == null) return null
        
        return findElementByNameSearch(project, args.symbolName)
    }
    
    override fun createGroupedResult(references: List<ReferenceInfo>, element: PsiElement): GroupedReferencesResult {
        // Group references by usage type
        val usagesByType = references.groupBy { it.usageType }
        
        // Calculate summary statistics
        val fileCount = references.map { it.filePath }.distinct().size
        val hasTestUsages = references.any { it.isInTestCode }
        val deprecatedUsageCount = countDeprecatedUsages(references)
        
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
        
        // Wrap document access in read action to ensure thread safety
        val (document, lineNumber) = com.intellij.openapi.application.ReadAction.compute<Pair<com.intellij.openapi.editor.Document?, Int>, Exception> {
            val doc = PsiDocumentManager.getInstance(project).getDocument(containingFile)
            val line = doc?.getLineNumber(textRange.startOffset) ?: 0
            Pair(doc, line)
        }
        
        // Get the line containing the reference for preview
        val lineText = com.intellij.openapi.application.ReadAction.compute<String, Exception> {
            val lineStartOffset = document?.getLineStartOffset(lineNumber) ?: 0
            val lineEndOffset = document?.getLineEndOffset(lineNumber) ?: textRange.endOffset
            document?.text?.substring(lineStartOffset, lineEndOffset)?.trim() ?: element.text
        }
        
        // Find containing function and class
        val containingFunction = PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
        val containingClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java)
        
        // Check if in test code
        val isInTest = isInTestCode(virtualFile) || isInTestFunction(element)
        
        // Check if in comment or docstring
        val isInComment = isInPythonComment(element)
        
        // Check if in deprecated code
        val isInDeprecatedCode = isInDeprecatedContext(element)
        
        // Get access modifier based on Python naming conventions
        val accessModifier = getPythonAccessModifier(element, target)
        
        // Get surrounding context (2 lines before and after)
        val surroundingContext = com.intellij.openapi.application.ReadAction.compute<String?, Exception> {
            getSurroundingContext(document, lineNumber)
        }
        
        // Get data flow context
        val dataFlowContext = getPythonDataFlowContext(element, target)
        
        return ReferenceInfo(
            filePath = relativePath,
            startOffset = textRange.startOffset,
            endOffset = textRange.endOffset,
            lineNumber = lineNumber + 1, // Convert to 1-based
            usageType = overrideType ?: determinePythonUsageType(element, target),
            elementText = element.text?.trim(),
            preview = lineText,
            containingMethod = containingFunction?.name,
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
        return languageId == "Python" || element is PyElement
    }
    
    override fun getSupportedLanguage(): String {
        return "Python"
    }
    
    // Private helper methods
    
    private fun getElementName(element: PsiElement): String {
        return when (element) {
            is PyFunction -> element.name ?: element.text
            is PyClass -> element.name ?: element.text
            is PyTargetExpression -> element.name ?: element.text
            is PsiNamedElement -> element.name ?: element.text
            else -> element.text
        }
    }
    
    private fun findMethodOverrides(method: PyFunction, scope: GlobalSearchScope): List<PyFunction> {
        val result = mutableListOf<PyFunction>()
        val containingClass = method.containingClass ?: return result
        
        // Find all Python files and look for overrides
        val project = method.project
        val fileIndex = ProjectFileIndex.getInstance(project)
        val psiManager = PsiManager.getInstance(project)
        
        fileIndex.iterateContent { virtualFile ->
            if (virtualFile.extension == "py" && scope.contains(virtualFile)) {
                val psiFile = psiManager.findFile(virtualFile) as? PyFile
                psiFile?.let { pyFile ->
                    pyFile.topLevelClasses.forEach { pyClass ->
                        if (isSubclassOf(pyClass, containingClass)) {
                            pyClass.findMethodByName(method.name, false, null)?.let { overrideMethod ->
                                if (overrideMethod != method) {
                                    result.add(overrideMethod)
                                }
                            }
                        }
                    }
                }
            }
            true
        }
        
        return result
    }
    
    private fun findPropertyUsages(property: PyFunction, scope: GlobalSearchScope): List<PsiElement> {
        val result = mutableListOf<PsiElement>()
        val containingClass = property.containingClass ?: return result
        val propertyName = property.name ?: return result
        
        // Find direct attribute access to this property
        ReferencesSearch.search(containingClass, scope).forEach { ref ->
            val element = ref.element
            val parent = element.parent
            if (parent is PyReferenceExpression && parent.referencedName == propertyName) {
                result.add(parent)
            }
        }
        
        return result
    }
    
    private fun findClassInstantiations(pyClass: PyClass, scope: GlobalSearchScope): List<PsiElement> {
        val result = mutableListOf<PsiElement>()
        
        ReferencesSearch.search(pyClass, scope).forEach { ref ->
            val element = ref.element
            val parent = element.parent
            if (parent is PyCallExpression && parent.callee == element) {
                result.add(parent)
            }
        }
        
        return result
    }
    
    private fun findInheritanceUsages(pyClass: PyClass, scope: GlobalSearchScope): List<PsiElement> {
        val result = mutableListOf<PsiElement>()
        
        ReferencesSearch.search(pyClass, scope).forEach { ref ->
            val element = ref.element
            val parent = element.parent
            if (parent is PyArgumentList) {
                val grandParent = parent.parent
                if (grandParent is PyClass) {
                    result.add(element)
                }
            }
        }
        
        return result
    }
    
    private fun findVariableUsages(variable: PyTargetExpression, scope: GlobalSearchScope): List<Pair<PsiElement, String>> {
        val result = mutableListOf<Pair<PsiElement, String>>()
        
        ReferencesSearch.search(variable, scope).forEach { ref ->
            val element = ref.element
            val usageType = when {
                isInAssignment(element) -> "variable_write"
                isInAugmentedAssignment(element) -> "variable_increment"
                isInDeleteStatement(element) -> "variable_delete"
                else -> "variable_read"
            }
            result.add(Pair(element, usageType))
        }
        
        return result
    }
    
    private fun findElementByNameSearch(project: Project, symbolName: String): PsiElement? {
        val scope = GlobalSearchScope.projectScope(project)
        val fileIndex = ProjectFileIndex.getInstance(project)
        val psiManager = PsiManager.getInstance(project)
        
        var foundElement: PsiElement? = null
        
        logger.debug("Starting manual search for Python symbol '$symbolName'")
        
        fileIndex.iterateContent { virtualFile ->
            if (foundElement != null) return@iterateContent false
            
            if (virtualFile.extension == "py" && scope.contains(virtualFile)) {
                val psiFile = psiManager.findFile(virtualFile) as? PyFile
                psiFile?.let { pyFile ->
                    // Search for classes
                    pyFile.topLevelClasses.find { it.name == symbolName }?.let {
                        foundElement = it
                        return@iterateContent false
                    }
                    
                    // Search for functions
                    pyFile.topLevelFunctions.find { it.name == symbolName }?.let {
                        foundElement = it
                        return@iterateContent false
                    }
                    
                    // Search for variables
                    pyFile.topLevelAttributes.find { it.name == symbolName }?.let {
                        foundElement = it
                        return@iterateContent false
                    }
                    
                    // Search within classes
                    pyFile.topLevelClasses.forEach { pyClass ->
                        pyClass.methods.find { it.name == symbolName }?.let {
                            foundElement = it
                            return@iterateContent false
                        }
                        
                        pyClass.classAttributes.find { it.name == symbolName }?.let {
                            foundElement = it
                            return@iterateContent false
                        }
                    }
                }
            }
            true
        }
        
        logger.debug("Manual search complete. Found: $foundElement")
        return foundElement
    }
    
    private fun findPrimaryUsageLocation(references: List<ReferenceInfo>): String? {
        if (references.isEmpty()) return null
        
        return references
            .filter { it.containingClass != null && !it.isInTestCode }
            .groupBy { it.containingClass }
            .maxByOrNull { it.value.size }
            ?.key
    }
    
    private fun countDeprecatedUsages(references: List<ReferenceInfo>): Int {
        return references.count { it.isInDeprecatedCode }
    }
    
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
        
        // Python-specific insights
        generatePythonSpecificInsights(references, element, insights)
        
        return insights
    }
    
    private fun generatePythonSpecificInsights(references: List<ReferenceInfo>, element: PsiElement, insights: MutableList<String>) {
        when (element) {
            is PyFunction -> {
                // Method-specific insights
                val overrideCount = references.count { it.usageType == "method_override" }
                if (overrideCount > 0) {
                    insights.add("Method is overridden $overrideCount ${if (overrideCount == 1) "time" else "times"} - changes will affect subclasses")
                }
                
                // Property insights
                if (isProperty(element)) {
                    val propertyAccess = references.count { it.usageType == "property_access" }
                    val methodCalls = references.count { it.usageType == "method_call" }
                    if (propertyAccess > methodCalls) {
                        insights.add("Property is accessed directly more than called as method - good encapsulation")
                    }
                }
                
                // Async function insights
                if (isAsync(element)) {
                    val awaitCount = references.count { it.dataFlowContext?.contains("await") == true }
                    if (awaitCount < references.size / 2) {
                        insights.add("Async function may be called without await in some places - check for missing await keywords")
                    }
                }
            }
            
            is PyClass -> {
                // Class instantiation insights
                val instantiations = references.count { it.usageType == "class_instantiation" }
                val inheritances = references.count { it.usageType == "inheritance" }
                
                if (inheritances > instantiations && instantiations > 0) {
                    insights.add("Class is inherited more than instantiated - consider if it should be abstract")
                }
                
                // Dataclass insights
                if (isDataclass(element)) {
                    val directAccess = references.count { it.usageType == "attribute_access" }
                    if (directAccess > 0) {
                        insights.add("Dataclass fields accessed directly $directAccess ${if (directAccess == 1) "time" else "times"} - this is expected for dataclasses")
                    }
                }
            }
            
            is PyTargetExpression -> {
                // Variable insights
                val writes = references.count { it.usageType == "variable_write" }
                val reads = references.count { it.usageType == "variable_read" }
                
                if (writes == 0 && reads > 0) {
                    insights.add("Variable is never reassigned after initialization - consider making it a constant")
                } else if (writes > reads && reads > 0) {
                    insights.add("Variable is written more often than read ($writes writes, $reads reads)")
                }
                
                // Check if variable follows Python naming conventions
                val name = element.name
                if (name != null) {
                    val visibility = getPythonVisibility(name)
                    if (visibility == "private" && references.any { !it.filePath.endsWith(element.containingFile.name) }) {
                        insights.add("Private variable (double underscore) accessed from other files - consider reducing visibility")
                    }
                }
            }
        }
        
        // Docstring/comment references
        val commentRefs = references.count { it.isInComment }
        if (commentRefs > 0) {
            insights.add("$commentRefs ${if (commentRefs == 1) "reference" else "references"} in comments/docstrings - update documentation if changing")
        }
    }
    
    private fun isSubclassOf(candidate: PyClass, baseClass: PyClass): Boolean {
        return candidate.isSubclass(baseClass, null)
    }
    
    private fun isInTestFunction(element: PsiElement): Boolean {
        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
        return function?.name?.startsWith("test_") == true ||
               extractDecorators(function ?: return false).any { 
                   it.contains("pytest", ignoreCase = true) || 
                   it.contains("unittest", ignoreCase = true) 
               }
    }
    
    private fun isInPythonComment(element: PsiElement): Boolean {
        // Check if in string literal that serves as docstring
        val stringLiteral = PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression::class.java)
        if (stringLiteral != null) {
            val parent = stringLiteral.parent
            // Check if it's a docstring (first statement in function/class/module)
            if (parent is PyExpressionStatement) {
                val grandParent = parent.parent
                if (grandParent is PyStatementList) {
                    val statements = grandParent.statements
                    if (statements.isNotEmpty() && statements[0] == parent) {
                        return true // This is a docstring
                    }
                }
            }
        }
        
        // Check if in actual comment
        return PsiTreeUtil.getParentOfType(element, PsiComment::class.java) != null
    }
    
    private fun isInDeprecatedContext(element: PsiElement): Boolean {
        val containingFunction = PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
        val containingClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java)
        
        return extractDecorators(containingFunction ?: return false).any { it.contains("deprecated", ignoreCase = true) } ||
               extractDecorators(containingClass ?: return false).any { it.contains("deprecated", ignoreCase = true) }
    }
    
    private fun getPythonAccessModifier(element: PsiElement, @Suppress("UNUSED_PARAMETER") target: PsiElement): String? {
        val parent = element.parent
        return when {
            parent is PyReferenceExpression -> {
                val qualifier = parent.qualifier
                when {
                    qualifier == null -> "implicit"
                    qualifier.text == "self" -> "self"
                    qualifier.text == "super()" -> "super"
                    qualifier.text == "cls" -> "class"
                    else -> "qualified"
                }
            }
            else -> null
        }
    }
    
    private fun getSurroundingContext(document: Document?, lineNumber: Int): String? {
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
    
    private fun getPythonDataFlowContext(element: PsiElement, @Suppress("UNUSED_PARAMETER") target: PsiElement): String? {
        val parent = element.parent
        
        return when {
            // Assignment contexts
            parent is PyAssignmentStatement && parent.leftHandSideExpression == element -> "assigned to"
            parent is PyAssignmentStatement && parent.assignedValue == element -> "assigned from"
            parent is PyAugAssignmentStatement -> "augmented assignment"
            
            // Function contexts
            parent is PyReturnStatement -> "returned from function"
            parent is PyArgumentList -> "passed as argument"
            parent is PyYieldExpression -> "yielded from generator"
            
            // Control flow contexts
            isInPythonCondition(element) -> "used in condition"
            parent is PyIfStatement -> "condition check"
            parent is PyWhileStatement -> "loop condition"
            parent is PyForStatement -> "loop target"
            
            // Exception handling
            parent is PyRaiseStatement -> "raised as exception"
            parent is PyExceptPart -> "exception handler"
            
            // Comprehensions
            parent is PyListCompExpression -> "list comprehension"
            parent is PyDictCompExpression -> "dict comprehension"
            parent is PySetCompExpression -> "set comprehension"
            parent is PyGeneratorExpression -> "generator expression"
            
            // Function decorators
            parent is PyDecorator -> "decorator"
            
            // Import contexts
            parent is PyImportStatement -> "import statement"
            parent is PyFromImportStatement -> "from import statement"
            
            else -> null
        }
    }
    
    private fun determinePythonUsageType(element: PsiElement, target: PsiElement): String {
        val parent = element.parent
        
        return when {
            element == target -> "declaration"
            
            // Function/method calls
            parent is PyCallExpression && parent.callee == element -> {
                when {
                    target is PyFunction && isProperty(target) -> "property_call"
                    target is PyFunction && isAsync(target) -> "async_call"
                    target is PyFunction && getMethodType(target) == "static" -> "static_method_call"
                    target is PyFunction && getMethodType(target) == "class" -> "class_method_call"
                    isInTestFunction(element) -> "test_method_call"
                    else -> "method_call"
                }
            }
            
            // Class instantiation
            parent is PyCallExpression && target is PyClass -> "class_instantiation"
            
            // Attribute access
            parent is PyReferenceExpression -> {
                val grandParent = parent.parent
                when {
                    grandParent is PyAssignmentStatement && grandParent.leftHandSideExpression == parent -> "attribute_write"
                    grandParent is PyAugAssignmentStatement -> "attribute_increment"
                    grandParent is PyDelStatement -> "attribute_delete"
                    isInPythonCondition(parent) -> "attribute_condition_check"
                    grandParent is PyArgumentList -> "attribute_as_argument"
                    else -> "attribute_access"
                }
            }
            
            // Import statements
            parent is PyImportStatement -> "import"
            parent is PyFromImportStatement -> "from_import"
            
            // Inheritance
            parent is PyArgumentList && parent.parent is PyClass -> "inheritance"
            
            // Type annotations
            parent is PyAnnotation -> "type_annotation"
            
            // Exception handling
            parent is PyExceptPart -> "exception_type"
            parent is PyRaiseStatement -> "exception_raise"
            
            // Decorators
            parent is PyDecorator -> "decorator"
            
            // Variable assignments
            isInAssignment(element) -> "variable_write"
            isInAugmentedAssignment(element) -> "variable_increment"
            isInDeleteStatement(element) -> "variable_delete"
            
            // Docstring references
            isInPythonComment(element) -> "docstring_reference"
            
            else -> "reference"
        }
    }
    
    private fun isInAssignment(element: PsiElement): Boolean {
        val assignment = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement::class.java)
        return assignment?.leftHandSideExpression == element.parent
    }
    
    private fun isInAugmentedAssignment(element: PsiElement): Boolean {
        return PsiTreeUtil.getParentOfType(element, PyAugAssignmentStatement::class.java) != null
    }
    
    private fun isInDeleteStatement(element: PsiElement): Boolean {
        return PsiTreeUtil.getParentOfType(element, PyDelStatement::class.java) != null
    }
    
    private fun isInPythonCondition(element: PsiElement): Boolean {
        return PsiTreeUtil.getParentOfType(element,
            PyIfStatement::class.java,
            PyWhileStatement::class.java,
            PyConditionalExpression::class.java
        ) != null
    }
}
