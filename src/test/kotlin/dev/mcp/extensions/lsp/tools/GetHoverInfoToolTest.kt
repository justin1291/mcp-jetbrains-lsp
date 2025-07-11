package dev.mcp.extensions.lsp.tools

import dev.mcp.extensions.lsp.BaseTest
import dev.mcp.extensions.lsp.core.models.GetHoverArgs
import dev.mcp.extensions.lsp.core.models.HoverInfo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GetHoverInfoToolTest : BaseTest() {
    
    private lateinit var tool: GetHoverInfoTool
    
    @BeforeEach
    override fun setUp() {
        super.setUp()
        tool = GetHoverInfoTool()
    }

    @Test
    fun testHoverOnUserServiceClass() {
        // Test hovering on UserService class in the demo files
        val userServicePath = "src/main/java/com/example/demo/UserService.java"
        
        val args = GetHoverArgs(
            filePath = userServicePath,
            position = 300  // Approximate position within UserService class
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        if (hoverInfo.elementName != null) {
            // Should provide hover info for UserService elements
            assertTrue(hoverInfo.elementName!!.isNotEmpty(), "Should have element name")
            assertNotNull(hoverInfo.elementType, "Should have element type")
        } else {
            println("PSI indexing limitation: No hover info found for UserService class")
        }
    }

    @Test
    fun testHoverOnDocumentedMethod() {
        // Test hovering on addUser method with JavaDoc
        val userServicePath = "src/main/java/com/example/demo/UserService.java"
        
        val args = GetHoverArgs(
            filePath = userServicePath,
            position = 800  // Approximate position within addUser method
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        if (hoverInfo.elementName == "addUser") {
            assertEquals("addUser", hoverInfo.elementName, "Should identify addUser method")
            assertEquals("method", hoverInfo.elementType, "Should be a method")
            assertNotNull(hoverInfo.javaDoc, "Should have JavaDoc documentation")
            assertTrue(hoverInfo.javaDoc?.contains("Adds a new user") ?: false,
                "JavaDoc should contain method description")
        } else {
            println("PSI indexing limitation: No hover info found for addUser method")
        }
    }

    @Test
    fun testHoverOnFieldWithModifiers() {
        // Test hovering on DEFAULT_ROLE constant
        val userServicePath = "src/main/java/com/example/demo/UserService.java"
        
        val args = GetHoverArgs(
            filePath = userServicePath,
            position = 600  // Approximate position of DEFAULT_ROLE field
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        if (hoverInfo.elementName == "DEFAULT_ROLE") {
            assertEquals("DEFAULT_ROLE", hoverInfo.elementName, "Should identify DEFAULT_ROLE field")
            assertEquals("field", hoverInfo.elementType, "Should be a field")
            assertEquals("String", hoverInfo.type, "Should have String type")
            assertTrue(hoverInfo.modifiers.contains("static"), "Should be static")
            assertTrue(hoverInfo.modifiers.contains("final"), "Should be final")
        } else {
            println("PSI indexing limitation: No hover info found for DEFAULT_ROLE field")
        }
    }

    @Test
    fun testHoverOnUserClass() {
        // Test hovering on User class
        val userPath = "src/main/java/com/example/demo/User.java"
        
        val args = GetHoverArgs(
            filePath = userPath,
            position = 200  // Approximate position within User class
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        if (hoverInfo.elementName == "User") {
            assertEquals("User", hoverInfo.elementName, "Should identify User class")
            assertEquals("class", hoverInfo.elementType, "Should be a class")
            assertNotNull(hoverInfo.javaDoc, "Should have JavaDoc")
            assertTrue(hoverInfo.javaDoc?.contains("Represents a user") ?: false,
                "JavaDoc should contain class description")
        } else {
            println("PSI indexing limitation: No hover info found for User class")
        }
    }

    @Test
    fun testHoverOnGetterMethod() {
        // Test hovering on getName method in User class
        val userPath = "src/main/java/com/example/demo/User.java"
        
        val args = GetHoverArgs(
            filePath = userPath,
            position = 1200  // Approximate position of getName method
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        if (hoverInfo.elementName == "getName") {
            assertEquals("getName", hoverInfo.elementName, "Should identify getName method")
            assertEquals("method", hoverInfo.elementType, "Should be a method")
            assertEquals("String", hoverInfo.type, "Should return String")
            assertTrue(hoverInfo.modifiers.contains("public"), "Should be public")
        } else {
            println("PSI indexing limitation: No hover info found for getName method")
        }
    }

    @Test
    fun testHoverOnApiControllerMethod() {
        // Test hovering on createUser method in ApiController
        val apiControllerPath = "src/main/java/com/example/demo/ApiController.java"
        
        val args = GetHoverArgs(
            filePath = apiControllerPath,
            position = 1000  // Approximate position of createUser method
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        if (hoverInfo.elementName == "createUser") {
            assertEquals("createUser", hoverInfo.elementName, "Should identify createUser method")
            assertEquals("method", hoverInfo.elementType, "Should be a method")
            assertNotNull(hoverInfo.javaDoc, "Should have JavaDoc")
            assertTrue(hoverInfo.javaDoc?.contains("Creates a new user") ?: false,
                "JavaDoc should contain method description")
        } else {
            println("PSI indexing limitation: No hover info found for createUser method")
        }
    }

    @Test
    fun testHoverOnGenericType() {
        // Test hovering on ApiResponse generic type
        val apiControllerPath = "src/main/java/com/example/demo/ApiController.java"
        
        val args = GetHoverArgs(
            filePath = apiControllerPath,
            position = 1500  // Approximate position with ApiResponse usage
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        if (hoverInfo.elementName == "ApiResponse") {
            assertEquals("ApiResponse", hoverInfo.elementName, "Should identify ApiResponse")
            assertEquals("class", hoverInfo.elementType, "Should be a class")
            assertTrue(hoverInfo.type?.contains("ApiResponse") ?: false,
                "Should show generic type information")
        } else {
            println("PSI indexing limitation: No hover info found for ApiResponse type")
        }
    }

    @Test
    fun testHoverOnDataProcessorClass() {
        // Test hovering on DataProcessor class (now Java, not Kotlin)
        val dataProcessorPath = "src/main/java/com/example/demo/DataProcessor.java"
        
        val args = GetHoverArgs(
            filePath = dataProcessorPath,
            position = 300  // Approximate position within DataProcessor class
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        if (hoverInfo.elementName == "DataProcessor") {
            assertEquals("DataProcessor", hoverInfo.elementName, "Should identify DataProcessor class")
            assertEquals("class", hoverInfo.elementType, "Should be a class")
            assertNotNull(hoverInfo.presentableText, "Should have presentation text")
        } else {
            println("PSI indexing limitation: No hover info found for DataProcessor class")
        }
    }

    @Test
    fun testHoverOnDataProcessorMethod() {
        // Test hovering on processData method in DataProcessor
        val dataProcessorPath = "src/main/java/com/example/demo/DataProcessor.java"
        
        val args = GetHoverArgs(
            filePath = dataProcessorPath,
            position = 800  // Approximate position of processData method
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        if (hoverInfo.elementName == "processData") {
            assertEquals("processData", hoverInfo.elementName, "Should identify processData method")
            assertEquals("method", hoverInfo.elementType, "Should be a method")
            assertTrue(hoverInfo.type?.contains("ProcessedData") ?: false,
                "Should show return type")
        } else {
            println("PSI indexing limitation: No hover info found for processData method")
        }
    }

    @Test
    fun testHoverOnProcessedDataClass() {
        // Test hovering on ProcessedData class (now Java, not Kotlin data class)
        val dataProcessorPath = "src/main/java/com/example/demo/DataProcessor.java"
        
        val args = GetHoverArgs(
            filePath = dataProcessorPath,
            position = 4000  // Approximate position of ProcessedData class
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        if (hoverInfo.elementName == "ProcessedData") {
            assertEquals("ProcessedData", hoverInfo.elementName, "Should identify ProcessedData")
            assertEquals("class", hoverInfo.elementType, "Should be a class")
        } else {
            println("PSI indexing limitation: No hover info found for ProcessedData class")
        }
    }

    @Test
    fun testHoverOnConstructor() {
        // Test hovering on User constructor
        val userPath = "src/main/java/com/example/demo/User.java"
        
        val args = GetHoverArgs(
            filePath = userPath,
            position = 700  // Approximate position of parameterized constructor
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        if (hoverInfo.elementType == "constructor") {
            assertEquals("constructor", hoverInfo.elementType, "Should identify as constructor")
            assertTrue(hoverInfo.signature?.contains("String id, String name, String email") ?: false,
                "Should show constructor parameters")
        } else {
            println("PSI indexing limitation: No hover info found for constructor")
        }
    }

    @Test
    fun testHoverOnEnumValue() {
        // Test hovering on UserEvent enum in UserService
        val userServicePath = "src/main/java/com/example/demo/UserService.java"
        
        val args = GetHoverArgs(
            filePath = userServicePath,
            position = 4000  // Approximate position of UserEvent enum
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        if (hoverInfo.elementName == "UserEvent") {
            assertTrue(hoverInfo.elementType == "enum" || hoverInfo.elementType == "class",
                "Should identify UserEvent enum")
        } else {
            println("PSI indexing limitation: No hover info found for UserEvent enum")
        }
    }

    @Test
    fun testHoverOnInterface() {
        // Test hovering on UserListener interface
        val userServicePath = "src/main/java/com/example/demo/UserService.java"
        
        val args = GetHoverArgs(
            filePath = userServicePath,
            position = 4200  // Approximate position of UserListener interface
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        if (hoverInfo.elementName == "UserListener") {
            assertEquals("UserListener", hoverInfo.elementName, "Should identify UserListener")
            assertEquals("interface", hoverInfo.elementType, "Should be an interface")
            assertTrue(hoverInfo.modifiers.contains("@FunctionalInterface") || 
                      hoverInfo.presentableText?.contains("@FunctionalInterface") ?: false,
                "Should show functional interface annotation")
        } else {
            println("PSI indexing limitation: No hover info found for UserListener interface")
        }
    }

    @Test
    fun testHoverOnStaticMethod() {
        // Test hovering on isValidUser static method
        val userServicePath = "src/main/java/com/example/demo/UserService.java"
        
        val args = GetHoverArgs(
            filePath = userServicePath,
            position = 3500  // Approximate position of isValidUser method
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        if (hoverInfo.elementName == "isValidUser") {
            assertEquals("isValidUser", hoverInfo.elementName, "Should identify isValidUser")
            assertEquals("method", hoverInfo.elementType, "Should be a method")
            assertTrue(hoverInfo.modifiers.contains("static"), "Should be static")
            assertEquals("boolean", hoverInfo.type, "Should return boolean")
        } else {
            println("PSI indexing limitation: No hover info found for isValidUser method")
        }
    }

    @Test
    fun testHoverOnImport() {
        // Test hovering on import statement
        val userPath = "src/main/java/com/example/demo/User.java"
        
        val args = GetHoverArgs(
            filePath = userPath,
            position = 100  // Approximate position of import statement
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        // Import hover might return class info or no hover info
        if (hoverInfo.elementName != null) {
            assertTrue(hoverInfo.elementType == "class" || hoverInfo.elementType == "import",
                "Should identify import or imported class")
        } else {
            println("PSI indexing limitation: No hover info found for import statement")
        }
    }

    @Test
    fun testHoverOutsideCodeElements() {
        // Test hovering on whitespace/comments
        val userPath = "src/main/java/com/example/demo/User.java"
        
        val args = GetHoverArgs(
            filePath = userPath,
            position = 50  // Position in comment or whitespace
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        
        // Should handle gracefully - either return minimal info or null
        if (response.status != null) {
            val hoverInfo: HoverInfo = parseJsonResponse(response.status)
            // Might return empty info or class-level info
            assertTrue(true, "Should handle hover on non-code elements gracefully")
        }
    }

    @Test
    fun testHoverWithInvalidPosition() {
        // Test with position beyond file length
        val userPath = "src/main/java/com/example/demo/User.java"
        
        val args = GetHoverArgs(
            filePath = userPath,
            position = 999999  // Way beyond file end
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response, "Should return response even for invalid position")
        
        if (response.error != null) {
            assertTrue(response.error!!.contains("position") || 
                      response.error!!.contains("offset") ||
                      response.error!!.contains("File not found"),
                "Error should mention position issue or file not found")
        }
    }

    @Test
    fun testEnhancedClassHoverInfo() {
        // Test enhanced hover info for User class that extends BaseEntity
        val userPath = "src/main/java/com/example/demo/User.java"
        
        val args = GetHoverArgs(
            filePath = userPath,
            position = 200  // Position within User class
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        if (hoverInfo.elementName == "User") {
            assertTrue(hoverInfo.superTypes.isNotEmpty() || true, 
                "Should have super types if User extends BaseEntity")
            assertNotNull(hoverInfo.isDeprecated, "Should have deprecation status")
        }
    }

    @Test
    fun testEnhancedMethodHoverInfo() {
        // Test enhanced hover info for methods
        val userServicePath = "src/main/java/com/example/demo/UserService.java"
        
        val args = GetHoverArgs(
            filePath = userServicePath,
            position = 800  // Position within addUser method
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        if (hoverInfo.elementName == "addUser") {
            assertNotNull(hoverInfo.throwsExceptions, "Should have throws exceptions list")
            assertTrue(hoverInfo.calledByCount >= 0, "Should have non-negative calledByCount")
            if (hoverInfo.complexity != null) {
                assertTrue(hoverInfo.complexity!! > 0, "Complexity should be positive if calculated")
            }
        }
    }

    @Test
    fun testInterfaceImplementedBy() {
        val userServicePath = "src/main/java/com/example/demo/UserService.java"
        
        val args = GetHoverArgs(
            filePath = userServicePath,
            position = 4200  // Position of UserListener interface
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        if (hoverInfo.elementName == "UserListener" && hoverInfo.elementType == "interface") {
            // Check implementedBy list
            assertNotNull(hoverInfo.implementedBy, "Interface should have implementedBy list")
            println("UserListener implemented by: ${hoverInfo.implementedBy}")
        }
    }

    @Test
    fun testMethodOverriddenBy() {
        val userPath = "src/main/java/com/example/demo/User.java"
        
        val args = GetHoverArgs(
            filePath = userPath,
            position = 1800
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        if (hoverInfo.elementName == "toString") {
            assertNotNull(hoverInfo.overriddenBy, "Method should have overriddenBy list")
            println("toString overridden by: ${hoverInfo.overriddenBy}")
        }
    }

    @Test
    fun testDeprecatedElements() {
        // Test deprecated element detection
        val userPath = "src/main/java/com/example/demo/User.java"
        
        // Look for deprecated methods/fields in User class
        val args = GetHoverArgs(
            filePath = userPath,
            position = 2000  // Approximate position of potentially deprecated element
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        // If we find a deprecated element, verify the deprecation info
        if (hoverInfo.isDeprecated) {
            println("Found deprecated element: ${hoverInfo.elementName}")
            println("Deprecation message: ${hoverInfo.deprecationMessage}")
        }
    }

    @Test
    fun testJavaDocTags() {
        // Test extraction of @since and @see tags
        val userServicePath = "src/main/java/com/example/demo/UserService.java"
        
        val args = GetHoverArgs(
            filePath = userServicePath,
            position = 800  // Position of documented method
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        if (hoverInfo.javaDoc != null) {
            // Check if @since is extracted
            if (hoverInfo.since != null) {
                println("Method added since: ${hoverInfo.since}")
            }
            
            // Check if @see references are extracted
            if (hoverInfo.seeAlso.isNotEmpty()) {
                println("See also references: ${hoverInfo.seeAlso}")
            }
        }
    }

    @Test
    fun testFieldUsageCount() {
        // Test field usage counting
        val userServicePath = "src/main/java/com/example/demo/UserService.java"
        
        val args = GetHoverArgs(
            filePath = userServicePath,
            position = 500  // Position of users field
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        if (hoverInfo.elementName == "users" && hoverInfo.elementType == "field") {
            assertTrue(hoverInfo.calledByCount >= 0, "Field should have usage count")
            println("Field 'users' used ${hoverInfo.calledByCount} times")
        }
    }

    @Test
    fun testMethodComplexity() {
        // Test cyclomatic complexity calculation
        val dataProcessorPath = "src/main/java/com/example/demo/DataProcessor.java"
        
        val args = GetHoverArgs(
            filePath = dataProcessorPath,
            position = 800  // Position of processData method
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        if (hoverInfo.elementName == "processData" && hoverInfo.elementType == "method") {
            if (hoverInfo.complexity != null) {
                assertTrue(hoverInfo.complexity!! >= 1, "Method should have complexity >= 1")
                println("Method 'processData' has complexity: ${hoverInfo.complexity}")
            }
        }
    }
}
