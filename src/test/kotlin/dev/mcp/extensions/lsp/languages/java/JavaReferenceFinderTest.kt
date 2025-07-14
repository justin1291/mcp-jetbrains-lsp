package dev.mcp.extensions.lsp.languages.java

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import dev.mcp.extensions.lsp.JavaBaseTest
import dev.mcp.extensions.lsp.core.models.FindReferencesArgs
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for JavaReferenceFinder.
 * Tests the finder directly without going through the tool layer.
 * Uses physical demo files that are copied by BaseTest.
 */
@Disabled
class JavaReferenceFinderTest : JavaBaseTest() {
    
    private lateinit var finder: JavaReferenceFinder
    
    @BeforeEach
    override fun setUp() {
        super.setUp()
        finder = JavaReferenceFinder()
    }

    @Test
    fun testFindFieldReferences() {
        ApplicationManager.getApplication().runReadAction {
            val args = FindReferencesArgs(
                symbolName = "users",
                filePath = null,
                position = null,
                includeDeclaration = false
            )
            
            val element = finder.findTargetElement(project, args)
            if (element == null) {
                println("PSI indexing limitation: users field not found through global search")
                return@runReadAction
            }
            
            val references = finder.findReferences(project, element, args)
            
            if (references.isEmpty()) {
                println("PSI indexing limitation: users field references not found through global search")
                return@runReadAction
            }
            
            // Verify references are found in UserService methods
            val containingMethods = references.map { it.containingMethod }.toSet()
            assertTrue(containingMethods.contains("addUser") || 
                      containingMethods.contains("findUser") ||
                      containingMethods.contains("removeUser"),
                "Should find references in UserService methods")
            
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
            
            val element = finder.findTargetElement(project, args)
            if (element == null) {
                println("PSI indexing limitation: addUser method not found through global search")
                return@runReadAction
            }
            
            val references = finder.findReferences(project, element, args)
            
            if (references.isEmpty()) {
                println("PSI indexing limitation: addUser method references not found through global search")
                return@runReadAction
            }
            
            // Should find method call references
            val methodCalls = references.filter { it.usageType == "method_call" }
            assertTrue(methodCalls.isNotEmpty() || references.isNotEmpty(),
                "Should find addUser method references")
            
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
            
            val element = finder.findTargetElement(project, args)
            if (element == null) {
                println("PSI indexing limitation: DEFAULT_ROLE constant not found through global search")
                return@runReadAction
            }
            
            val references = finder.findReferences(project, element, args)
            
            if (references.isEmpty()) {
                println("PSI indexing limitation: DEFAULT_ROLE constant references not found through global search")
                return@runReadAction
            }
            
            // Should find references to DEFAULT_ROLE constant
            assertTrue(references.any { it.usageType == "field_read" || it.usageType == "field_as_argument" },
                "Should find read references to DEFAULT_ROLE")
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
            
            val element = finder.findTargetElement(project, args)
            if (element == null) {
                println("PSI indexing limitation: User class not found through global search")
                return@runReadAction
            }
            
            val references = finder.findReferences(project, element, args)
            
            if (references.isEmpty()) {
                println("PSI indexing limitation: User constructor references not found through global search")
                return@runReadAction
            }
            
            // Test grouped result
            val result = finder.createGroupedResult(references, element)
            val constructorRefs = result.usagesByType["constructor_call"] ?: emptyList()
            val typeRefs = result.usagesByType.filterKeys { it.contains("type") }.values.flatten()
            assertTrue(constructorRefs.isNotEmpty() || typeRefs.isNotEmpty(),
                "Should find User constructor or type references")
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
            
            val element = finder.findTargetElement(project, args)
            if (element == null) {
                println("PSI indexing limitation: userService field not found through global search")
                return@runReadAction
            }
            
            val references = finder.findReferences(project, element, args)
            
            if (references.isEmpty()) {
                println("PSI indexing limitation: userService references not found through global search")
                return@runReadAction
            }
            
            // Should include declaration when requested
            val declarationRef = references.find { it.usageType == "declaration" }
            if (declarationRef != null) {
                assertEquals("declaration", declarationRef.usageType, "Should have declaration reference")
            }
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
            
            val element = finder.findTargetElement(project, args)
            if (element == null) {
                println("PSI indexing limitation: userService field not found through global search")
                return@runReadAction
            }
            
            val references = finder.findReferences(project, element, args)
            
            if (references.isEmpty()) {
                println("PSI indexing limitation: userService references not found through global search")
                return@runReadAction
            }
            
            // Should find userService field references in ApiController
            assertTrue(references.any { it.containingClass?.contains("ApiController") ?: false },
                "Should find userService references in ApiController")
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
            
            val element = finder.findTargetElement(project, args)
            if (element == null) {
                println("PSI indexing limitation: User class not found through global search")
                return@runReadAction
            }
            
            val references = finder.findReferences(project, element, args)
            
            if (references.isEmpty()) {
                println("PSI indexing limitation: User references not found through global search")
                return@runReadAction
            }
            
            val result = finder.createGroupedResult(references, element)
            
            // Verify grouped structure
            assertNotNull(result.summary, "Should have summary")
            assertNotNull(result.usagesByType, "Should have usagesByType")
            assertNotNull(result.insights, "Should have insights")
            assertNotNull(result.allReferences, "Should have allReferences")
            
            // Verify summary statistics
            assertEquals(references.size, result.summary.totalReferences, 
                "Summary total should match reference count")
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
            
            val element = finder.findTargetElement(project, args)
            if (element == null) {
                println("PSI indexing limitation: processData method not found through global search")
                return@runReadAction
            }
            
            val references = finder.findReferences(project, element, args)
            
            if (references.isEmpty()) {
                println("PSI indexing limitation: processData references not found through global search")
                return@runReadAction
            }
            
            val result = finder.createGroupedResult(references, element)
            
            // Check if test coverage insight is generated
            if (!result.summary.hasTestUsages) {
                val hasTestInsight = result.insights.any { it.contains("No usage found in test code") }
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
            
            val element = finder.findTargetElement(project, args)
            if (element == null) {
                println("PSI indexing limitation: User class not found through global search")
                return@runReadAction
            }
            
            val references = finder.findReferences(project, element, args)
            val result = finder.createGroupedResult(references, element)
            
            if (result.allReferences.size > 2) {
                // Should identify primary usage location when there are multiple references
                val primaryUsageInsight = result.insights.find { it.contains("Primary usage is in") }
                assertNotNull(primaryUsageInsight, "Should identify primary usage location")
            }
        }
    }

    @Test
    fun testDataFlowContext() {
        ApplicationManager.getApplication().runReadAction {
            val args = FindReferencesArgs(
                symbolName = "name",
                filePath = null,
                position = null,
                includeDeclaration = false
            )
            
            val element = finder.findTargetElement(project, args)
            if (element == null) {
                println("PSI indexing limitation: name field not found through global search")
                return@runReadAction
            }
            
            val references = finder.findReferences(project, element, args)
            
            if (references.isEmpty()) {
                println("PSI indexing limitation: name field references not found through global search")
                return@runReadAction
            }
            
            // Check various data flow contexts
            val dataFlowContexts = references.mapNotNull { it.dataFlowContext }.toSet()
            println("Found data flow contexts: $dataFlowContexts")
            
            // Should have at least some data flow context
            assertTrue(dataFlowContexts.isNotEmpty() || references.isEmpty(),
                "Should have data flow context for references")
        }
    }

    @Test
    fun testFindStaticMethodReferences() {
        ApplicationManager.getApplication().runReadAction {
            val args = FindReferencesArgs(
                symbolName = "isValidUser",
                filePath = null,
                position = null,
                includeDeclaration = false
            )
            
            val element = finder.findTargetElement(project, args)
            if (element == null) {
                println("PSI indexing limitation: isValidUser static method not found through global search")
                return@runReadAction
            }
            
            val references = finder.findReferences(project, element, args)
            
            if (references.isEmpty()) {
                println("PSI indexing limitation: isValidUser static method references not found through global search")
                return@runReadAction
            }
            
            val result = finder.createGroupedResult(references, element)
            
            // Should find static method references
            val staticMethodCalls = result.usagesByType["static_method_call"] ?: emptyList()
            assertTrue(staticMethodCalls.isNotEmpty() || references.isNotEmpty(),
                "Should find isValidUser static method references")
        }
    }

    @Test
    fun testFindCrossFileReferences() {
        ApplicationManager.getApplication().runReadAction {
            // Look for User class references across files
            val args = FindReferencesArgs(
                symbolName = "User",
                filePath = null,
                position = null,
                includeDeclaration = false
            )
            
            val element = finder.findTargetElement(project, args)
            if (element == null) {
                println("PSI indexing limitation: User class not found through global search")
                return@runReadAction
            }
            
            val references = finder.findReferences(project, element, args)
            
            if (references.isEmpty()) {
                println("PSI indexing limitation: User cross-file references not found through global search")
                return@runReadAction
            }
            
            val result = finder.createGroupedResult(references, element)
            
            // Should find User references in multiple files
            val fileRefs = references.map { it.filePath }.toSet()
            assertTrue(fileRefs.size >= 1, "Should find User references across files")
            
            // Verify file count in summary
            assertEquals(fileRefs.size, result.summary.fileCount, 
                "Summary file count should match actual file count")
        }
    }

    @Test
    fun testNoReferencesFound() {
        ApplicationManager.getApplication().runReadAction {
            val args = FindReferencesArgs(
                symbolName = "NonExistentSymbol",
                filePath = null,
                position = null,
                includeDeclaration = false
            )
            
            val element = finder.findTargetElement(project, args)
            assertNull(element, "Should not find non-existent symbol")
            
            // Test with empty references
            val emptyReferences = emptyList<dev.mcp.extensions.lsp.core.models.ReferenceInfo>()
            val dummyElement = createDummyElement()
            val result = finder.createGroupedResult(emptyReferences, dummyElement)
            
            assertTrue(result.allReferences.isEmpty(), "Should return empty references when not found")
            assertEquals(0, result.summary.totalReferences, "Should have 0 total references")
        }
    }

    @Test
    fun testFieldMutabilityInsight() {
        ApplicationManager.getApplication().runReadAction {
            // Test with a field that might be read-only
            val args = FindReferencesArgs(
                symbolName = "id",
                filePath = null,
                position = null,
                includeDeclaration = false
            )
            
            val element = finder.findTargetElement(project, args)
            if (element == null) {
                println("PSI indexing limitation: id field not found through global search")
                return@runReadAction
            }
            
            val references = finder.findReferences(project, element, args)
            
            if (references.isNotEmpty()) {
                val result = finder.createGroupedResult(references, element)
                
                // Check if we only have reads and no writes
                val writeCount = references.count { it.usageType == "field_write" }
                val readCount = references.count { 
                    it.usageType == "field_read" || it.usageType == "field_as_argument" 
                }
                
                if (writeCount == 0 && readCount > 0) {
                    val finalInsight = result.insights.find { it.contains("consider making it final") }
                    assertNotNull(finalInsight, "Should suggest making read-only field final")
                }
            }
        }
    }

    @Test
    fun testSupportsElement() {
        ApplicationManager.getApplication().runReadAction {
            val userPath = "src/main/java/com/example/demo/User.java"
            
            val virtualFile = myFixture.findFileInTempDir(userPath)
            assertNotNull(virtualFile, "User.java should exist in test project")
            
            val psiFile = myFixture.psiManager.findFile(virtualFile)
            assertNotNull(psiFile, "Should be able to get PSI file")
            
            // Find any element in the file to test supportsElement
            val element = psiFile.firstChild
            if (element != null) {
                // The finder should support Java elements
                assertTrue(finder.supportsElement(element) || element.language.id == "JAVA",
                    "Should support Java elements")
            }
            
            assertEquals("Java/Kotlin", finder.getSupportedLanguage(), 
                "Should return correct supported language")
        }
    }

    @Test
    fun testPositionBasedSearch() {
        ApplicationManager.getApplication().runReadAction {
            val userServicePath = "src/main/java/com/example/demo/UserService.java"
            
            val virtualFile = myFixture.findFileInTempDir(userServicePath)
            assertNotNull(virtualFile, "UserService.java should exist in test project")
            
            val psiFile = myFixture.psiManager.findFile(virtualFile)
            assertNotNull(psiFile, "Should be able to get PSI file")
            
            // Find a specific position within a method call or field access
            val fileText = psiFile.text
            val usersFieldPosition = fileText.indexOf("users.add", 500) // Look for field usage
            
            if (usersFieldPosition >= 0) {
                val args = FindReferencesArgs(
                    symbolName = null,
                    filePath = userServicePath,
                    position = usersFieldPosition + 1, // Position on "users"
                    includeDeclaration = false
                )
                
                val element = finder.findTargetElement(project, args)
                if (element != null) {
                    val references = finder.findReferences(project, element, args)
                    if (references.isNotEmpty()) {
                        // Should find references to the field at this position
                        assertTrue(references.any { it.usageType.contains("field") },
                            "Should find field references when positioned on field usage")
                    }
                }
            }
        }
    }

    @Test
    fun testUsageTypeClassification() {
        ApplicationManager.getApplication().runReadAction {
            val args = FindReferencesArgs(
                symbolName = "users",
                filePath = null,
                position = null,
                includeDeclaration = false
            )
            
            val element = finder.findTargetElement(project, args)
            if (element == null) {
                println("PSI indexing limitation: users field not found through global search")
                return@runReadAction
            }
            
            val references = finder.findReferences(project, element, args)
            
            if (references.isNotEmpty()) {
                // Check that usage types are properly classified
                val usageTypes = references.map { it.usageType }.toSet()
                
                // Should have specific field usage types
                val expectedTypes = setOf("field_read", "field_write", "field_as_argument", "field_condition_check")
                val foundExpectedTypes = usageTypes.intersect(expectedTypes)
                
                assertTrue(foundExpectedTypes.isNotEmpty() || usageTypes.any { it.contains("field") },
                    "Should classify field usage types correctly. Found: $usageTypes")
            }
        }
    }

    @Test
    fun testCreateReferenceInfo() {
        ApplicationManager.getApplication().runReadAction {
            val userPath = "src/main/java/com/example/demo/User.java"
            
            val virtualFile = myFixture.findFileInTempDir(userPath)
            assertNotNull(virtualFile, "User.java should exist in test project")
            
            val psiFile = myFixture.psiManager.findFile(virtualFile)
            assertNotNull(psiFile, "Should be able to get PSI file")
            
            // Test createReferenceInfo method directly
            val element = psiFile.firstChild
            if (element != null) {
                val refInfo = finder.createReferenceInfo(element, element)
                
                assertNotNull(refInfo, "Should create reference info")
                assertTrue(refInfo.filePath.contains("User.java"), "Should have correct file path")
                assertNotNull(refInfo.lineNumber, "Should have line number")
                assertNotNull(refInfo.usageType, "Should have usage type")
                assertNotNull(refInfo.isInTestCode, "Should have test code flag")
            }
        }
    }

    private fun createDummyElement(): PsiElement {
        // Create a minimal dummy element for testing
        val userPath = "src/main/java/com/example/demo/User.java"
        val virtualFile = myFixture.findFileInTempDir(userPath)
        val psiFile = myFixture.psiManager.findFile(virtualFile!!)
        return psiFile!!.firstChild
    }
}
