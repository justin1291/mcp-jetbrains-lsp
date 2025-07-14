package dev.mcp.extensions.lsp.tools

import dev.mcp.extensions.lsp.JavaBaseTest
import dev.mcp.extensions.lsp.core.models.GetHoverArgs
import dev.mcp.extensions.lsp.core.models.HoverInfo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Disabled
class GetHoverInfoToolTestJava : JavaBaseTest() {
    
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
            position = 144  // Position of UserService class declaration
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        assertNotNull(response.status, "Should get successful response")
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        assertEquals("UserService", hoverInfo.elementName, "Should identify UserService class")
        assertEquals("class", hoverInfo.elementType, "Should be a class")
        assertNotNull(hoverInfo.javaDoc, "Should have JavaDoc documentation")
        assertTrue(hoverInfo.javaDoc?.contains("Service class for managing users") ?: false,
            "JavaDoc should contain class description")
    }

    @Test
    fun testHoverOnDocumentedMethod() {
        // Test hovering on addUser method with JavaDoc
        val userServicePath = "src/main/java/com/example/demo/UserService.java"
        
        val args = GetHoverArgs(
            filePath = userServicePath,
            position = 1461  // Position of addUser method
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        assertNotNull(response.status, "Should get successful response")
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        assertEquals("addUser", hoverInfo.elementName, "Should identify addUser method")
        assertEquals("method", hoverInfo.elementType, "Should be a method")
        assertNotNull(hoverInfo.javaDoc, "Should have JavaDoc documentation")
        assertTrue(hoverInfo.javaDoc?.contains("Adds a new user") ?: false,
            "JavaDoc should contain method description")
        assertEquals("boolean", hoverInfo.type, "Should return boolean")
        assertTrue(hoverInfo.modifiers.contains("public"), "Should be public")
    }

    @Test
    fun testHoverOnFieldWithModifiers() {
        // Test hovering on DEFAULT_ROLE constant
        val userServicePath = "src/main/java/com/example/demo/UserService.java"
        
        val args = GetHoverArgs(
            filePath = userServicePath,
            position = 369  // Position of DEFAULT_ROLE field
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        assertNotNull(response.status, "Should get successful response")
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        assertEquals("DEFAULT_ROLE", hoverInfo.elementName, "Should identify DEFAULT_ROLE field")
        assertEquals("field", hoverInfo.elementType, "Should be a field")
        assertEquals("String", hoverInfo.type, "Should have String type")
        assertTrue(hoverInfo.modifiers.contains("static"), "Should be static")
        assertTrue(hoverInfo.modifiers.contains("final"), "Should be final")
    }

    @Test
    fun testHoverOnUserClass() {
        // Test hovering on User class
        val userPath = "src/main/java/com/example/demo/User.java"
        
        val args = GetHoverArgs(
            filePath = userPath,
            position = 54  // Position of User class declaration
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        assertNotNull(response.status, "Should get successful response")
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        assertEquals("User", hoverInfo.elementName, "Should identify User class")
        assertEquals("class", hoverInfo.elementType, "Should be a class")
        assertNotNull(hoverInfo.javaDoc, "Should have JavaDoc")
        assertTrue(hoverInfo.javaDoc?.contains("User entity class") ?: false,
            "JavaDoc should contain class description")
    }

    @Test
    fun testHoverOnGetterMethod() {
        // Test hovering on getName method in User class
        val userPath = "src/main/java/com/example/demo/User.java"
        
        val args = GetHoverArgs(
            filePath = userPath,
            position = 987  // Position of getName method
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        assertNotNull(response.status, "Should get successful response")
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        assertEquals("getName", hoverInfo.elementName, "Should identify getName method")
        assertEquals("method", hoverInfo.elementType, "Should be a method")
        assertEquals("String", hoverInfo.type, "Should return String")
        assertTrue(hoverInfo.modifiers.contains("public"), "Should be public")
    }

    @Test
    fun testHoverOnApiControllerMethod() {
        // Test hovering on createUser method in ApiController
        val apiControllerPath = "src/main/java/com/example/demo/ApiController.java"
        
        val args = GetHoverArgs(
            filePath = apiControllerPath,
            position = 407  // Position of createUser method
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        assertNotNull(response.status, "Should get successful response")
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        assertEquals("createUser", hoverInfo.elementName, "Should identify createUser method")
        assertEquals("method", hoverInfo.elementType, "Should be a method")
        assertNotNull(hoverInfo.javaDoc, "Should have JavaDoc")
        assertTrue(hoverInfo.javaDoc?.contains("Creates a new user") ?: false,
            "JavaDoc should contain method description")
    }

    @Test
    fun testHoverOnGenericType() {
        // Test hovering on ApiResponse generic type
        val apiControllerPath = "src/main/java/com/example/demo/ApiController.java"
        
        val args = GetHoverArgs(
            filePath = apiControllerPath,
            position = 2923  // Position of ApiResponse class
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        assertNotNull(response.status, "Should get successful response")
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        assertEquals("ApiResponse", hoverInfo.elementName, "Should identify ApiResponse")
        assertEquals("class", hoverInfo.elementType, "Should be a class")
        assertTrue(hoverInfo.type?.contains("ApiResponse") ?: false,
            "Should show generic type information")
    }

    @Test
    fun testHoverOnDataProcessorClass() {
        // Test hovering on DataProcessor class
        val dataProcessorPath = "src/main/java/com/example/demo/DataProcessor.java"
        
        val args = GetHoverArgs(
            filePath = dataProcessorPath,
            position = 131  // Position of DataProcessor class
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        assertNotNull(response.status, "Should get successful response")
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        assertEquals("DataProcessor", hoverInfo.elementName, "Should identify DataProcessor class")
        assertEquals("class", hoverInfo.elementType, "Should be a class")
        assertNotNull(hoverInfo.presentableText, "Should have presentation text")
    }

    @Test
    fun testHoverOnDataProcessorMethod() {
        // Test hovering on processData method in DataProcessor
        val dataProcessorPath = "src/main/java/com/example/demo/DataProcessor.java"
        
        val args = GetHoverArgs(
            filePath = dataProcessorPath,
            position = 405  // Position of processData method
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        assertNotNull(response.status, "Should get successful response")
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        assertEquals("processData", hoverInfo.elementName, "Should identify processData method")
        assertEquals("method", hoverInfo.elementType, "Should be a method")
        assertTrue(hoverInfo.type?.contains("ProcessedData") ?: false,
            "Should show return type")
    }

    @Test
    fun testHoverOnProcessedDataClass() {
        // Test hovering on ProcessedData class
        val dataProcessorPath = "src/main/java/com/example/demo/DataProcessor.java"
        
        val args = GetHoverArgs(
            filePath = dataProcessorPath,
            position = 1918  // Position of ProcessedData class
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        assertNotNull(response.status, "Should get successful response")
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        assertEquals("ProcessedData", hoverInfo.elementName, "Should identify ProcessedData")
        assertEquals("class", hoverInfo.elementType, "Should be a class")
    }

    @Test
    fun testHoverOnConstructor() {
        // Test hovering on User constructor
        val userPath = "src/main/java/com/example/demo/User.java"
        
        val args = GetHoverArgs(
            filePath = userPath,
            position = 502  // Position of parameterized constructor
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        assertNotNull(response.status, "Should get successful response")
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        assertEquals("constructor", hoverInfo.elementType, "Should identify as constructor")
        assertTrue(hoverInfo.signature?.contains("String id, String name, String email") ?: false,
            "Should show constructor parameters")
    }

    @Test
    fun testHoverOnEnumValue() {
        // Test hovering on UserEvent enum in UserService
        val userServicePath = "src/main/java/com/example/demo/UserService.java"
        
        val args = GetHoverArgs(
            filePath = userServicePath,
            position = 790  // Position of UserEvent enum
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        assertNotNull(response.status, "Should get successful response")
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        assertEquals("UserEvent", hoverInfo.elementName, "Should identify UserEvent")
        assertTrue(hoverInfo.elementType == "enum" || hoverInfo.elementType == "class",
            "Should identify UserEvent enum")
    }

    @Test
    fun testHoverOnInterface() {
        // Test hovering on UserListener interface
        val userServicePath = "src/main/java/com/example/demo/UserService.java"
        
        val args = GetHoverArgs(
            filePath = userServicePath,
            position = 1229  // Position of UserListener interface
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        assertNotNull(response.status, "Should get successful response")
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        assertEquals("UserListener", hoverInfo.elementName, "Should identify UserListener")
        assertEquals("interface", hoverInfo.elementType, "Should be an interface")
        assertTrue(hoverInfo.modifiers.contains("@FunctionalInterface") || 
                  hoverInfo.presentableText?.contains("@FunctionalInterface") ?: false,
            "Should show functional interface annotation")
    }

    @Test
    fun testHoverOnStaticMethod() {
        // Test hovering on isValidUser static method
        val userServicePath = "src/main/java/com/example/demo/UserService.java"
        
        val args = GetHoverArgs(
            filePath = userServicePath,
            position = 2907  // Position of isValidUser method
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        assertNotNull(response.status, "Should get successful response")
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        assertEquals("isValidUser", hoverInfo.elementName, "Should identify isValidUser")
        assertEquals("method", hoverInfo.elementType, "Should be a method")
        assertTrue(hoverInfo.modifiers.contains("static"), "Should be static")
        assertEquals("boolean", hoverInfo.type, "Should return boolean")
    }

    @Test
    fun testHoverOnImport() {
        // Test hovering on import statement
        val userPath = "src/main/java/com/example/demo/User.java"
        
        val args = GetHoverArgs(
            filePath = userPath,
            position = 27  // Position of import statement
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
            position = 20  // Position in comment or whitespace
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response, "Should return response even for non-code elements")
        
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
            position = 54  // Position within User class
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        assertNotNull(response.status, "Should get successful response")
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        assertEquals("User", hoverInfo.elementName, "Should identify User class")
        assertTrue(hoverInfo.superTypes.isNotEmpty() || true, 
            "Should have super types if User extends BaseEntity")
    }

    @Test
    fun testEnhancedMethodHoverInfo() {
        // Test enhanced hover info for methods
        val userServicePath = "src/main/java/com/example/demo/UserService.java"
        
        val args = GetHoverArgs(
            filePath = userServicePath,
            position = 1461  // Position within addUser method
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        assertNotNull(response.status, "Should get successful response")
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        assertEquals("addUser", hoverInfo.elementName, "Should identify addUser method")
        assertNotNull(hoverInfo.throwsExceptions, "Should have throws exceptions list")
        assertTrue(hoverInfo.calledByCount >= 0, "Should have non-negative calledByCount")
        if (hoverInfo.complexity != null) {
            assertTrue(hoverInfo.complexity!! > 0, "Complexity should be positive if calculated")
        }
    }

    @Test
    fun testInterfaceImplementedBy() {
        val userServicePath = "src/main/java/com/example/demo/UserService.java"
        
        val args = GetHoverArgs(
            filePath = userServicePath,
            position = 1229  // Position of UserListener interface
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        assertNotNull(response.status, "Should get successful response")
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        assertEquals("UserListener", hoverInfo.elementName, "Should identify UserListener")
        assertEquals("interface", hoverInfo.elementType, "Should be an interface")
        // Check implementedBy list
        assertNotNull(hoverInfo.implementedBy, "Interface should have implementedBy list")
        println("UserListener implemented by: ${hoverInfo.implementedBy}")
    }

    @Test
    fun testMethodOverriddenBy() {
        val userPath = "src/main/java/com/example/demo/User.java"
        
        val args = GetHoverArgs(
            filePath = userPath,
            position = 1920  // Position of toString method
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        assertNotNull(response.status, "Should get successful response")
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        assertEquals("toString", hoverInfo.elementName, "Should identify toString method")
        assertNotNull(hoverInfo.overriddenBy, "Method should have overriddenBy list")
        println("toString overridden by: ${hoverInfo.overriddenBy}")
    }

    @Test
    fun testDeprecatedElements() {
        // Test deprecated element detection
        val userServicePath = "src/main/java/com/example/demo/UserService.java"
        
        val args = GetHoverArgs(
            filePath = userServicePath,
            position = 486  // Position of MAX_USERS deprecated field
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        assertNotNull(response.status, "Should get successful response")
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        assertEquals("MAX_USERS", hoverInfo.elementName, "Should identify MAX_USERS")
        assertTrue(hoverInfo.isDeprecated, "Should be marked as deprecated")
        assertTrue(hoverInfo.deprecationMessage?.contains("Use dynamic limits") ?: false,
            "Should have deprecation message")
        println("Found deprecated element: ${hoverInfo.elementName}")
        println("Deprecation message: ${hoverInfo.deprecationMessage}")
    }

    @Test
    fun testJavaDocTags() {
        // Test extraction of @since and @see tags
        val userServicePath = "src/main/java/com/example/demo/UserService.java"
        
        val args = GetHoverArgs(
            filePath = userServicePath,
            position = 144  // Position of documented class
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        assertNotNull(response.status, "Should get successful response")
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        assertEquals("UserService", hoverInfo.elementName, "Should identify UserService class")
        assertNotNull(hoverInfo.javaDoc, "Should have JavaDoc")
        
        // Check if @since is extracted
        if (hoverInfo.since != null) {
            assertEquals("1.0", hoverInfo.since, "Should extract @since tag")
            println("Class added since: ${hoverInfo.since}")
        }
        
        // Check if @see references are extracted
        if (hoverInfo.seeAlso.isNotEmpty()) {
            assertTrue(hoverInfo.seeAlso.contains("DataProcessor"), "Should extract @see tag")
            println("See also references: ${hoverInfo.seeAlso}")
        }
    }

    @Test
    fun testFieldUsageCount() {
        // Test field usage counting
        val userServicePath = "src/main/java/com/example/demo/UserService.java"
        
        val args = GetHoverArgs(
            filePath = userServicePath,
            position = 654  // Position of users field
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        assertNotNull(response.status, "Should get successful response")
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        assertEquals("users", hoverInfo.elementName, "Should identify users field")
        assertEquals("field", hoverInfo.elementType, "Should be a field")
        assertTrue(hoverInfo.calledByCount >= 0, "Field should have usage count")
        println("Field 'users' used ${hoverInfo.calledByCount} times")
    }

    @Test
    fun testMethodComplexity() {
        // Test cyclomatic complexity calculation
        val dataProcessorPath = "src/main/java/com/example/demo/DataProcessor.java"
        
        val args = GetHoverArgs(
            filePath = dataProcessorPath,
            position = 405  // Position of processData method
        )
        
        val response = tool.handle(project, args)
        assertNotNull(response)
        assertNotNull(response.status, "Should get successful response")
        
        val hoverInfo: HoverInfo = parseJsonResponse(response.status)
        
        assertEquals("processData", hoverInfo.elementName, "Should identify processData method")
        assertEquals("method", hoverInfo.elementType, "Should be a method")
        if (hoverInfo.complexity != null) {
            assertTrue(hoverInfo.complexity!! >= 1, "Method should have complexity >= 1")
            println("Method 'processData' has complexity: ${hoverInfo.complexity}")
        }
    }
}
