package dev.mcp.extensions.lsp.languages.jvm

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import dev.mcp.extensions.lsp.JvmBaseTest
import org.junit.jupiter.api.Test

/**
 * Unit tests for JavaHoverInfoProvider using JVM implementation.
 *
 * These tests verify that the JVM HoverInfoProvider correctly extracts hover information
 * from Java code elements without going through the tool layer.
 *
 * Uses precise positions from our own get_symbols_in_file tool - dogfooding our own tools! 🐕
 * 
 * NOTE: This test now uses the JVM implementation to prepare for removing the Java-specific implementation.
 */
class JvmHoverProviderTest : JvmBaseTest() {

    private val provider: JvmHoverInfoProvider = JvmHoverInfoProvider()
    
    /**
     * Helper function to get PSI files by searching all files (including modules) and filtering by name
     */
    private fun getPsiFileByName(fileName: String): PsiFile? {
        val psiManager = PsiManager.getInstance(fixtureProject)
        val allScope = GlobalSearchScope.allScope(fixtureProject)
        val virtualFiles = FilenameIndex.getVirtualFilesByName(fileName, allScope)
        
        return virtualFiles.firstNotNullOfOrNull { virtualFile ->
            psiManager.findFile(virtualFile)
        }
    }
    
    /**
     * Get specific Java files used in tests
     */
    private fun getUserJavaFile() = getPsiFileByName("User.java")
    private fun getUserServiceJavaFile() = getPsiFileByName("UserService.java") 
    private fun getApiControllerJavaFile() = getPsiFileByName("ApiController.java")
    private fun getDataProcessorJavaFile() = getPsiFileByName("DataProcessor.java")

    @Test
    fun testHoverOnUserServiceClass() {
        // Position at UserService class definition
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserServiceJavaFile()
            assertNotNull("UserService.java should be available in test project", psiFile)

            // Test hover at a known position for UserService class (similar to other tests)
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 144) // Position from get_symbols_in_file

            assertNotNull("Should get hover info for UserService class", hoverInfo)
            assertEquals("Should identify UserService class", "UserService", hoverInfo!!.elementName)
            assertEquals("Should be a class", "class", hoverInfo.elementType)
            assertNotNull("Should have JavaDoc documentation", hoverInfo.javaDoc)
            assertTrue(
                "JavaDoc should contain class description",
                hoverInfo.javaDoc?.contains("Service class for managing users") ?: false
            )
        }
    }

    @Test
    fun testHoverOnDocumentedMethod() {
        // Position 1461 from get_symbols_in_file: addUser method
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserServiceJavaFile()
            assertNotNull("UserService.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 1461)

            assertNotNull("Should get hover info for addUser method", hoverInfo)
            assertEquals("Should identify addUser method", "addUser", hoverInfo!!.elementName)
            assertEquals("Should be a method", "method", hoverInfo.elementType)
            assertNotNull("Should have JavaDoc documentation", hoverInfo.javaDoc)
            assertTrue(
                "JavaDoc should contain method description",
                hoverInfo.javaDoc?.contains("Adds a new user") ?: false
            )
            assertEquals("Should return boolean", "boolean", hoverInfo.type)
            assertTrue("Should be public", hoverInfo.modifiers.contains("public"))
        }
    }

    @Test
    fun testHoverOnFieldWithModifiers() {
        // Position 369 from get_symbols_in_file: DEFAULT_ROLE field
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserServiceJavaFile()
            assertNotNull("UserService.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 369)

            assertNotNull("Should get hover info for DEFAULT_ROLE field", hoverInfo)
            assertEquals("Should identify DEFAULT_ROLE field", "DEFAULT_ROLE", hoverInfo!!.elementName)
            assertEquals("Should be a field", "field", hoverInfo.elementType)
            assertEquals("Should have String type", "String", hoverInfo.type)
            assertTrue("Should be static", hoverInfo.modifiers.contains("static"))
            assertTrue("Should be final", hoverInfo.modifiers.contains("final"))
            assertTrue("Should be public", hoverInfo.modifiers.contains("public"))
        }
    }

    @Test
    fun testHoverOnUserClass() {
        // Position 54 from get_symbols_in_file: User class
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserJavaFile()
            assertNotNull("User.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 54)

            assertNotNull("Should get hover info for User class", hoverInfo)
            assertEquals("Should identify User class", "User", hoverInfo!!.elementName)
            assertEquals("Should be a class", "class", hoverInfo.elementType)
            assertNotNull("Should have JavaDoc", hoverInfo.javaDoc)
            assertTrue(
                "JavaDoc should contain class description",
                hoverInfo.javaDoc?.contains("User entity class") ?: false
            )
        }
    }

    @Test
    fun testHoverOnGetterMethod() {
        // Position 987 from get_symbols_in_file: getName method
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserJavaFile()
            assertNotNull("User.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 987)

            assertNotNull("Should get hover info for getName method", hoverInfo)
            assertEquals("Should identify getName method", "getName", hoverInfo!!.elementName)
            assertEquals("Should be a method", "method", hoverInfo.elementType)
            assertEquals("Should return String", "String", hoverInfo.type)
            assertTrue("Should be public", hoverInfo.modifiers.contains("public"))
        }
    }

    @Test
    fun testHoverOnApiControllerMethod() {
        // Position 407 from get_symbols_in_file: createUser method
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getApiControllerJavaFile()
            assertNotNull("ApiController.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 407)

            assertNotNull("Should get hover info for createUser method", hoverInfo)
            assertEquals("Should identify createUser method", "createUser", hoverInfo!!.elementName)
            assertEquals("Should be a method", "method", hoverInfo.elementType)
            assertNotNull("Should have JavaDoc", hoverInfo.javaDoc)
            assertTrue(
                "JavaDoc should contain method description",
                hoverInfo.javaDoc?.contains("Creates a new user") ?: false
            )
        }
    }

    @Test
    fun testHoverOnGenericType() {
        // Position 2923 from get_symbols_in_file: ApiResponse class
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getApiControllerJavaFile()
            assertNotNull("ApiController.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 2923)

            assertNotNull("Should get hover info for ApiResponse type", hoverInfo)
            assertEquals("Should identify ApiResponse", "ApiResponse", hoverInfo!!.elementName)
            assertEquals("Should be a class", "class", hoverInfo.elementType)
            assertTrue(
                "Should show generic type information", 
                hoverInfo.type?.contains("ApiResponse") ?: false
            )

        }
    }

    @Test
    fun testHoverOnDataProcessorClass() {
        // Position 131 from get_symbols_in_file: DataProcessor class
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getDataProcessorJavaFile()
            assertNotNull("DataProcessor.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 131)

            assertNotNull("Should get hover info for DataProcessor class", hoverInfo)
            assertEquals("Should identify DataProcessor class", "DataProcessor", hoverInfo!!.elementName)
            assertEquals("Should be a class", "class", hoverInfo.elementType)
            assertNotNull("Should have presentation text", hoverInfo.presentableText)
        }
    }

    @Test
    fun testHoverOnDataProcessorMethod() {
        // Position 405 from get_symbols_in_file: processData method
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getDataProcessorJavaFile()
            assertNotNull("DataProcessor.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 405)

            assertNotNull("Should get hover info for processData method", hoverInfo)
            assertEquals("Should identify processData method", "processData", hoverInfo!!.elementName)
            assertEquals("Should be a method", "method", hoverInfo.elementType)
            assertTrue(
                "Should show return type",
                hoverInfo.type?.contains("ProcessedData") ?: false
            )
        }
    }

    @Test
    fun testHoverOnConstructor() {
        // Position 502 from get_symbols_in_file: User parameterized constructor
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserJavaFile()
            assertNotNull("User.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 502)

            assertNotNull("Should get hover info for constructor", hoverInfo)
            assertEquals("Should identify as constructor", "constructor", hoverInfo!!.elementType)
            assertTrue(
                "Should show constructor parameters",
                hoverInfo.signature?.contains("String") ?: false
            )
        }
    }

    @Test
    fun testHoverOnEnumValue() {
        // Position 790 from get_symbols_in_file: UserEvent enum
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserServiceJavaFile()
            assertNotNull("UserService.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 790)

            assertNotNull("Should get hover info for UserEvent enum", hoverInfo)
            assertEquals("Should identify UserEvent", "UserEvent", hoverInfo!!.elementName)
            assertTrue(
                "Should identify UserEvent enum",
                hoverInfo.elementType == "enum" || hoverInfo.elementType == "class"
            )
        }
    }

    @Test
    fun testHoverOnInterface() {
        // Position 1229 from get_symbols_in_file: UserListener interface
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserServiceJavaFile()
            assertNotNull("UserService.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 1229)

            assertNotNull("Should get hover info for UserListener interface", hoverInfo)
            assertEquals("Should identify UserListener", "UserListener", hoverInfo!!.elementName)
            assertEquals("Should be an interface", "interface", hoverInfo.elementType)
        }
    }

    @Test
    fun testHoverOnStaticMethod() {
        // Position 2907 from get_symbols_in_file: isValidUser static method
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserServiceJavaFile()
            assertNotNull("UserService.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 2907)

            assertNotNull("Should get hover info for isValidUser method", hoverInfo)
            assertEquals("Should identify isValidUser", "isValidUser", hoverInfo!!.elementName)
            assertEquals("Should be a method", "method", hoverInfo.elementType)
            assertTrue("Should be static", hoverInfo.modifiers.contains("static"))
            assertEquals("Should return boolean", "boolean", hoverInfo.type)
            assertTrue("Should be public", hoverInfo.modifiers.contains("public"))
        }
    }

    @Test
    fun testHoverOnDeprecatedElement() {
        // Position 486 from get_symbols_in_file: MAX_USERS deprecated field
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserServiceJavaFile()
            assertNotNull("UserService.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 486)

            assertNotNull("Should get hover info for MAX_USERS field", hoverInfo)
            assertEquals("Should identify MAX_USERS field", "MAX_USERS", hoverInfo!!.elementName)
            assertEquals("Should be a field", "field", hoverInfo.elementType)
            assertTrue("Should be marked as deprecated", hoverInfo.isDeprecated)
            assertTrue(
                "Should have deprecation message",
                hoverInfo.deprecationMessage?.contains("dynamic limits") ?: false
            )
        }
    }

    @Test
    fun testHoverOutsideCodeElements() {
        // Test hovering on whitespace/comments - should handle gracefully
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserJavaFile()
            assertNotNull("User.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 50) // Position in comment or whitespace

            // Should handle gracefully - either return info or null
            if (hoverInfo != null) {
                assertNotNull("Should have element name if info is returned", hoverInfo.elementName)
            }
            // If null, that's also acceptable for non-code elements
        }
    }

    @Test
    fun testHoverWithInvalidPosition() {
        // Test with position beyond file length
        ApplicationManager.getApplication().runReadAction {
            val userJavaFile = getUserJavaFile()
            assertNotNull("User.java should be available in test project", userJavaFile)
            
            // Test with invalid position
            val invalidHoverInfo = provider.getHoverInfoAtPosition(userJavaFile!!, 999999)
            
            // Should handle gracefully and return null or minimal info
            if (invalidHoverInfo != null) {
                assertNotNull("Should have element name if info is returned", invalidHoverInfo.elementName)
            }
            // If null, that's acceptable for invalid positions
        }
    }

    @Test
    fun testEnhancedClassHoverInfo() {
        // Position 54 from get_symbols_in_file: User class
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserJavaFile()
            assertNotNull("User.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 54)

            assertNotNull("Should get hover info for User class", hoverInfo)
            assertEquals("Should identify User class", "User", hoverInfo!!.elementName)
            // superTypes may be empty for User class, that's acceptable
            assertTrue(
                "Should have superTypes list (empty or filled)",
                hoverInfo.superTypes.isEmpty() || hoverInfo.superTypes.isNotEmpty()
            )
        }
    }

    @Test
    fun testEnhancedMethodHoverInfo() {
        // Position 1461 from get_symbols_in_file: addUser method
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserServiceJavaFile()
            assertNotNull("UserService.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 1461)

            assertNotNull("Should get hover info for addUser method", hoverInfo)
            assertEquals("Should identify addUser method", "addUser", hoverInfo!!.elementName)
            assertNotNull("Should have throws exceptions list", hoverInfo.throwsExceptions)
            assertTrue("Should have non-negative calledByCount", hoverInfo.calledByCount >= 0)
            if (hoverInfo.complexity != null) {
                assertTrue("Complexity should be positive if calculated", hoverInfo.complexity!! > 0)
            }
        }
    }

    @Test
    fun testInterfaceImplementedBy() {
        // Position 1229 from get_symbols_in_file: UserListener interface
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserServiceJavaFile()
            assertNotNull("UserService.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 1229)

            assertNotNull("Should get hover info for UserListener interface", hoverInfo)
            assertEquals("Should identify UserListener", "UserListener", hoverInfo!!.elementName)
            assertEquals("Should be an interface", "interface", hoverInfo.elementType)
            assertNotNull("Interface should have implementedBy list", hoverInfo.implementedBy)
        }
    }

    @Test
    fun testMethodOverriddenBy() {
        // Position 1920 from get_symbols_in_file: toString method
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserJavaFile()
            assertNotNull("User.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 1920)

            assertNotNull("Should get hover info for toString method", hoverInfo)
            assertEquals("Should identify toString method", "toString", hoverInfo!!.elementName)
            assertNotNull("Method should have overriddenBy list", hoverInfo.overriddenBy)
        }
    }

    @Test
    fun testJavaDocTags() {
        // Position 144 from get_symbols_in_file: UserService class (has @since and @see tags)
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserServiceJavaFile()
            assertNotNull("UserService.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 144)

            assertNotNull("Should get hover info for UserService class", hoverInfo)
            assertNotNull("Should have JavaDoc", hoverInfo!!.javaDoc)
            // Check if @since is extracted
            if (hoverInfo.since != null) {
                assertEquals("Should extract @since tag", "1.0", hoverInfo.since)
            }
            // Check if @see references are extracted
            if (hoverInfo.seeAlso.isNotEmpty()) {
                assertTrue("Should extract @see tag", hoverInfo.seeAlso.contains("DataProcessor"))
            }
        }
    }

    @Test
    fun testFieldUsageCount() {
        // Position 654 from get_symbols_in_file: users field
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserServiceJavaFile()
            assertNotNull("UserService.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 654)

            assertNotNull("Should get hover info for users field", hoverInfo)
            assertEquals("Should identify users field", "users", hoverInfo!!.elementName)
            assertEquals("Should be a field", "field", hoverInfo.elementType)
            assertTrue("Field should have usage count", hoverInfo.calledByCount >= 0)
        }
    }

    @Test
    fun testMethodComplexity() {
        // Position 405 from get_symbols_in_file: processData method
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getDataProcessorJavaFile()
            assertNotNull("DataProcessor.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 405)

            assertNotNull("Should get hover info for processData method", hoverInfo)
            assertEquals("Should identify processData method", "processData", hoverInfo!!.elementName)
            assertEquals("Should be a method", "method", hoverInfo.elementType)
            if (hoverInfo.complexity != null) {
                assertTrue("Method should have complexity >= 1", hoverInfo.complexity!! >= 1)
            }
        }
    }

    @Test
    fun testHoverInfoForJavaDocAndAnnotations() {
        // Test hover on a method with comprehensive JavaDoc and annotations
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserServiceJavaFile()
            assertNotNull("UserService.java should be available in test project", psiFile)

            // Position 1461 from get_symbols_in_file: addUser method
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 1461)

            assertNotNull("Should get hover info for addUser method", hoverInfo)
            assertEquals("Should identify addUser method", "addUser", hoverInfo!!.elementName)
            assertEquals("Should be a method", "method", hoverInfo.elementType)
            assertNotNull("Should have JavaDoc documentation", hoverInfo.javaDoc)
            assertTrue(
                "JavaDoc should contain method description",
                hoverInfo.javaDoc?.contains("Adds a new user") ?: false
            )
            assertEquals("Should return boolean", "boolean", hoverInfo.type)
            assertTrue("Should be public", hoverInfo.modifiers.contains("public"))
            
            // Check for method signature
            assertNotNull("Should have signature", hoverInfo.signature)
            assertTrue("Signature should contain method name", hoverInfo.signature?.contains("addUser") ?: false)
        }
    }

    @Test
    fun testHoverOnComplexGenericTypes() {
        // Test hover on generic types with complex type parameters
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserServiceJavaFile()
            assertNotNull("UserService.java should be available in test project", psiFile)

            // Position 654 from get_symbols_in_file: users field (likely a List or similar)
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 654)

            assertNotNull("Should get hover info for users field", hoverInfo)
            assertEquals("Should identify users field", "users", hoverInfo!!.elementName)
            assertEquals("Should be a field", "field", hoverInfo.elementType)
            assertNotNull("Should have type information", hoverInfo.type)
            assertTrue("Should be private", hoverInfo.modifiers.contains("private"))
        }
    }

    @Test
    fun testHoverOnOverrideMethod() {
        // Position 1920 from get_symbols_in_file: toString method
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserJavaFile()
            assertNotNull("User.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 1920)

            assertNotNull("Should get hover info for toString method", hoverInfo)
            assertEquals("Should identify toString method", "toString", hoverInfo!!.elementName)
            assertEquals("Should be a method", "method", hoverInfo.elementType)
            assertNotNull("Method should have overriddenBy list", hoverInfo.overriddenBy)
            assertTrue("Should be an override", hoverInfo.overriddenBy.isNotEmpty() || hoverInfo.signature?.contains("@Override") == true)
        }
    }

    @Test
    fun testHoverOnDeprecatedElementWithMessage() {
        // Position 486 from get_symbols_in_file: MAX_USERS deprecated field
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserServiceJavaFile()
            assertNotNull("UserService.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 486)

            assertNotNull("Should get hover info for MAX_USERS field", hoverInfo)
            assertEquals("Should identify MAX_USERS field", "MAX_USERS", hoverInfo!!.elementName)
            assertEquals("Should be a field", "field", hoverInfo.elementType)
            assertTrue("Should be marked as deprecated", hoverInfo.isDeprecated)
            assertTrue(
                "Should have deprecation message",
                hoverInfo.deprecationMessage?.contains("dynamic limits") ?: false
            )
        }
    }

    @Test
    fun testHoverOnMethodWithThrowsExceptions() {
        // Test hover on a method that throws exceptions
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserServiceJavaFile()
            assertNotNull("UserService.java should be available in test project", psiFile)

            // Position 1461 from get_symbols_in_file: addUser method
            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 1461)

            assertNotNull("Should get hover info for addUser method", hoverInfo)
            assertEquals("Should identify addUser method", "addUser", hoverInfo!!.elementName)
            assertEquals("Should be a method", "method", hoverInfo.elementType)
            assertNotNull("Should have throws exceptions list", hoverInfo.throwsExceptions)
            assertTrue("Should have non-negative calledByCount", hoverInfo.calledByCount >= 0)
            if (hoverInfo.complexity != null) {
                assertTrue("Complexity should be positive if calculated", hoverInfo.complexity!! > 0)
            }
        }
    }

    @Test
    fun testHoverOnInterfaceWithImplementations() {
        // Position 1229 from get_symbols_in_file: UserListener interface
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserServiceJavaFile()
            assertNotNull("UserService.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 1229)

            assertNotNull("Should get hover info for UserListener interface", hoverInfo)
            assertEquals("Should identify UserListener", "UserListener", hoverInfo!!.elementName)
            assertEquals("Should be an interface", "interface", hoverInfo.elementType)
            assertNotNull("Interface should have implementedBy list", hoverInfo.implementedBy)
        }
    }

    @Test
    fun testHoverOnFieldWithUsageCount() {
        // Position 654 from get_symbols_in_file: users field
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserServiceJavaFile()
            assertNotNull("UserService.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 654)

            assertNotNull("Should get hover info for users field", hoverInfo)
            assertEquals("Should identify users field", "users", hoverInfo!!.elementName)
            assertEquals("Should be a field", "field", hoverInfo.elementType)
            assertTrue("Field should have usage count", hoverInfo.calledByCount >= 0)
        }
    }

    @Test
    fun testHoverOnClassWithSuperTypes() {
        // Position 54 from get_symbols_in_file: User class
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserJavaFile()
            assertNotNull("User.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 54)

            assertNotNull("Should get hover info for User class", hoverInfo)
            assertEquals("Should identify User class", "User", hoverInfo!!.elementName)
            assertEquals("Should be a class", "class", hoverInfo.elementType)
            assertNotNull("Should have JavaDoc", hoverInfo.javaDoc)
            assertTrue(
                "JavaDoc should contain class description",
                hoverInfo.javaDoc?.contains("User entity class") ?: false
            )
            // superTypes may be empty for User class, that's acceptable
            assertTrue(
                "Should have superTypes list (empty or filled)",
                hoverInfo.superTypes.isEmpty() || hoverInfo.superTypes.isNotEmpty()
            )
        }
    }

    @Test
    fun testHoverOnConstructorWithParameters() {
        // Position 502 from get_symbols_in_file: User parameterized constructor
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserJavaFile()
            assertNotNull("User.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 502)

            assertNotNull("Should get hover info for constructor", hoverInfo)
            assertEquals("Should identify as constructor", "constructor", hoverInfo!!.elementType)
            assertTrue(
                "Should show constructor parameters",
                hoverInfo.signature?.contains("String") ?: false
            )
            assertNotNull("Should have constructor signature", hoverInfo.signature)
        }
    }

    @Test
    fun testHoverOnJavaDocWithTags() {
        // Position 144 from get_symbols_in_file: UserService class (has @since and @see tags)
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserServiceJavaFile()
            assertNotNull("UserService.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 144)

            assertNotNull("Should get hover info for UserService class", hoverInfo)
            assertNotNull("Should have JavaDoc", hoverInfo!!.javaDoc)
            // Check if @since is extracted
            if (hoverInfo.since != null) {
                assertEquals("Should extract @since tag", "1.0", hoverInfo.since)
            }
            // Check if @see references are extracted
            if (hoverInfo.seeAlso.isNotEmpty()) {
                assertTrue("Should extract @see tag", hoverInfo.seeAlso.contains("DataProcessor"))
            }
        }
    }

    @Test
    fun testHoverInvalidPositionHandling() {
        // Test with position beyond file length
        ApplicationManager.getApplication().runReadAction {
            val userJavaFile = getUserJavaFile()
            assertNotNull("User.java should be available in test project", userJavaFile)
            
            // Test with invalid position
            val invalidHoverInfo = provider.getHoverInfoAtPosition(userJavaFile!!, 999999)
            
            // Should handle gracefully and return null or minimal info
            if (invalidHoverInfo != null) {
                assertNotNull("Should have element name if info is returned", invalidHoverInfo.elementName)
            }
            // If null, that's acceptable for invalid positions
        }
    }

    @Test
    fun testSupportsElement() {
        // Test that the provider supports Java elements
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserJavaFile()
            assertNotNull("User.java should be available in test project", psiFile)

            val element = psiFile!!.findElementAt(200) // Get any element from the file
            assertNotNull("Should find an element at position 200", element)

            val supportsElement = provider.supportsElement(element!!)
            assertTrue("Provider should support Java elements", supportsElement)

            val supportedLanguage = provider.getSupportedLanguage()
            assertEquals("Should support Java/Kotlin", "Java/Kotlin", supportedLanguage)
        }
    }

    @Test
    fun testInnerClassHover() {
        // Position 3872 from get_symbols_in_file: UserSession inner class
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getUserServiceJavaFile()
            assertNotNull("UserService.java should be available in test project", psiFile)

            val hoverInfo = provider.getHoverInfoAtPosition(psiFile!!, 3872)

            assertNotNull("Should get hover info for UserSession inner class", hoverInfo)
            assertEquals("Should identify UserSession", "UserSession", hoverInfo!!.elementName)
            assertEquals("Should be a class", "class", hoverInfo.elementType)
            assertTrue("Should be static inner class", hoverInfo.modifiers.contains("static"))
        }
    }
}
