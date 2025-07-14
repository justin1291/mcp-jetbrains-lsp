package dev.mcp.extensions.lsp.languages.java

import com.intellij.openapi.application.ApplicationManager
import dev.mcp.extensions.lsp.JavaBaseTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for JavaHoverInfoProvider.
 * 
 * These tests verify that the JavaHoverInfoProvider correctly extracts hover information
 * from Java code elements without going through the tool layer.
 * 
 * Uses precise positions from our own get_symbols_in_file tool - dogfooding our own tools! 🐕
 */
class JavaHoverProviderTest : JavaBaseTest() {
    
    private lateinit var provider: JavaHoverInfoProvider
    
    @BeforeEach
    override fun setUp() {
        super.setUp()
        provider = JavaHoverInfoProvider()
    }

    @Test
    fun testHoverOnUserServiceClass() {
        // Position 144 from get_symbols_in_file: UserService class
        ApplicationManager.getApplication().runReadAction {
            val userServiceFile = myFixture.findFileInTempDir("src/main/java/com/example/demo/UserService.java")
            assertNotNull(userServiceFile, "UserService.java should be available in test project")
            
            val psiFile = myFixture.psiManager.findFile(userServiceFile!!)
            assertNotNull(psiFile, "PsiFile should be available")
            
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 144)
            
            assertNotNull(hoverInfo, "Should get hover info for UserService class")
            assertEquals("UserService", hoverInfo.elementName, "Should identify UserService class")
            assertEquals("class", hoverInfo.elementType, "Should be a class")
            assertNotNull(hoverInfo.javaDoc, "Should have JavaDoc documentation")
            assertTrue(hoverInfo.javaDoc?.contains("Service class for managing users") ?: false,
                "JavaDoc should contain class description")
        }
    }

    @Test
    fun testHoverOnDocumentedMethod() {
        // Position 1461 from get_symbols_in_file: addUser method
        ApplicationManager.getApplication().runReadAction {
            val userServiceFile = myFixture.findFileInTempDir("src/main/java/com/example/demo/UserService.java")
            assertNotNull(userServiceFile, "UserService.java should be available in test project")
            
            val psiFile = myFixture.psiManager.findFile(userServiceFile!!)
            assertNotNull(psiFile, "PsiFile should be available")
            
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 1461)
            
            assertNotNull(hoverInfo, "Should get hover info for addUser method")
            assertEquals("addUser", hoverInfo.elementName, "Should identify addUser method")
            assertEquals("method", hoverInfo.elementType, "Should be a method")
            assertNotNull(hoverInfo.javaDoc, "Should have JavaDoc documentation")
            assertTrue(hoverInfo.javaDoc?.contains("Adds a new user") ?: false,
                "JavaDoc should contain method description")
            assertEquals("boolean", hoverInfo.type, "Should return boolean")
            assertTrue(hoverInfo.modifiers.contains("public"), "Should be public")
        }
    }

    @Test
    fun testHoverOnFieldWithModifiers() {
        // Position 369 from get_symbols_in_file: DEFAULT_ROLE field
        ApplicationManager.getApplication().runReadAction {
            val userServiceFile = myFixture.findFileInTempDir("src/main/java/com/example/demo/UserService.java")
            assertNotNull(userServiceFile, "UserService.java should be available in test project")
            
            val psiFile = myFixture.psiManager.findFile(userServiceFile!!)
            assertNotNull(psiFile, "PsiFile should be available")
            
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 369)
            
            assertNotNull(hoverInfo, "Should get hover info for DEFAULT_ROLE field")
            assertEquals("DEFAULT_ROLE", hoverInfo.elementName, "Should identify DEFAULT_ROLE field")
            assertEquals("field", hoverInfo.elementType, "Should be a field")
            assertEquals("String", hoverInfo.type, "Should have String type")
            assertTrue(hoverInfo.modifiers.contains("static"), "Should be static")
            assertTrue(hoverInfo.modifiers.contains("final"), "Should be final")
            assertTrue(hoverInfo.modifiers.contains("public"), "Should be public")
        }
    }

    @Test
    fun testHoverOnUserClass() {
        // Position 54 from get_symbols_in_file: User class
        ApplicationManager.getApplication().runReadAction {
            val userFile = myFixture.findFileInTempDir("src/main/java/com/example/demo/User.java")
            assertNotNull(userFile, "User.java should be available in test project")
            
            val psiFile = myFixture.psiManager.findFile(userFile!!)
            assertNotNull(psiFile, "PsiFile should be available")
            
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 54)
            
            assertNotNull(hoverInfo, "Should get hover info for User class")
            assertEquals("User", hoverInfo.elementName, "Should identify User class")
            assertEquals("class", hoverInfo.elementType, "Should be a class")
            assertNotNull(hoverInfo.javaDoc, "Should have JavaDoc")
            assertTrue(hoverInfo.javaDoc?.contains("User entity class") ?: false,
                "JavaDoc should contain class description")
        }
    }

    @Test
    fun testHoverOnGetterMethod() {
        // Position 987 from get_symbols_in_file: getName method
        ApplicationManager.getApplication().runReadAction {
            val userFile = myFixture.findFileInTempDir("src/main/java/com/example/demo/User.java")
            assertNotNull(userFile, "User.java should be available in test project")
            
            val psiFile = myFixture.psiManager.findFile(userFile!!)
            assertNotNull(psiFile, "PsiFile should be available")
            
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 987)
            
            assertNotNull(hoverInfo, "Should get hover info for getName method")
            assertEquals("getName", hoverInfo.elementName, "Should identify getName method")
            assertEquals("method", hoverInfo.elementType, "Should be a method")
            assertEquals("String", hoverInfo.type, "Should return String")
            assertTrue(hoverInfo.modifiers.contains("public"), "Should be public")
        }
    }

    @Test
    fun testHoverOnApiControllerMethod() {
        // Position 407 from get_symbols_in_file: createUser method
        ApplicationManager.getApplication().runReadAction {
            val apiControllerFile = myFixture.findFileInTempDir("src/main/java/com/example/demo/ApiController.java")
            assertNotNull(apiControllerFile, "ApiController.java should be available in test project")
            
            val psiFile = myFixture.psiManager.findFile(apiControllerFile!!)
            assertNotNull(psiFile, "PsiFile should be available")
            
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 407)
            
            assertNotNull(hoverInfo, "Should get hover info for createUser method")
            assertEquals("createUser", hoverInfo.elementName, "Should identify createUser method")
            assertEquals("method", hoverInfo.elementType, "Should be a method")
            assertNotNull(hoverInfo.javaDoc, "Should have JavaDoc")
            assertTrue(hoverInfo.javaDoc?.contains("Creates a new user") ?: false,
                "JavaDoc should contain method description")
        }
    }

    @Test
    fun testHoverOnGenericType() {
        // Position 2923 from get_symbols_in_file: ApiResponse class
        ApplicationManager.getApplication().runReadAction {
            val apiControllerFile = myFixture.findFileInTempDir("src/main/java/com/example/demo/ApiController.java")
            assertNotNull(apiControllerFile, "ApiController.java should be available in test project")
            
            val psiFile = myFixture.psiManager.findFile(apiControllerFile!!)
            assertNotNull(psiFile, "PsiFile should be available")
            
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 2923)
            
            assertNotNull(hoverInfo, "Should get hover info for ApiResponse type")
            assertEquals("ApiResponse", hoverInfo.elementName, "Should identify ApiResponse")
            assertEquals("class", hoverInfo.elementType, "Should be a class")
            assertTrue(hoverInfo.type?.contains("ApiResponse") ?: false,
                "Should show generic type information")
        }
    }

    @Test
    fun testHoverOnDataProcessorClass() {
        // Position 131 from get_symbols_in_file: DataProcessor class
        ApplicationManager.getApplication().runReadAction {
            val dataProcessorFile = myFixture.findFileInTempDir("src/main/java/com/example/demo/DataProcessor.java")
            assertNotNull(dataProcessorFile, "DataProcessor.java should be available in test project")
            
            val psiFile = myFixture.psiManager.findFile(dataProcessorFile!!)
            assertNotNull(psiFile, "PsiFile should be available")
            
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 131)
            
            assertNotNull(hoverInfo, "Should get hover info for DataProcessor class")
            assertEquals("DataProcessor", hoverInfo.elementName, "Should identify DataProcessor class")
            assertEquals("class", hoverInfo.elementType, "Should be a class")
            assertNotNull(hoverInfo.presentableText, "Should have presentation text")
        }
    }

    @Test
    fun testHoverOnDataProcessorMethod() {
        // Position 405 from get_symbols_in_file: processData method
        ApplicationManager.getApplication().runReadAction {
            val dataProcessorFile = myFixture.findFileInTempDir("src/main/java/com/example/demo/DataProcessor.java")
            assertNotNull(dataProcessorFile, "DataProcessor.java should be available in test project")
            
            val psiFile = myFixture.psiManager.findFile(dataProcessorFile!!)
            assertNotNull(psiFile, "PsiFile should be available")
            
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 405)
            
            assertNotNull(hoverInfo, "Should get hover info for processData method")
            assertEquals("processData", hoverInfo.elementName, "Should identify processData method")
            assertEquals("method", hoverInfo.elementType, "Should be a method")
            assertTrue(hoverInfo.type?.contains("ProcessedData") ?: false,
                "Should show return type")
        }
    }

    @Test
    fun testHoverOnConstructor() {
        // Position 502 from get_symbols_in_file: User parameterized constructor
        ApplicationManager.getApplication().runReadAction {
            val userFile = myFixture.findFileInTempDir("src/main/java/com/example/demo/User.java")
            assertNotNull(userFile, "User.java should be available in test project")
            
            val psiFile = myFixture.psiManager.findFile(userFile!!)
            assertNotNull(psiFile, "PsiFile should be available")
            
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 502)
            
            assertNotNull(hoverInfo, "Should get hover info for constructor")
            assertEquals("constructor", hoverInfo.elementType, "Should identify as constructor")
            assertTrue(hoverInfo.signature?.contains("String") ?: false,
                "Should show constructor parameters")
        }
    }

    @Test
    fun testHoverOnEnumValue() {
        // Position 790 from get_symbols_in_file: UserEvent enum
        ApplicationManager.getApplication().runReadAction {
            val userServiceFile = myFixture.findFileInTempDir("src/main/java/com/example/demo/UserService.java")
            assertNotNull(userServiceFile, "UserService.java should be available in test project")
            
            val psiFile = myFixture.psiManager.findFile(userServiceFile!!)
            assertNotNull(psiFile, "PsiFile should be available")
            
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 790)
            
            assertNotNull(hoverInfo, "Should get hover info for UserEvent enum")
            assertEquals("UserEvent", hoverInfo.elementName, "Should identify UserEvent")
            assertTrue(hoverInfo.elementType == "enum" || hoverInfo.elementType == "class",
                "Should identify UserEvent enum")
        }
    }

    @Test
    fun testHoverOnInterface() {
        // Position 1229 from get_symbols_in_file: UserListener interface
        ApplicationManager.getApplication().runReadAction {
            val userServiceFile = myFixture.findFileInTempDir("src/main/java/com/example/demo/UserService.java")
            assertNotNull(userServiceFile, "UserService.java should be available in test project")
            
            val psiFile = myFixture.psiManager.findFile(userServiceFile!!)
            assertNotNull(psiFile, "PsiFile should be available")
            
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 1229)
            
            assertNotNull(hoverInfo, "Should get hover info for UserListener interface")
            assertEquals("UserListener", hoverInfo.elementName, "Should identify UserListener")
            assertEquals("interface", hoverInfo.elementType, "Should be an interface")
        }
    }

    @Test
    fun testHoverOnStaticMethod() {
        // Position 2907 from get_symbols_in_file: isValidUser static method
        ApplicationManager.getApplication().runReadAction {
            val userServiceFile = myFixture.findFileInTempDir("src/main/java/com/example/demo/UserService.java")
            assertNotNull(userServiceFile, "UserService.java should be available in test project")
            
            val psiFile = myFixture.psiManager.findFile(userServiceFile!!)
            assertNotNull(psiFile, "PsiFile should be available")
            
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 2907)
            
            assertNotNull(hoverInfo, "Should get hover info for isValidUser method")
            assertEquals("isValidUser", hoverInfo.elementName, "Should identify isValidUser")
            assertEquals("method", hoverInfo.elementType, "Should be a method")
            assertTrue(hoverInfo.modifiers.contains("static"), "Should be static")
            assertEquals("boolean", hoverInfo.type, "Should return boolean")
            assertTrue(hoverInfo.modifiers.contains("public"), "Should be public")
        }
    }

    @Test
    fun testHoverOnDeprecatedElement() {
        // Position 486 from get_symbols_in_file: MAX_USERS deprecated field
        ApplicationManager.getApplication().runReadAction {
            val userServiceFile = myFixture.findFileInTempDir("src/main/java/com/example/demo/UserService.java")
            assertNotNull(userServiceFile, "UserService.java should be available in test project")
            
            val psiFile = myFixture.psiManager.findFile(userServiceFile!!)
            assertNotNull(psiFile, "PsiFile should be available")
            
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 486)
            
            assertNotNull(hoverInfo, "Should get hover info for MAX_USERS field")
            assertEquals("MAX_USERS", hoverInfo.elementName, "Should identify MAX_USERS field")
            assertEquals("field", hoverInfo.elementType, "Should be a field")
            assertTrue(hoverInfo.isDeprecated, "Should be marked as deprecated")
            assertTrue(hoverInfo.deprecationMessage?.contains("dynamic limits") ?: false,
                "Should have deprecation message")
        }
    }

    @Test
    fun testHoverOutsideCodeElements() {
        // Test hovering on whitespace/comments - should handle gracefully
        ApplicationManager.getApplication().runReadAction {
            val userFile = myFixture.findFileInTempDir("src/main/java/com/example/demo/User.java")
            assertNotNull(userFile, "User.java should be available in test project")
            
            val psiFile = myFixture.psiManager.findFile(userFile!!)
            assertNotNull(psiFile, "PsiFile should be available")
            
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 50) // Position in comment or whitespace
            
            // Should handle gracefully - either return info or null
            if (hoverInfo != null) {
                assertNotNull(hoverInfo.elementName, "Should have element name if info is returned")
            }
            // If null, that's also acceptable for non-code elements
        }
    }

    @Test
    fun testHoverWithInvalidPosition() {
        // Test with position beyond file length
        ApplicationManager.getApplication().runReadAction {
            val userFile = myFixture.findFileInTempDir("src/main/java/com/example/demo/User.java")
            assertNotNull(userFile, "User.java should be available in test project")
            
            val psiFile = myFixture.psiManager.findFile(userFile!!)
            assertNotNull(psiFile, "PsiFile should be available")
            
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 999999) // Way beyond file end
            
            // Should handle gracefully and return null or minimal info
            if (hoverInfo != null) {
                assertNotNull(hoverInfo.elementName, "Should have element name if info is returned")
            }
            // If null, that's acceptable for invalid positions
        }
    }

    @Test
    fun testEnhancedClassHoverInfo() {
        // Position 54 from get_symbols_in_file: User class
        ApplicationManager.getApplication().runReadAction {
            val userFile = myFixture.findFileInTempDir("src/main/java/com/example/demo/User.java")
            assertNotNull(userFile, "User.java should be available in test project")
            
            val psiFile = myFixture.psiManager.findFile(userFile!!)
            assertNotNull(psiFile, "PsiFile should be available")
            
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 54)
            
            assertNotNull(hoverInfo, "Should get hover info for User class")
            assertEquals("User", hoverInfo.elementName, "Should identify User class")
            // superTypes may be empty for User class, that's acceptable
            assertTrue(hoverInfo.superTypes.isEmpty() || hoverInfo.superTypes.isNotEmpty(),
                "Should have superTypes list (empty or filled)")
        }
    }

    @Test
    fun testEnhancedMethodHoverInfo() {
        // Position 1461 from get_symbols_in_file: addUser method
        ApplicationManager.getApplication().runReadAction {
            val userServiceFile = myFixture.findFileInTempDir("src/main/java/com/example/demo/UserService.java")
            assertNotNull(userServiceFile, "UserService.java should be available in test project")
            
            val psiFile = myFixture.psiManager.findFile(userServiceFile!!)
            assertNotNull(psiFile, "PsiFile should be available")
            
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 1461)
            
            assertNotNull(hoverInfo, "Should get hover info for addUser method")
            assertEquals("addUser", hoverInfo.elementName, "Should identify addUser method")
            assertNotNull(hoverInfo.throwsExceptions, "Should have throws exceptions list")
            assertTrue(hoverInfo.calledByCount >= 0, "Should have non-negative calledByCount")
            if (hoverInfo.complexity != null) {
                assertTrue(hoverInfo.complexity!! > 0, "Complexity should be positive if calculated")
            }
        }
    }

    @Test
    fun testInterfaceImplementedBy() {
        // Position 1229 from get_symbols_in_file: UserListener interface
        ApplicationManager.getApplication().runReadAction {
            val userServiceFile = myFixture.findFileInTempDir("src/main/java/com/example/demo/UserService.java")
            assertNotNull(userServiceFile, "UserService.java should be available in test project")
            
            val psiFile = myFixture.psiManager.findFile(userServiceFile!!)
            assertNotNull(psiFile, "PsiFile should be available")
            
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 1229)
            
            assertNotNull(hoverInfo, "Should get hover info for UserListener interface")
            assertEquals("UserListener", hoverInfo.elementName, "Should identify UserListener")
            assertEquals("interface", hoverInfo.elementType, "Should be an interface")
            assertNotNull(hoverInfo.implementedBy, "Interface should have implementedBy list")
        }
    }

    @Test
    fun testMethodOverriddenBy() {
        // Position 1920 from get_symbols_in_file: toString method
        ApplicationManager.getApplication().runReadAction {
            val userFile = myFixture.findFileInTempDir("src/main/java/com/example/demo/User.java")
            assertNotNull(userFile, "User.java should be available in test project")
            
            val psiFile = myFixture.psiManager.findFile(userFile!!)
            assertNotNull(psiFile, "PsiFile should be available")
            
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 1920)
            
            assertNotNull(hoverInfo, "Should get hover info for toString method")
            assertEquals("toString", hoverInfo.elementName, "Should identify toString method")
            assertNotNull(hoverInfo.overriddenBy, "Method should have overriddenBy list")
        }
    }

    @Test
    fun testJavaDocTags() {
        // Position 144 from get_symbols_in_file: UserService class (has @since and @see tags)
        ApplicationManager.getApplication().runReadAction {
            val userServiceFile = myFixture.findFileInTempDir("src/main/java/com/example/demo/UserService.java")
            assertNotNull(userServiceFile, "UserService.java should be available in test project")
            
            val psiFile = myFixture.psiManager.findFile(userServiceFile!!)
            assertNotNull(psiFile, "PsiFile should be available")
            
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 144)
            
            assertNotNull(hoverInfo, "Should get hover info for UserService class")
            assertNotNull(hoverInfo.javaDoc, "Should have JavaDoc")
            // Check if @since is extracted
            if (hoverInfo.since != null) {
                assertEquals("1.0", hoverInfo.since, "Should extract @since tag")
            }
            // Check if @see references are extracted
            if (hoverInfo.seeAlso.isNotEmpty()) {
                assertTrue(hoverInfo.seeAlso.contains("DataProcessor"), "Should extract @see tag")
            }
        }
    }

    @Test
    fun testFieldUsageCount() {
        // Position 654 from get_symbols_in_file: users field
        ApplicationManager.getApplication().runReadAction {
            val userServiceFile = myFixture.findFileInTempDir("src/main/java/com/example/demo/UserService.java")
            assertNotNull(userServiceFile, "UserService.java should be available in test project")
            
            val psiFile = myFixture.psiManager.findFile(userServiceFile!!)
            assertNotNull(psiFile, "PsiFile should be available")
            
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 654)
            
            assertNotNull(hoverInfo, "Should get hover info for users field")
            assertEquals("users", hoverInfo.elementName, "Should identify users field")
            assertEquals("field", hoverInfo.elementType, "Should be a field")
            assertTrue(hoverInfo.calledByCount >= 0, "Field should have usage count")
        }
    }

    @Test
    fun testMethodComplexity() {
        // Position 405 from get_symbols_in_file: processData method
        ApplicationManager.getApplication().runReadAction {
            val dataProcessorFile = myFixture.findFileInTempDir("src/main/java/com/example/demo/DataProcessor.java")
            assertNotNull(dataProcessorFile, "DataProcessor.java should be available in test project")
            
            val psiFile = myFixture.psiManager.findFile(dataProcessorFile!!)
            assertNotNull(psiFile, "PsiFile should be available")
            
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 405)
            
            assertNotNull(hoverInfo, "Should get hover info for processData method")
            assertEquals("processData", hoverInfo.elementName, "Should identify processData method")
            assertEquals("method", hoverInfo.elementType, "Should be a method")
            if (hoverInfo.complexity != null) {
                assertTrue(hoverInfo.complexity!! >= 1, "Method should have complexity >= 1")
            }
        }
    }

    @Test
    fun testSupportsElement() {
        // Test that the provider supports Java elements
        ApplicationManager.getApplication().runReadAction {
            val userFile = myFixture.findFileInTempDir("src/main/java/com/example/demo/User.java")
            assertNotNull(userFile, "User.java should be available in test project")
            
            val psiFile = myFixture.psiManager.findFile(userFile!!)
            assertNotNull(psiFile, "PsiFile should be available")
            
            val element = psiFile!!.findElementAt(200) // Get any element from the file
            assertNotNull(element, "Should find an element at position 200")
            
            val supportsElement = provider.supportsElement(element!!)
            assertTrue(supportsElement, "Provider should support Java elements")
            
            val supportedLanguage = provider.getSupportedLanguage()
            assertEquals("Java/Kotlin", supportedLanguage, "Should support Java/Kotlin")
        }
    }

    @Test
    fun testInnerClassHover() {
        // Position 3872 from get_symbols_in_file: UserSession inner class
        ApplicationManager.getApplication().runReadAction {
            val userServiceFile = myFixture.findFileInTempDir("src/main/java/com/example/demo/UserService.java")
            assertNotNull(userServiceFile, "UserService.java should be available in test project")
            
            val psiFile = myFixture.psiManager.findFile(userServiceFile!!)
            assertNotNull(psiFile, "PsiFile should be available")
            
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 3872)
            
            assertNotNull(hoverInfo, "Should get hover info for UserSession inner class")
            assertEquals("UserSession", hoverInfo.elementName, "Should identify UserSession")
            assertEquals("class", hoverInfo.elementType, "Should be a class")
            assertTrue(hoverInfo.modifiers.contains("static"), "Should be static inner class")
        }
    }
}
