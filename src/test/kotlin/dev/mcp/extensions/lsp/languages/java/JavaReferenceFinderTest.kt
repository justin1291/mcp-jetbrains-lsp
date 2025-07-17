package dev.mcp.extensions.lsp.languages.java

import com.intellij.openapi.application.ApplicationManager
import dev.mcp.extensions.lsp.JavaBaseTest
import dev.mcp.extensions.lsp.core.models.FindReferencesArgs
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class JavaReferenceFinderTest : JavaBaseTest() {

    private val finder: JavaReferenceFinder = JavaReferenceFinder()

    @Test
    fun testFindFieldReferences() {
        ApplicationManager.getApplication().runReadAction {
            val args = FindReferencesArgs(
                symbolName = "users",
                filePath = null,
                position = null,
                includeDeclaration = false
            )

            val element = finder.findTargetElement(fixtureProject, args)!!
            val references = finder.findReferences(fixtureProject, element, args)

            // Verify references are found in UserService methods
            val containingMethods = references.map { it.containingMethod }.toSet()
            assertTrue(
                containingMethods.contains("addUser") ||
                        containingMethods.contains("findUser") ||
                        containingMethods.contains("removeUser"),
                "Should find references in UserService methods"
            )

            // Test grouped result generation
            val result = finder.createGroupedResult(references, element)
            assertNotNull(result, "Should create grouped result")
            assertTrue(result.insights.isNotEmpty(), "Should generate insights")
        }
    }

    @Test
    fun testFindMethodReferences() {
        ApplicationManager.getApplication().runReadAction {
            val args = FindReferencesArgs(
                symbolName = "addUser",
                filePath = null,
                position = null,
                includeDeclaration = false
            )

            val element = finder.findTargetElement(fixtureProject, args)!!
            val references = finder.findReferences(fixtureProject, element, args)

            // Should find method call references
            assertTrue(
                references.isNotEmpty(),
                "Should find addUser method references"
            )

            // Verify data flow context is populated
            val hasDataFlowContext = references.any { it.dataFlowContext != null }
            assertTrue(hasDataFlowContext, "Should have data flow context for some references")

            // Test grouped result
            val result = finder.createGroupedResult(references, element)
            assertNotNull(result.usagesByType, "Should have usagesByType grouping")
        }
    }

    @Test
    fun testFindConstantReferences() {
        ApplicationManager.getApplication().runReadAction {
            val args = FindReferencesArgs(
                symbolName = "DEFAULT_ROLE",
                filePath = null,
                position = null,
                includeDeclaration = false
            )

            val element = finder.findTargetElement(fixtureProject, args)!!
            val references = finder.findReferences(fixtureProject, element, args)

            // Should find references to DEFAULT_ROLE constant
            assertTrue(
                references.any { it.usageType == "reference" },
                "Should find references to DEFAULT_ROLE"
            )
        }
    }

    @Test
    fun testFindConstructorReferences() {
        ApplicationManager.getApplication().runReadAction {
            val args = FindReferencesArgs(
                symbolName = "User",
                filePath = null,
                position = null,
                includeDeclaration = false
            )

            val element = finder.findTargetElement(fixtureProject, args)!!
            val references = finder.findReferences(fixtureProject, element, args)

            // Test grouped result
            val result = finder.createGroupedResult(references, element)
            assertTrue(
                references.isNotEmpty(),
                "Should find User references"
            )
        }
    }

    @Test
    fun testIncludeDeclaration() {
        ApplicationManager.getApplication().runReadAction {
            val args = FindReferencesArgs(
                symbolName = "userService",
                filePath = null,
                position = null,
                includeDeclaration = true
            )

            val element = finder.findTargetElement(fixtureProject, args)!!
            val references = finder.findReferences(fixtureProject, element, args)

            // Should include declaration when requested
            val declarationRef = references.find { it.usageType == "declaration" }
            assertNotNull(declarationRef, "Should have declaration reference")
            assertEquals("declaration", "declaration", declarationRef.usageType)
        }
    }

    @Test
    fun testFindApiControllerReferences() {
        ApplicationManager.getApplication().runReadAction {
            val args = FindReferencesArgs(
                symbolName = "userService",
                filePath = null,
                position = null,
                includeDeclaration = false
            )

            val element = finder.findTargetElement(fixtureProject, args)!!
            val references = finder.findReferences(fixtureProject, element, args)

            // Should find userService field references in ApiController
            assertTrue(
                references.any { it.containingClass?.contains("ApiController") ?: false },
                "Should find userService references in ApiController"
            )
        }
    }

    @Test
    fun testGroupedResults() {
        ApplicationManager.getApplication().runReadAction {
            val args = FindReferencesArgs(
                symbolName = "User",
                filePath = null,
                position = null,
                includeDeclaration = false
            )

            val element = finder.findTargetElement(fixtureProject, args)!!
            val references = finder.findReferences(fixtureProject, element, args)

            val result = finder.createGroupedResult(references, element)

            // Verify grouped structure
            assertNotNull(result.summary, "Should have summary")
            assertNotNull(result.usagesByType, "Should have usagesByType")
            assertNotNull(result.insights, "Should have insights")
            assertNotNull(result.allReferences, "Should have allReferences")

            // Verify summary statistics
            assertEquals(
                references.size, result.summary.totalReferences,
                "Summary total should match reference count"
            )
            assertTrue(result.summary.fileCount > 0, "Should have file count")
        }
    }

    @Test
    fun testTestCodeInsight() {
        ApplicationManager.getApplication().runReadAction {
            // Look for a method that might not have test coverage
            val args = FindReferencesArgs(
                symbolName = "processData",
                filePath = null,
                position = null,
                includeDeclaration = false
            )

            val element = finder.findTargetElement(fixtureProject, args)!!
            val references = finder.findReferences(fixtureProject, element, args)

            val result = finder.createGroupedResult(references, element)

            // Check if test coverage insight is generated
            if (!result.summary.hasTestUsages) {
                val hasTestInsight = result.insights.any { it.contains("test code") }
                assertTrue(hasTestInsight, "Should suggest adding tests when no test usage found")
            }
        }
    }

    @Test
    fun testPrimaryUsageInsight() {
        ApplicationManager.getApplication().runReadAction {
            val args = FindReferencesArgs(
                symbolName = "User",
                filePath = null,
                position = null,
                includeDeclaration = false
            )

            val element = finder.findTargetElement(fixtureProject, args)!!
            val references = finder.findReferences(fixtureProject, element, args)

            val result = finder.createGroupedResult(references, element)

            // Should provide insight about primary usage pattern
            assertTrue(
                result.insights.any { it.contains("Primary usage") },
                "Should provide insight about primary usage patterns"
            )
        }
    }
}
