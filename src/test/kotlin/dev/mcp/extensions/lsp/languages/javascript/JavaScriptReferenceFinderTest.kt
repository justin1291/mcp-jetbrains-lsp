package dev.mcp.extensions.lsp.languages.javascript

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import dev.mcp.extensions.lsp.core.models.FindReferencesArgs
import dev.mcp.extensions.lsp.core.models.ReferenceInfo
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JavaScriptReferenceFinderTest {

    private lateinit var myFixture: CodeInsightTestFixture
    private val finder = JavaScriptReferenceFinder()

    @Test
    fun testCreateGroupedResultMapping() {
        // Test that createGroupedResult correctly processes and groups references
        val mockTarget = createMockElement("calculateTotal")
        val mockReferences = listOf(
            ReferenceInfo(
                filePath = "test.js",
                startOffset = 100,
                endOffset = 115,
                lineNumber = 5,
                usageType = "method_call",
                elementText = "calculateTotal",
                preview = "const total = calculateTotal(items);",
                containingMethod = "processOrder",
                containingClass = null,
                isInTestCode = false,
                isInComment = false,
                accessModifier = null,
                surroundingContext = "const total = calculateTotal(items);",
                dataFlowContext = "assigned to total",
                isInDeprecatedCode = false
            ),
            ReferenceInfo(
                filePath = "test.js",
                startOffset = 200,
                endOffset = 215,
                lineNumber = 10,
                usageType = "method_call",
                elementText = "calculateTotal",
                preview = "return calculateTotal(data);",
                containingMethod = "handler",
                containingClass = null,
                isInTestCode = false,
                isInComment = false,
                accessModifier = null,
                surroundingContext = "return calculateTotal(data);",
                dataFlowContext = "returned from function",
                isInDeprecatedCode = false
            ),
            ReferenceInfo(
                filePath = "test.spec.js",
                startOffset = 300,
                endOffset = 315,
                lineNumber = 15,
                usageType = "method_call",
                elementText = "calculateTotal",
                preview = "expect(calculateTotal([])).toBe(0);",
                containingMethod = "testCalculateTotal",
                containingClass = null,
                isInTestCode = true,
                isInComment = false,
                accessModifier = null,
                surroundingContext = "expect(calculateTotal([])).toBe(0);",
                dataFlowContext = "passed as argument to expect",
                isInDeprecatedCode = false
            )
        )

        val groupedResult = finder.createGroupedResult(mockReferences, mockTarget)

        // Verify summary is correctly calculated
        val summary = groupedResult.summary
        assertEquals(3, summary.totalReferences)
        assertEquals(2, summary.fileCount) // test.js and test.spec.js
        assertEquals(true, summary.hasTestUsages)
        assertEquals("test.js", summary.primaryUsageLocation) // More usages in test.js
        assertEquals(0, summary.deprecatedUsageCount)

        // Verify grouping by type
        val usagesByType = groupedResult.usagesByType
        assertEquals(1, usagesByType.size)
        assertTrue(usagesByType.containsKey("method_call"))
        assertEquals(3, usagesByType["method_call"]?.size)

        // Verify insights generation
        val insights = groupedResult.insights
        assertTrue(insights.isNotEmpty())
        assertTrue(insights.any { it.contains("Primary usage as method_call") })
        assertTrue(insights.any { it.contains("test file") })

        // Verify all references are preserved
        assertEquals(mockReferences.size, groupedResult.allReferences.size)
        assertEquals(mockReferences, groupedResult.allReferences)
    }

    @Test
    fun testCreateGroupedResultWithVariousUsageTypes() {
        // Test grouping with different usage types
        val mockTarget = createMockElement("userService")
        val mockReferences = listOf(
            createReferenceInfo("method_call", "processUser", false),
            createReferenceInfo("method_call", "validateUser", false),
            createReferenceInfo("constructor_call", "createService", false),
            createReferenceInfo("field_read", "getUserName", false),
            createReferenceInfo("field_write", "setUserName", false),
            createReferenceInfo("method_call", "testUserService", true) // test usage
        )

        val groupedResult = finder.createGroupedResult(mockReferences, mockTarget)

        // Verify grouping by type
        val usagesByType = groupedResult.usagesByType
        assertEquals(4, usagesByType.size) // method_call, constructor_call, field_read, field_write
        assertEquals(3, usagesByType["method_call"]?.size) // Including test usage
        assertEquals(1, usagesByType["constructor_call"]?.size)
        assertEquals(1, usagesByType["field_read"]?.size)
        assertEquals(1, usagesByType["field_write"]?.size)

        // Verify summary
        val summary = groupedResult.summary
        assertEquals(6, summary.totalReferences)
        assertEquals(true, summary.hasTestUsages)
        assertEquals("method_call", usagesByType.maxByOrNull { it.value.size }?.key)
    }

    @Test
    fun testInsightGeneration() {
        val singleUsageReferences = listOf(createReferenceInfo("method_call", "onlyUser", false))
        val mockTarget = createMockElement("testFunction")
        val result1 = finder.createGroupedResult(singleUsageReferences, mockTarget)


        val insights1 = result1.insights
        assertTrue(insights1.any { it.contains("Primary usage as method_call") })


        val heavyUsageReferences = (1..15).map { createReferenceInfo("method_call", "user$it", false) }
        val result2 = finder.createGroupedResult(heavyUsageReferences, mockTarget)
        val insights2 = result2.insights


        assertTrue(insights2.any { it.contains("Primary usage as method_call") })


        val mixedReferences = listOf(
            createReferenceInfo("method_call", "prodUser", false),
            createReferenceInfo("method_call", "testUser", true)
        )
        val result3 = finder.createGroupedResult(mixedReferences, mockTarget)
        val insights3 = result3.insights

        // Should have insights about test usage
        assertTrue(insights3.any { it.contains("test file") })
    }

    @Test
    fun testIncludeDeclarationLogic() {
        // Test that includeDeclaration parameter works correctly
        val argsWithoutDeclaration = FindReferencesArgs(
            symbolName = "testSymbol",
            includeDeclaration = false
        )
        assertEquals(false, argsWithoutDeclaration.includeDeclaration)

        val argsWithDeclaration = FindReferencesArgs(
            symbolName = "testSymbol",
            includeDeclaration = true
        )
        assertEquals(true, argsWithDeclaration.includeDeclaration)
    }

    @Test
    fun testSupportsElement() {
        val supportedLanguage = finder.getSupportedLanguage()
        assertEquals("JavaScript/TypeScript", supportedLanguage)

        // Test that finder reports correct supported language
        assertTrue(supportedLanguage.contains("JavaScript"))
        assertTrue(supportedLanguage.contains("TypeScript"))
    }

    @Test
    fun testDeprecatedUsageDetection() {
        // Test that deprecated usage is properly counted
        val mockTarget = createMockElement("deprecatedFunction")
        val mockReferences = listOf(
            createReferenceInfo("method_call", "normalUser", false, false),
            createReferenceInfo("method_call", "deprecatedUser", false, true),
            createReferenceInfo("method_call", "anotherDeprecatedUser", false, true)
        )

        val groupedResult = finder.createGroupedResult(mockReferences, mockTarget)

        // Verify deprecated usage count
        assertEquals(2, groupedResult.summary.deprecatedUsageCount)

        // Verify insights mention deprecated usage
        assertTrue(groupedResult.insights.any { it.contains("deprecated API") })
    }

    // Helper methods

    private fun createMockElement(name: String): PsiElement {
        return object : PsiElement {
            override fun getText(): String = name
            override fun getTextRange(): com.intellij.openapi.util.TextRange =
                com.intellij.openapi.util.TextRange(0, name.length)

            override fun getStartOffsetInParent(): Int = 0
            override fun getTextLength(): Int = name.length
            override fun findElementAt(offset: Int): PsiElement? = null
            override fun findReferenceAt(offset: Int): com.intellij.psi.PsiReference? = null
            override fun getTextOffset(): Int = 0
            override fun getManager(): com.intellij.psi.PsiManager = throw UnsupportedOperationException()
            override fun getChildren(): Array<PsiElement> = emptyArray()
            override fun getParent(): PsiElement? = null
            override fun getFirstChild(): PsiElement? = null
            override fun getLastChild(): PsiElement? = null
            override fun getNextSibling(): PsiElement? = null
            override fun getPrevSibling(): PsiElement? = null
            override fun getContainingFile(): PsiFile? = null
            override fun getTextRangeInParent(): com.intellij.openapi.util.TextRange = textRange
            override fun getNavigationElement(): PsiElement = this
            override fun getOriginalElement(): PsiElement = this
            override fun isValid(): Boolean = true
            override fun isWritable(): Boolean = false
            override fun getReference(): com.intellij.psi.PsiReference? = null
            override fun getReferences(): Array<com.intellij.psi.PsiReference> = emptyArray()
            override fun textToCharArray(): CharArray = text.toCharArray()
            override fun add(element: PsiElement): PsiElement = throw UnsupportedOperationException()
            override fun addBefore(element: PsiElement, anchor: PsiElement?): PsiElement =
                throw UnsupportedOperationException()

            override fun addAfter(element: PsiElement, anchor: PsiElement?): PsiElement =
                throw UnsupportedOperationException()

            override fun checkAdd(element: PsiElement) {}
            override fun addRange(first: PsiElement?, last: PsiElement?): PsiElement =
                throw UnsupportedOperationException()

            override fun addRangeBefore(first: PsiElement, last: PsiElement, anchor: PsiElement?): PsiElement =
                throw UnsupportedOperationException()

            override fun addRangeAfter(first: PsiElement?, last: PsiElement?, anchor: PsiElement?): PsiElement =
                throw UnsupportedOperationException()

            override fun delete() {}
            override fun checkDelete() {}
            override fun deleteChildRange(first: PsiElement?, last: PsiElement?) {}
            override fun replace(newElement: PsiElement): PsiElement = newElement
            override fun isPhysical(): Boolean = false
            override fun accept(visitor: com.intellij.psi.PsiElementVisitor) {}
            override fun acceptChildren(visitor: com.intellij.psi.PsiElementVisitor) {}
            override fun getNode(): com.intellij.lang.ASTNode? = null
            override fun isEquivalentTo(another: PsiElement?): Boolean = this == another
            override fun getIcon(flags: Int): javax.swing.Icon? = null
            override fun getLanguage(): com.intellij.lang.Language =
                com.intellij.lang.Language.findLanguageByID("JavaScript") ?: com.intellij.lang.Language.ANY

            override fun getProject(): Project = myFixture.project
            override fun <T : Any?> getUserData(key: com.intellij.openapi.util.Key<T>): T? = null
            override fun <T : Any?> putUserData(key: com.intellij.openapi.util.Key<T>, value: T?) {}
            override fun copy(): PsiElement = this
            override fun <T : Any?> getCopyableUserData(key: com.intellij.openapi.util.Key<T>): T? = null
            override fun <T : Any?> putCopyableUserData(key: com.intellij.openapi.util.Key<T>, value: T?) {}
            override fun getContext(): PsiElement? = parent
            override fun textMatches(text: CharSequence): Boolean = this.text == text.toString()
            override fun textMatches(element: PsiElement): Boolean = this.text == element.text
            override fun textContains(c: Char): Boolean = text.contains(c)
            override fun getResolveScope(): com.intellij.psi.search.GlobalSearchScope =
                throw UnsupportedOperationException()

            override fun getUseScope(): com.intellij.psi.search.SearchScope = throw UnsupportedOperationException()
            override fun processDeclarations(
                processor: com.intellij.psi.scope.PsiScopeProcessor,
                state: com.intellij.psi.ResolveState,
                lastParent: PsiElement?,
                place: PsiElement
            ): Boolean = true
        }
    }

    private fun createReferenceInfo(
        usageType: String,
        containingMethod: String,
        isInTestCode: Boolean,
        isInDeprecatedCode: Boolean = false
    ): ReferenceInfo {
        return ReferenceInfo(
            filePath = if (isInTestCode) "test.spec.js" else "test.js",
            startOffset = 100,
            endOffset = 115,
            lineNumber = 5,
            usageType = usageType,
            elementText = "testElement",
            preview = "mock preview",
            containingMethod = containingMethod,
            containingClass = null,
            isInTestCode = isInTestCode,
            isInComment = false,
            accessModifier = null,
            surroundingContext = "mock context",
            dataFlowContext = null,
            isInDeprecatedCode = isInDeprecatedCode
        )
    }
}
