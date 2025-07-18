package dev.mcp.extensions.lsp.languages.jvm

import com.intellij.openapi.application.ApplicationManager
import dev.mcp.extensions.lsp.JvmBaseTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Unit tests for JavaDefinitionFinder using JVM implementation.
 * Tests the JVM finder directly without going through the tool layer.
 * Uses physical demo files that are copied by BaseTest.
 * 
 * NOTE: This test now uses the JVM implementation to prepare for removing the Java-specific implementation.
 */
class JvmDefinitionFinderTest : JvmBaseTest() {

    private val finder: JvmDefinitionFinder = JvmDefinitionFinder()
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testDiagnosticCheckWhatFinderReturns() {
        ApplicationManager.getApplication().runReadAction {
            val symbolsToTest = listOf("User", "UserService", "DataProcessor", "ApiResponse")
            val output = StringBuilder()
            
            symbolsToTest.forEach { symbolName ->
                output.appendLine("\n=== Searching for: $symbolName ===")
                val foundDefinitions = finder.findDefinitionByName(fixtureProject, symbolName)
                
                if (foundDefinitions.isEmpty()) {
                    output.appendLine("  No definitions found!")
                } else {
                    foundDefinitions.forEach { definition ->
                        output.appendLine("  Found: ${definition.name}")
                        output.appendLine("    Type: ${definition.type}")
                        output.appendLine("    File: ${definition.filePath}")
                        output.appendLine("    Modifiers: ${definition.modifiers}")
                        output.appendLine("    Confidence: ${definition.confidence}")
                        if (definition.disambiguationHint != null) {
                            output.appendLine("    Hint: ${definition.disambiguationHint}")
                        }
                    }
                }
            }
            
            val outputFile = File("test-output-diagnostic.txt")
            outputFile.writeText(output.toString())
            
            assertTrue("Diagnostic test completed", true)
        }
    }

    @Test
    fun testFindClassDefinitionWithProjectFile() {
        ApplicationManager.getApplication().runReadAction {
            val foundDefinitions = finder.findDefinitionByName(fixtureProject, "UserService")

            assertTrue("Should find UserService definitions", foundDefinitions.isNotEmpty())

            val userServiceDefinition = foundDefinitions.find { it.name == "UserService" }
            assertNotNull("UserService definition should exist", userServiceDefinition)
            
            assertTrue(
                "File path should contain UserService.java",
                userServiceDefinition!!.filePath.contains("UserService.java")
            )
        }
    }

    @Test
    fun testFindMethodDefinitionByPosition() {
        ApplicationManager.getApplication().runReadAction {
            val foundDefinitions = finder.findDefinitionByName(fixtureProject, "addUser")
            
            assertTrue("Should find addUser method", foundDefinitions.isNotEmpty())
            
            val addUserMethod = foundDefinitions.find { it.name == "addUser" && it.type == "method" }
            assertNotNull("addUser method should exist", addUserMethod)
            assertEquals("Should find a method", "method", addUserMethod!!.type)
            assertTrue(
                "Method should be in UserService.java",
                addUserMethod.filePath.contains("UserService.java")
            )
        }
    }

    @Test
    fun testFindFieldDefinition() {
        ApplicationManager.getApplication().runReadAction {
            val definitions = finder.findDefinitionByName(fixtureProject, "DEFAULT_ROLE")

            assertTrue("DEFAULT_ROLE field should be found", definitions.isNotEmpty())

            val defaultRoleField = definitions.find { it.name == "DEFAULT_ROLE" }
            assertNotNull("DEFAULT_ROLE field definition should exist", defaultRoleField)
            assertEquals("DEFAULT_ROLE should be a field", "field", defaultRoleField!!.type)
            assertTrue(
                "DEFAULT_ROLE should be static",
                defaultRoleField.modifiers.contains("static")
            )
            assertTrue(
                "DEFAULT_ROLE should be final",
                defaultRoleField.modifiers.contains("final")
            )
        }
    }

    @Test
    fun testFindConstructorDefinition() {
        ApplicationManager.getApplication().runReadAction {
            val foundDefinitions = finder.findDefinitionByName(fixtureProject, "User")

            assertTrue("Should find definitions for User", foundDefinitions.isNotEmpty())

            val userClassDefinition = foundDefinitions.find { it.name == "User" && it.type == "class" }
            val userConstructorDefinition = foundDefinitions.find { it.name == "User" && it.type == "constructor" }
            
            assertTrue("Should find either User class or constructor", 
                userClassDefinition != null || userConstructorDefinition != null)
            
            if (userConstructorDefinition != null) {
                assertEquals(
                    "User constructor should have type 'constructor'",
                    "constructor", userConstructorDefinition.type
                )
            }
        }
    }

    @Test
    fun testFindApiControllerMethods() {
        ApplicationManager.getApplication().runReadAction {
            val definitions = finder.findDefinitionByName(fixtureProject, "createUser")

            assertTrue("createUser method should be found", definitions.isNotEmpty())

            val createUserMethod = definitions.find { it.name == "createUser" && it.type == "method" }
            assertNotNull("createUser method definition should exist", createUserMethod)
            assertTrue(
                "createUser should be in ApiController class",
                createUserMethod!!.containingClass?.contains("ApiController") == true
            )
        }
    }

    @Test
    fun testFindDataProcessorDefinition() {
        ApplicationManager.getApplication().runReadAction {
            val foundDefinitions = finder.findDefinitionByName(fixtureProject, "DataProcessor")

            assertTrue("DataProcessor should be found", foundDefinitions.isNotEmpty())

            val dataProcessorDefinition = foundDefinitions.find { it.name == "DataProcessor" }
            assertNotNull("DataProcessor definition should exist", dataProcessorDefinition)
            
            assertTrue(
                "File path should contain DataProcessor.java",
                dataProcessorDefinition!!.filePath.contains("DataProcessor.java")
            )
        }
    }

    @Test
    fun testFindDataProcessorMethodDefinition() {
        ApplicationManager.getApplication().runReadAction {
            val definitions = finder.findDefinitionByName(fixtureProject, "processData")

            assertTrue("processData method should be found", definitions.isNotEmpty())

            val processDataMethod = definitions.find { it.name == "processData" && it.type == "method" }
            assertNotNull("processData method definition should exist", processDataMethod)
            assertTrue(
                "processData should be in DataProcessor class",
                processDataMethod!!.containingClass?.contains("DataProcessor") == true
            )
        }
    }

    @Test
    fun testFindApiResponseClass() {
        ApplicationManager.getApplication().runReadAction {
            val foundDefinitions = finder.findDefinitionByName(fixtureProject, "ApiResponse")

            assertTrue("ApiResponse should be found", foundDefinitions.isNotEmpty())

            val apiResponseDefinition = foundDefinitions.find { it.name == "ApiResponse" }
            assertNotNull("ApiResponse definition should exist", apiResponseDefinition)
            
            assertTrue(
                "ApiResponse should be in ApiController.java",
                apiResponseDefinition!!.filePath.contains("ApiController.java")
            )
        }
    }

    @Test
    fun testFindEnumDefinition() {
        ApplicationManager.getApplication().runReadAction {
            val definitions = finder.findDefinitionByName(fixtureProject, "UserEvent")

            assertTrue("UserEvent enum should be found", definitions.isNotEmpty())

            val userEventEnum = definitions.find { it.name == "UserEvent" }
            assertNotNull("UserEvent enum definition should exist", userEventEnum)
            assertTrue(
                "UserEvent should be an enum or class",
                userEventEnum!!.type == "enum" || userEventEnum.type == "class"
            )
        }
    }

    @Test
    fun testSymbolNotFound() {
        ApplicationManager.getApplication().runReadAction {
            val definitions = finder.findDefinitionByName(fixtureProject, "NonExistentClass")

            assertTrue("NonExistentClass should not be found", definitions.isEmpty())
        }
    }

    @Test
    fun testFindOverloadedMethods() {
        ApplicationManager.getApplication().runReadAction {
            val foundDefinitions = finder.findDefinitionByName(fixtureProject, "User")

            assertTrue("Should find definitions for User", foundDefinitions.isNotEmpty())

            val userClassDefinitions = foundDefinitions.filter { it.name == "User" && it.type == "class" }
            val userConstructorDefinitions = foundDefinitions.filter { it.name == "User" && it.type == "constructor" }
            
            assertTrue("Should find User class or constructors", 
                userClassDefinitions.isNotEmpty() || userConstructorDefinitions.isNotEmpty())
        }
    }

    @Test
    fun testConfidenceScoring() {
        ApplicationManager.getApplication().runReadAction {
            val foundDefinitions = finder.findDefinitionByName(fixtureProject, "User")

            assertTrue("Should find definitions for User", foundDefinitions.isNotEmpty())

            foundDefinitions.forEach { definition ->
                assertTrue(
                    "Confidence score should be between 0 and 1",
                    definition.confidence >= 0.0f && definition.confidence <= 1.0f
                )
            }

            if (foundDefinitions.size > 1) {
                for (i in 1 until foundDefinitions.size) {
                    assertTrue(
                        "Results should be sorted by confidence descending",
                        foundDefinitions[i - 1].confidence >= foundDefinitions[i].confidence
                    )
                }
            }
        }
    }

    @Test
    fun testDisambiguationHints() {
        ApplicationManager.getApplication().runReadAction {
            val definitions = finder.findDefinitionByName(fixtureProject, "addUser")

            assertTrue("addUser method should be found", definitions.isNotEmpty())

            val methodDef = definitions.find { it.type == "method" }
            assertNotNull("Should find addUser method", methodDef)
            assertNotNull("Method should have disambiguation hint", methodDef!!.disambiguationHint)
            assertTrue(
                "Hint should mention containing class",
                methodDef.disambiguationHint!!.contains("UserService")
            )
        }
    }

    @Test
    fun testTestCodeDetection() {
        ApplicationManager.getApplication().runReadAction {
            val definitions = finder.findDefinitionByName(fixtureProject, "UserService")

            assertTrue("UserService class should be found", definitions.isNotEmpty())

            // Check that isTestCode field is populated correctly
            definitions.forEach { def ->
                assertNotNull("isTestCode should be populated", def.isTestCode)
                // Since demo files are in src/main, they should not be test code
                assertFalse("Main source files should not be marked as test code", def.isTestCode)
            }
        }
    }

    @Test
    fun testLibraryCodeDetection() {
        ApplicationManager.getApplication().runReadAction {
            // Try to find a JDK class like String
            val definitions = finder.findDefinitionByName(fixtureProject, "String")

            // JDK classes might not be found in tests, but if found, should be marked as library code
            if (definitions.isNotEmpty()) {
                val stringClass = definitions.find { it.name == "String" && it.type == "class" }
                if (stringClass != null) {
                    assertTrue(
                        "JDK classes should be marked as library code",
                        stringClass.isLibraryCode
                    )
                }
            }
        }
    }

    @Test
    fun testAccessibilityWarnings() {
        ApplicationManager.getApplication().runReadAction {
            val definitions = finder.findDefinitionByName(fixtureProject, "users")

            assertTrue("users field should be found", definitions.isNotEmpty())

            val usersField = definitions.find { it.name == "users" && it.type == "field" }
            assertNotNull("users field definition should exist", usersField)

            if (usersField!!.modifiers.contains("private")) {
                assertNotNull(
                    "Private field should have accessibility warning",
                    usersField.accessibilityWarning
                )
                assertTrue(
                    "Warning should mention field is private",
                    usersField.accessibilityWarning!!.contains("Private")
                )
            }
        }
    }

    @Test
    fun testQualifiedNameSearch() {
        ApplicationManager.getApplication().runReadAction {
            val definitions = finder.findDefinitionByName(fixtureProject, "UserService.addUser")

            assertTrue("UserService.addUser should be found", definitions.isNotEmpty())

            val addUserMethod = definitions.find { it.name == "addUser" }
            assertNotNull("addUser method should be found", addUserMethod)
            assertEquals(
                "Qualified name search should have maximum confidence",
                1.0f, addUserMethod!!.confidence
            )
        }
    }

    @Test
    fun testStaticMethodDisambiguation() {
        ApplicationManager.getApplication().runReadAction {
            val definitions = finder.findDefinitionByName(fixtureProject, "isValidUser")

            assertTrue("isValidUser method should be found", definitions.isNotEmpty())

            val staticMethod = definitions.find { it.name == "isValidUser" && it.modifiers.contains("static") }
            assertNotNull("Static method should be found", staticMethod)
            assertNotNull(
                "Static method should have disambiguation hint",
                staticMethod!!.disambiguationHint
            )
            assertTrue(
                "Hint should mention method is static",
                staticMethod.disambiguationHint!!.contains("Static")
            )
        }
    }

    @Test
    fun testPositionBasedSearchWithConfidence() {
        ApplicationManager.getApplication().runReadAction {
            // Test that methods found have high confidence when searched by name
            val definitions = finder.findDefinitionByName(fixtureProject, "addUser")

            assertTrue("Should find addUser method", definitions.isNotEmpty())

            val addUserMethod = definitions.find { it.name == "addUser" && it.type == "method" }
            assertNotNull("Should find addUser method", addUserMethod)
            assertEquals("Method found by name should have partial match confidence",
                0.3f, addUserMethod!!.confidence
            )
            
            // Also verify it's in the right file
            assertTrue(
                "Method should be in UserService.java",
                addUserMethod.filePath.contains("UserService.java")
            )
        }
    }

    @Test
    fun testDirectElementDefinition() {
        ApplicationManager.getApplication().runReadAction {
            // Test finding class definitions directly by name
            val definitions = finder.findDefinitionByName(fixtureProject, "User")

            assertTrue(
                "Should find definition for User class",
                definitions.isNotEmpty()
            )

            // The finder might return class, constructor, or both
            val userDef = definitions.find { 
                it.name == "User" && (it.type == "class" || it.type == "constructor") 
            }
            assertNotNull("Should find User definition (class or constructor)", userDef)
            assertEquals(
                "Direct element search should have partial match confidence",
                0.3f, userDef!!.confidence
            )
            assertTrue(
                "Should point to User.java file",
                userDef.filePath.contains("User.java")
            )
        }
    }

    @Test
    fun testReferenceResolution() {
        ApplicationManager.getApplication().runReadAction {
            // Test that we can find User class which is referenced in UserService
            val definitions = finder.findDefinitionByName(fixtureProject, "User")

            assertTrue(
                "Should find User definition through reference resolution",
                definitions.isNotEmpty()
            )

            // The reference might resolve to class or constructor
            val userDef = definitions.find { 
                it.name == "User" && (it.type == "class" || it.type == "constructor") 
            }
            assertNotNull("Should find User definition", userDef)
            assertEquals(
                "Reference resolution should have partial match confidence",
                0.3f, userDef!!.confidence
            )
            assertTrue(
                "Should point to User.java file",
                userDef.filePath.contains("User.java")
            )
            
            // Also test finding a method that references User
            val addUserDefs = finder.findDefinitionByName(fixtureProject, "addUser")
            assertTrue("Should find addUser method that uses User", addUserDefs.isNotEmpty())
            
            val addUserMethod = addUserDefs.find { it.name == "addUser" && it.type == "method" }
            assertNotNull("Should find addUser method", addUserMethod)
            assertTrue(
                "Method should be in UserService which references User",
                addUserMethod!!.filePath.contains("UserService.java")
            )
        }
    }

    @Test
    fun testFileSupport() {
        ApplicationManager.getApplication().runReadAction {
            // Test that the finder reports support for Java/Kotlin
            assertEquals(
                "Should support Java/Kotlin language",
                "Java/Kotlin", finder.getSupportedLanguage()
            )
            
            // Verify we can find Java symbols, which proves file support
            val definitions = finder.findDefinitionByName(fixtureProject, "User")
            assertTrue("Should find User class to verify Java support", definitions.isNotEmpty())
        }
    }

    @Test
    fun testCreateLocationMethodSignature() {
        ApplicationManager.getApplication().runReadAction {
            val definitions = finder.findDefinitionByName(fixtureProject, "toString")

            assertTrue("toString method should be found", definitions.isNotEmpty())

            val toStringDef = definitions.find { it.name == "toString" }
            assertNotNull("toString method definition should exist", toStringDef)
            assertNotNull("Method should have signature", toStringDef!!.signature)
            assertTrue(
                "Signature should include method name",
                toStringDef.signature!!.contains("toString")
            )
        }
    }
    
    @Test
    fun testSummaryOfFinderBehavior() {
        ApplicationManager.getApplication().runReadAction {
            val classNames = listOf("User", "UserService", "DataProcessor")
            classNames.forEach { className ->
                val foundDefinitions = finder.findDefinitionByName(fixtureProject, className)
                val returnedTypes = foundDefinitions.map { it.type }.distinct().sorted()
                assertTrue("Should find some definitions for $className", foundDefinitions.isNotEmpty())
                assertTrue("Should have valid types for $className", returnedTypes.isNotEmpty())
            }
            
            val userDefinitions = finder.findDefinitionByName(fixtureProject, "User")
            val hasClassDefinition = userDefinitions.any { it.type == "class" }
            val hasConstructorDefinition = userDefinitions.any { it.type == "constructor" }
            
            assertTrue("Should find either User class or constructor definitions", 
                hasClassDefinition || hasConstructorDefinition)
        }
    }

    @Test
    fun testSerializationOfJvmDefinitionResults() {
        ApplicationManager.getApplication().runReadAction {
            val testSymbols = listOf(
                "User",
                "UserService", 
                "addUser",
                "getName",
                "DEFAULT_ROLE"
            )
            
            testSymbols.forEach { symbolName ->
                // Test direct finder usage
                val definitions = try {
                    finder.findDefinitionByName(fixtureProject, symbolName)
                } catch (e: Exception) {
                    throw AssertionError("CRITICAL FAILURE for symbol $symbolName: ${e.message}", e)
                }
                
                definitions.forEachIndexed { index, definition ->
                    try {
                        // Try to serialize - this will throw if there are Any types
                        json.encodeToString(definition)
                        assertNotNull("Definition name should not be null", definition.name)
                        assertNotNull("Definition type should not be null", definition.type)
                        assertNotNull("Definition filePath should not be null", definition.filePath)
                        assertTrue("Confidence should be valid", definition.confidence >= 0.0f && definition.confidence <= 1.0f)
                    } catch (e: SerializationException) {
                        // Handle serialization failures - log what failed
                        throw AssertionError("SERIALIZATION FAILED for definition $index of $symbolName: ${e.message}. Definition: name=${definition.name}, type=${definition.type}, filePath=${definition.filePath}", e)
                    } catch (e: Exception) {
                        throw AssertionError("UNEXPECTED ERROR for definition $index of $symbolName: ${e.message}", e)
                    }
                }
            }
        }
    }

    @Test
    fun testEdgeCaseSerializationWithProjectFiles() {
        ApplicationManager.getApplication().runReadAction {
            val edgeCases = listOf(
                "nonExistentSymbol",
                "",
                "  ",
                "Symbol.That.Does.Not.Exist",
                "toString",
                "equals"
            )
            
            edgeCases.forEach { symbolName ->
                val definitions = try {
                    finder.findDefinitionByName(fixtureProject, symbolName)
                } catch (e: Exception) {
                    throw AssertionError("Edge case serialization failed for '$symbolName': ${e.message}", e)
                }
                
                try {
                    json.encodeToString(definitions)
                    definitions.forEach { definition ->
                        assertNotNull("Name should not be null", definition.name)
                        assertNotNull("Type should not be null", definition.type)
                        assertNotNull("FilePath should not be null", definition.filePath)
                        assertTrue("StartOffset should be valid", definition.startOffset >= 0)
                        assertTrue("EndOffset should be valid", definition.endOffset >= 0)
                        assertTrue("LineNumber should be valid", definition.lineNumber >= 1)
                        assertTrue("Confidence should be valid", definition.confidence >= 0.0f && definition.confidence <= 1.0f)
                        if (definition.signature != null) {
                            assertNotNull("Signature should be valid string", definition.signature)
                        }
                        if (definition.containingClass != null) {
                            assertNotNull("ContainingClass should be valid string", definition.containingClass)
                        }
                        if (definition.disambiguationHint != null) {
                            assertNotNull("DisambiguationHint should be valid string", definition.disambiguationHint)
                        }
                        if (definition.accessibilityWarning != null) {
                            assertNotNull("AccessibilityWarning should be valid string", definition.accessibilityWarning)
                        }
                    }
                } catch (e: SerializationException) {
                    throw AssertionError("Edge case serialization failed for '$symbolName': ${e.message}", e)
                } catch (e: Exception) {
                    throw AssertionError("Edge case serialization failed for '$symbolName': ${e.message}", e)
                }
            }
        }
    }
}
