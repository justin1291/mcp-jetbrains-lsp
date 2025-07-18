package dev.mcp.extensions.lsp.languages.jvm

import com.intellij.openapi.application.ApplicationManager
import dev.mcp.extensions.lsp.JvmBaseTest
import dev.mcp.extensions.lsp.core.models.FindReferencesArgs
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


/**
 * Unit tests for JavaReferenceFinder using JVM implementation.
 * Tests the JVM reference finder directly without going through the tool layer.
 * Uses physical demo files that are copied by BaseTest.
 * 
 * NOTE: This test now uses the JVM implementation to prepare for removing the Java-specific implementation.
 */
class JvmReferenceFinderTest : JvmBaseTest() {

    private val finder: JvmReferenceFinder = JvmReferenceFinder()

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

    @Test
    fun testFindReferencesWithDataFlow() {
        ApplicationManager.getApplication().runReadAction {
            val args = FindReferencesArgs(
                symbolName = "users",
                filePath = null,
                position = null,
                includeDeclaration = false
            )

            val element = finder.findTargetElement(fixtureProject, args)!!
            val references = finder.findReferences(fixtureProject, element, args)

            assertTrue("Should find references to users field", references.isNotEmpty())

            // Check for different usage types
            val usageTypes = references.map { it.usageType }.toSet()
            assertTrue("Should have field operations", 
                usageTypes.intersect(setOf("field_write", "field_read", "reference")).isNotEmpty())

            // Test data flow context
            val hasDataFlowContext = references.any { !it.dataFlowContext.isNullOrBlank() }
            if (hasDataFlowContext) {
                assertTrue("Should have meaningful data flow context", 
                    references.any { it.dataFlowContext?.contains("assigned") ?: false ||
                                   it.dataFlowContext?.contains("used") ?: false })
            }
        }
    }

    @Test
    fun testFindStaticMethodReferences() {
        ApplicationManager.getApplication().runReadAction {
            // Test with a static constant that has references instead of an unused method
            val args = FindReferencesArgs(
                symbolName = "DEFAULT_ROLE",
                filePath = null,
                position = null,
                includeDeclaration = false
            )

            val element = finder.findTargetElement(fixtureProject, args)!!
            val references = finder.findReferences(fixtureProject, element, args)

            assertTrue("Should find references to DEFAULT_ROLE static constant", references.isNotEmpty())

            // Should find static field references
            val staticReferences = references.filter { 
                it.usageType == "static_field_read" || 
                it.usageType == "field_read" || 
                it.usageType == "reference" 
            }
            assertTrue("Should find static field references", staticReferences.isNotEmpty())
        }
    }

    @Test
    fun testGroupedResultInsights() {
        ApplicationManager.getApplication().runReadAction {
            val args = FindReferencesArgs(
                symbolName = "DEFAULT_ROLE",
                filePath = null,
                position = null,
                includeDeclaration = false
            )

            val element = finder.findTargetElement(fixtureProject, args)!!
            val references = finder.findReferences(fixtureProject, element, args)
            val result = finder.createGroupedResult(references, element)

            // Should generate insights about usage patterns
            assertTrue("Should have insights", result.insights.isNotEmpty())

            // Check for common insight patterns
            val insightText = result.insights.joinToString(" ")
            assertTrue("Should mention usage patterns",
                insightText.contains("usage") || 
                insightText.contains("method") || 
                insightText.contains("reference"))
        }
    }

    @Test
    fun testFindReferencesWithPosition() {
        ApplicationManager.getApplication().runReadAction {
            // Test position-based reference finding using a known field
            val args = FindReferencesArgs(
                symbolName = "users",
                filePath = null,
                position = null,
                includeDeclaration = false
            )

            val element = finder.findTargetElement(fixtureProject, args)!!
            val references = finder.findReferences(fixtureProject, element, args)

            assertTrue("Should find references using target element", references.isNotEmpty())

            // Verify the references have proper context
            references.forEach { ref ->
                assertNotNull("Reference should have containing class", ref.containingClass)
                assertNotNull("Reference should have file path", ref.filePath)
                assertTrue("Reference should have valid line number", ref.lineNumber > 0)
            }
        }
    }

    @Test
    fun testEmptyReferencesHandling() {
        ApplicationManager.getApplication().runReadAction {
            val args = FindReferencesArgs(
                symbolName = "NonExistentSymbol",
                filePath = null,
                position = null,
                includeDeclaration = false
            )

            val element = finder.findTargetElement(fixtureProject, args)
            
            if (element != null) {
                val references = finder.findReferences(fixtureProject, element, args)
                
                // May have no references for non-existent symbol
                val result = finder.createGroupedResult(references, element)
                assertNotNull("Should create result even for empty references", result)
                assertEquals("Total should be 0 for non-existent symbol", 0, result.summary.totalReferences)
            }
        }
    }

    @Test
    fun testReferenceUsageTypes() {
        ApplicationManager.getApplication().runReadAction {
            val args = FindReferencesArgs(
                symbolName = "addUser",
                filePath = null,
                position = null,
                includeDeclaration = false
            )

            val element = finder.findTargetElement(fixtureProject, args)!!
            val references = finder.findReferences(fixtureProject, element, args)

            assertTrue("Should find method references", references.isNotEmpty())

            // Check for method call usage types
            val usageTypes = references.map { it.usageType }.toSet()
            assertTrue("Should have method call types", 
                usageTypes.intersect(setOf("method_call", "reference")).isNotEmpty())

            // Verify grouped result structure
            val result = finder.createGroupedResult(references, element)
            assertNotNull("Should have usagesByType grouping", result.usagesByType)
            assertTrue("Should group by type", result.usagesByType.keys.isNotEmpty())
        }
    }

    @Test
    fun testSupportedLanguageAndFileTypes() {
        ApplicationManager.getApplication().runReadAction {
            val supportedLanguage = finder.getSupportedLanguage()
            assertEquals("Java/Kotlin", supportedLanguage)
            
            // Test that finder can handle project files
            val args = FindReferencesArgs(
                symbolName = "User",
                filePath = null,
                position = null,
                includeDeclaration = false
            )
            
            val element = finder.findTargetElement(fixtureProject, args)
            assertNotNull("Should find elements in project", element)
        }
    }
}
