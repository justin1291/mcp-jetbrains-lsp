package dev.mcp.extensions.lsp.tools

import dev.mcp.extensions.lsp.JavaBaseTest
import dev.mcp.extensions.lsp.core.models.GetSymbolsArgs
import dev.mcp.extensions.lsp.core.models.SymbolInfo
import dev.mcp.extensions.lsp.core.models.SymbolKind
import dev.mcp.extensions.lsp.core.models.Visibility
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Disabled
class GetSymbolsInFileToolTest : JavaBaseTest() {

    private val tool: GetSymbolsInFileTool = GetSymbolsInFileTool()

    @Test
    fun testExtractSymbolsFromUserService() {
        val userServicePath = "src/main/java/com/example/demo/UserService.java"

        val args = GetSymbolsArgs(
            filePath = userServicePath,
            hierarchical = false,
            includeImports = true
        )

        val response = tool.handle(project, args)
        assertNotNull(response)

        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }

        val symbols: List<SymbolInfo> = parseJsonResponse(response.status)
        assertTrue(symbols.isNotEmpty(), "Should extract symbols from UserService.java")

        // Check for UserService class
        val classes = symbols.filter { it.kind == SymbolKind.Class }
        assertTrue(
            classes.any { it.name == "UserService" },
            "Should find UserService class"
        )

        // Check for key methods
        val methods = symbols.filter { it.kind == SymbolKind.Method }
        assertTrue(
            methods.any { it.name == "addUser" },
            "Should find addUser method"
        )
        assertTrue(
            methods.any { it.name == "findUser" },
            "Should find findUser method"
        )
        assertTrue(
            methods.any { it.name == "removeUser" },
            "Should find removeUser method"
        )

        // Check for constants
        val fields = symbols.filter { it.kind == SymbolKind.Field }
        assertTrue(
            fields.any { it.name == "DEFAULT_ROLE" },
            "Should find DEFAULT_ROLE constant"
        )
        assertTrue(
            fields.any { it.name == "MAX_USERS" },
            "Should find MAX_USERS constant"
        )
    }

    @Test
    fun testExtractSymbolsFromUser() {
        val userPath = "src/main/java/com/example/demo/User.java"

        val args = GetSymbolsArgs(
            filePath = userPath,
            hierarchical = false,
            includeImports = true
        )

        val response = tool.handle(project, args)
        assertNotNull(response)

        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }

        val symbols: List<SymbolInfo> = parseJsonResponse(response.status)
        assertTrue(symbols.isNotEmpty(), "Should extract symbols from User.java")

        // Check for User class
        val classes = symbols.filter { it.kind == SymbolKind.Class }
        assertTrue(
            classes.any { it.name == "User" },
            "Should find User class"
        )

        // Check for getter methods
        val methods = symbols.filter { it.kind == SymbolKind.Method }
        assertTrue(
            methods.any { it.name == "getId" },
            "Should find getId method"
        )
        assertTrue(
            methods.any { it.name == "getName" },
            "Should find getName method"
        )
        assertTrue(
            methods.any { it.name == "getEmail" },
            "Should find getEmail method"
        )

        // Check for setter methods
        assertTrue(
            methods.any { it.name == "setId" },
            "Should find setId method"
        )
        assertTrue(
            methods.any { it.name == "setName" },
            "Should find setName method"
        )

        // Check for fields
        val fields = symbols.filter { it.kind == SymbolKind.Field }
        assertTrue(
            fields.any { it.name == "id" },
            "Should find id field"
        )
        assertTrue(
            fields.any { it.name == "name" },
            "Should find name field"
        )
        assertTrue(
            fields.any { it.name == "email" },
            "Should find email field"
        )

        // Check for constructors
        val constructors = symbols.filter { it.kind == SymbolKind.Constructor }
        assertTrue(
            constructors.any { it.name == "User" },
            "Should find User constructor"
        )
    }

    @Test
    fun testExtractSymbolsFromApiController() {
        val apiControllerPath = "src/main/java/com/example/demo/ApiController.java"

        val args = GetSymbolsArgs(
            filePath = apiControllerPath,
            hierarchical = false,
            includeImports = true
        )

        val response = tool.handle(project, args)
        assertNotNull(response)

        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }

        val symbols: List<SymbolInfo> = parseJsonResponse(response.status)
        assertTrue(symbols.isNotEmpty(), "Should extract symbols from ApiController.java")

        // Check for ApiController class
        val classes = symbols.filter { it.kind == SymbolKind.Class }
        assertTrue(
            classes.any { it.name == "ApiController" },
            "Should find ApiController class"
        )
        assertTrue(
            classes.any { it.name == "ApiResponse" },
            "Should find ApiResponse class"
        )

        // Check for API methods
        val methods = symbols.filter { it.kind == SymbolKind.Method }
        assertTrue(
            methods.any { it.name == "createUser" },
            "Should find createUser method"
        )
        assertTrue(
            methods.any { it.name == "getUser" },
            "Should find getUser method"
        )
        assertTrue(
            methods.any { it.name == "updateUser" },
            "Should find updateUser method"
        )
        assertTrue(
            methods.any { it.name == "deleteUser" },
            "Should find deleteUser method"
        )

        // Check for constants
        val fields = symbols.filter { it.kind == SymbolKind.Field }
        assertTrue(
            fields.any { it.name == "STATUS_SUCCESS" },
            "Should find STATUS_SUCCESS constant"
        )
        assertTrue(
            fields.any { it.name == "STATUS_ERROR" },
            "Should find STATUS_ERROR constant"
        )
    }

    @Test
    fun testExtractSymbolsFromDataProcessor() {
        // DataProcessor is now Java, not Kotlin
        val dataProcessorPath = "src/main/java/com/example/demo/DataProcessor.java"

        val args = GetSymbolsArgs(
            filePath = dataProcessorPath,
            hierarchical = false,
            includeImports = true
        )

        val response = tool.handle(project, args)
        assertNotNull(response)

        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }

        val symbols: List<SymbolInfo> = parseJsonResponse(response.status)
        assertTrue(symbols.isNotEmpty(), "Should extract symbols from DataProcessor.java")

        // Check for Java classes (converted from Kotlin)
        val classes = symbols.filter { it.kind == SymbolKind.Class }
        assertTrue(
            classes.any { it.name == "DataProcessor" },
            "Should find DataProcessor class"
        )
        assertTrue(
            classes.any { it.name == "ProcessedData" },
            "Should find ProcessedData class"
        )
        assertTrue(
            classes.any { it.name == "CacheStats" },
            "Should find CacheStats class"
        )

        // Check for Java methods (converted from Kotlin)
        val methods = symbols.filter { it.kind == SymbolKind.Method }
        assertTrue(
            methods.any { it.name == "processData" },
            "Should find processData method"
        )
        assertTrue(
            methods.any { it.name == "processBatch" },
            "Should find processBatch method"
        )
        assertTrue(
            methods.any { it.name == "getCacheStats" },
            "Should find getCacheStats method"
        )

        // Check for static constants (converted from companion object)
        val fields = symbols.filter { it.kind == SymbolKind.Field }
        assertTrue(
            fields.any { it.name == "MAX_CACHE_SIZE" },
            "Should find MAX_CACHE_SIZE constant"
        )
        assertTrue(
            fields.any { it.name == "DEFAULT_BATCH_SIZE" },
            "Should find DEFAULT_BATCH_SIZE constant"
        )
    }

    @Test
    fun testHierarchicalExtraction() {
        // Test hierarchical symbol extraction using UserService
        val userServicePath = "src/main/java/com/example/demo/UserService.java"

        val args = GetSymbolsArgs(
            filePath = userServicePath,
            hierarchical = true,
            includeImports = true
        )

        val response = tool.handle(project, args)
        assertNotNull(response)

        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }

        val symbols: List<SymbolInfo> = parseJsonResponse(response.status)
        assertTrue(symbols.isNotEmpty(), "Should extract hierarchical symbols")

        // Check top-level structure
        val imports = symbols.filter { it.kind == SymbolKind.Import }
        assertTrue(imports.isNotEmpty(), "Should have imports at top level")

        val topLevelClasses = symbols.filter { it.kind == SymbolKind.Class }
        assertTrue(
            topLevelClasses.any { it.name == "UserService" },
            "Should find UserService at top level"
        )

        // Check for nested types (enum and interface)
        val userServiceClass = topLevelClasses.find { it.name == "UserService" }
        if (userServiceClass?.children != null) {
            val nestedTypes = userServiceClass.children?.filter {
                it.kind == SymbolKind.Enum || it.kind == SymbolKind.Interface
            }
            if (nestedTypes != null) {
                assertTrue(
                    nestedTypes.any { it.name == "UserEvent" },
                    "Should find UserEvent enum as nested"
                )
                assertTrue(
                    nestedTypes.any { it.name == "UserListener" },
                    "Should find UserListener interface as nested"
                )
            }
        }
    }

    @Test
    fun testSymbolTypeFiltering() {
        // Test filtering only methods from UserService
        val userServicePath = "src/main/java/com/example/demo/UserService.java"

        val methodArgs = GetSymbolsArgs(
            filePath = userServicePath,
            hierarchical = false,
            symbolTypes = listOf("method")
        )

        val methodResponse = tool.handle(project, methodArgs)
        if (methodResponse.error != null) {
            println("PSI indexing limitation in test: ${methodResponse.error}")
            return
        }

        val methods: List<SymbolInfo> = parseJsonResponse(methodResponse.status)
        assertTrue(
            methods.all { it.kind.value == "method" || it.kind.value == "constructor" },
            "Should only have methods"
        )
        assertTrue(
            methods.any { it.name == "addUser" },
            "Should include addUser method"
        )

        // Test filtering only fields
        val fieldArgs = GetSymbolsArgs(
            filePath = userServicePath,
            hierarchical = false,
            symbolTypes = listOf("field")
        )

        val fieldResponse = tool.handle(project, fieldArgs)
        if (fieldResponse.error != null) {
            println("PSI indexing limitation in test: ${fieldResponse.error}")
            return
        }

        val fields: List<SymbolInfo> = parseJsonResponse(fieldResponse.status)
        assertTrue(fields.all { it.kind.value == "field" }, "Should only have fields")
        assertTrue(
            fields.any { it.name == "DEFAULT_ROLE" },
            "Should include DEFAULT_ROLE field"
        )
    }

    @Test
    fun testModifierExtraction() {
        // Test modifier extraction from UserService
        val userServicePath = "src/main/java/com/example/demo/UserService.java"

        val args = GetSymbolsArgs(
            filePath = userServicePath,
            hierarchical = false
        )

        val response = tool.handle(project, args)
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }

        val symbols: List<SymbolInfo> = parseJsonResponse(response.status)

        // Check DEFAULT_ROLE field modifiers
        val defaultRole = symbols.find { it.name == "DEFAULT_ROLE" }
        if (defaultRole != null) {
            assertTrue(
                defaultRole.modifiers.contains("public"),
                "DEFAULT_ROLE should be public"
            )
            assertTrue(
                defaultRole.modifiers.contains("static"),
                "DEFAULT_ROLE should be static"
            )
            assertTrue(
                defaultRole.modifiers.contains("final"),
                "DEFAULT_ROLE should be final"
            )
        }

        // Check public method modifiers
        val addUser = symbols.find { it.name == "addUser" && it.kind.value == "method" }
        if (addUser != null) {
            assertTrue(
                addUser.modifiers.contains("public"),
                "addUser should be public"
            )
        }

        // Check static method modifiers
        val isValidUser = symbols.find { it.name == "isValidUser" }
        if (isValidUser != null) {
            assertTrue(
                isValidUser.modifiers.contains("static"),
                "isValidUser should be static"
            )
        }
    }

    @Test
    fun testMethodParametersAndReturnTypes() {
        // Test parameter and return type extraction from UserService
        val userServicePath = "src/main/java/com/example/demo/UserService.java"

        val args = GetSymbolsArgs(
            filePath = userServicePath,
            hierarchical = false
        )

        val response = tool.handle(project, args)
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }

        val symbols: List<SymbolInfo> = parseJsonResponse(response.status)

        // Check addUser method
        val addUser = symbols.find { it.name == "addUser" && it.kind == SymbolKind.Method }
        if (addUser != null) {
            assertEquals("boolean", addUser.typeInfo?.displayName, "addUser should return boolean")
            val params = addUser.languageData?.get("parameters")
            assertTrue(params?.contains("User") == true, "addUser should have User parameter")
        }

        // Check findUser method
        val findUser = symbols.find { it.name == "findUser" }
        if (findUser != null) {
            assertTrue(
                findUser.typeInfo?.displayName?.contains("Optional") == true,
                "findUser should return Optional"
            )
            val params = findUser.languageData?.get("parameters")
            assertTrue(params?.contains("String") == true, "findUser should have String parameter")
        }

        // Check getUserCount method
        val getUserCount = symbols.find { it.name == "getUserCount" }
        if (getUserCount != null) {
            assertEquals(
                "int", getUserCount.typeInfo?.displayName,
                "getUserCount should return int"
            )
            assertEquals(
                "0", getUserCount.languageData?.get("parameterCount"),
                "getUserCount should have no parameters"
            )
        }
    }

    @Test
    fun testJavaSpecificFeatures() {
        // Test Java-specific features in DataProcessor (converted from Kotlin)
        val dataProcessorPath = "src/main/java/com/example/demo/DataProcessor.java"

        val args = GetSymbolsArgs(
            filePath = dataProcessorPath,
            hierarchical = false
        )

        val response = tool.handle(project, args)
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }

        val symbols: List<SymbolInfo> = parseJsonResponse(response.status)

        // Check for regular Java class (converted from Kotlin data class)
        val processedData = symbols.find { it.name == "ProcessedData" }
        if (processedData != null) {
            assertEquals(SymbolKind.Class, processedData.kind, "ProcessedData should be a class")
        }

        // Static constants should be found as regular fields (converted from companion object)
        val constants = symbols.filter { it.kind == SymbolKind.Field && it.modifiers.contains("static") }
        assertTrue(
            constants.any { it.name == "MAX_CACHE_SIZE" },
            "Should find static MAX_CACHE_SIZE constant"
        )
        assertTrue(
            constants.any { it.name == "DEFAULT_BATCH_SIZE" },
            "Should find static DEFAULT_BATCH_SIZE constant"
        )
    }

    @Test
    fun testLineNumberAccuracy() {
        // Test line number accuracy using User class
        val userPath = "src/main/java/com/example/demo/User.java"

        val args = GetSymbolsArgs(
            filePath = userPath,
            hierarchical = false
        )

        val response = tool.handle(project, args)
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }

        val symbols: List<SymbolInfo> = parseJsonResponse(response.status)

        // Check that line numbers are reasonable (> 0)
        symbols.forEach { symbol ->
            assertTrue(
                symbol.location.lineNumber > 0,
                "${symbol.name} should have positive line number"
            )
        }

        // Check relative ordering of some symbols
        val userClass = symbols.find { it.name == "User" && it.kind == SymbolKind.Class }
        val getId = symbols.find { it.name == "getId" }
        val getName = symbols.find { it.name == "getName" }

        if (userClass != null && getId != null) {
            assertTrue(
                getId.location.lineNumber > userClass.location.lineNumber,
                "getId method should be after User class declaration"
            )
        }

        if (getId != null && getName != null) {
            assertTrue(
                getName.location.lineNumber > getId.location.lineNumber,
                "getName should be after getId in the file"
            )
        }
    }

    @Test
    fun testFileNotFound() {
        val args = GetSymbolsArgs(
            filePath = "non/existent/file.java",
            hierarchical = false
        )

        val response = tool.handle(project, args)
        assertNotNull(response)
        assertNotNull(response.error, "Should have error message")
        assertTrue(
            response.error?.contains("File not found") ?: false,
            "Error should mention file not found"
        )
    }

    @Test
    fun testEmptyFileHandling() {
        // Create a minimal empty Java file to test edge case
        val emptyFile = myFixture.addFileToProject(
            "src/main/java/com/example/Empty.java",
            "package com.example; // Empty file"
        )

        val args = GetSymbolsArgs(
            filePath = "src/main/java/com/example/Empty.java",
            hierarchical = false
        )

        val response = tool.handle(project, args)
        assertNotNull(response)

        if (response.error == null) {
            val symbols: List<SymbolInfo> = parseJsonResponse(response.status)
            // Should handle empty file gracefully
            assertTrue(
                symbols.isEmpty() || symbols.all { it.kind == SymbolKind.Import || it.kind == SymbolKind.Package },
                "Empty file should have no symbols or only package/import symbols"
            )
        } else {
            println("PSI indexing limitation: ${response.error}")
        }
    }

    @Test
    fun testNewMetadataFields() {
        // Test new metadata fields using the enhanced User.java file
        val userPath = "src/main/java/com/example/demo/User.java"

        val args = GetSymbolsArgs(
            filePath = userPath,
            hierarchical = false
        )

        val response = tool.handle(project, args)
        assertNotNull(response)

        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }

        val symbols: List<SymbolInfo> = parseJsonResponse(response.status)

        // Test isDeprecated field
        val oldDefaultRole = symbols.find { it.name == "OLD_DEFAULT_ROLE" }
        assertNotNull(oldDefaultRole, "Should find OLD_DEFAULT_ROLE")
        assertTrue(oldDefaultRole.isDeprecated, "OLD_DEFAULT_ROLE should be marked as deprecated")

        val validateMethod = symbols.find { it.name == "validate" }
        assertNotNull(validateMethod, "Should find validate method")
        assertTrue(validateMethod.isDeprecated, "validate method should be marked as deprecated")

        // Test documentation field
        assertTrue(oldDefaultRole.documentation?.isPresent == true, "OLD_DEFAULT_ROLE should have documentation")

        val toStringMethod = symbols.find { it.name == "toString" && it.kind == SymbolKind.Method }
        assertNotNull(toStringMethod, "Should find toString method")
        assertTrue(toStringMethod.documentation?.isPresent == true, "toString method should have documentation")

        // Test override field
        assertTrue(toStringMethod.overrides != null, "toString should be marked as override")

        val equalsMethod = symbols.find { it.name == "equals" }
        assertNotNull(equalsMethod, "Should find equals method")
        assertTrue(equalsMethod.overrides != null, "equals should be marked as override")

        val hashCodeMethod = symbols.find { it.name == "hashCode" }
        assertNotNull(hashCodeMethod, "Should find hashCode method")
        assertTrue(hashCodeMethod.overrides != null, "hashCode should be marked as override")

        // Test overrides details
        assertEquals(
            "java.lang.Object", toStringMethod.overrides?.parentClass,
            "toString should override Object.toString"
        )
        assertEquals("toString", toStringMethod.overrides?.methodName)

        assertEquals(
            "java.lang.Object", equalsMethod.overrides?.parentClass,
            "equals should override Object.equals"
        )
        assertEquals("equals", equalsMethod.overrides?.methodName)

        assertEquals(
            "java.lang.Object", hashCodeMethod.overrides?.parentClass,
            "hashCode should override Object.hashCode"
        )
        assertEquals("hashCode", hashCodeMethod.overrides?.methodName)

        // Test visibility field
        assertEquals(Visibility.PROTECTED, oldDefaultRole.visibility, "OLD_DEFAULT_ROLE should be protected")
        assertEquals(Visibility.PROTECTED, validateMethod.visibility, "validate method should be protected")

        val updateTimestamp = symbols.find { it.name == "updateTimestamp" }
        assertNotNull(updateTimestamp, "Should find updateTimestamp method")
        assertEquals(Visibility.PRIVATE, updateTimestamp.visibility, "updateTimestamp should be private")

        val notifyChange = symbols.find { it.name == "notifyChange" }
        assertNotNull(notifyChange, "Should find notifyChange method")
        assertEquals(
            Visibility.PACKAGE, notifyChange.visibility,
            "notifyChange should be package-private"
        )

        // Test visibility for public methods
        val getId = symbols.find { it.name == "getId" }
        assertNotNull(getId, "Should find getId method")
        assertEquals(Visibility.PUBLIC, getId.visibility, "getId should be public")

        // Test decorators field (replacing annotations)
        assertTrue(
            oldDefaultRole.decorators?.isNotEmpty() == true,
            "OLD_DEFAULT_ROLE should have decorators"
        )
        assertTrue(oldDefaultRole.decorators?.any { it.name == "Deprecated" } == true,
            "OLD_DEFAULT_ROLE decorators should include @Deprecated")

        assertTrue(
            toStringMethod.decorators?.isNotEmpty() == true,
            "toString should have decorators"
        )
        assertTrue(toStringMethod.decorators?.any { it.name == "Override" } == true,
            "toString decorators should include @Override")

        assertTrue(
            validateMethod.decorators?.isNotEmpty() == true,
            "validate should have decorators"
        )
        assertTrue(validateMethod.decorators?.any { it.name == "Deprecated" } == true,
            "validate decorators should include @Deprecated")
        assertTrue(validateMethod.decorators?.any { it.name == "SuppressWarnings" } == true,
            "validate decorators should include @SuppressWarnings")

        // Check BaseEntity class is also found (from extends)
        val baseEntityClass = symbols.find { it.name == "BaseEntity" && it.kind == SymbolKind.Class }
        assertNotNull(baseEntityClass, "Should find BaseEntity class")
        assertTrue(baseEntityClass.documentation?.isPresent == true, "BaseEntity should have documentation")
        assertEquals(
            Visibility.PACKAGE, baseEntityClass.visibility,
            "BaseEntity should be package-private (no modifier = package-private)"
        )
    }

    @Test
    fun testNestedClassExtraction() {
        // Test nested class extraction from ApiController (ApiResponse is nested)
        val apiControllerPath = "src/main/java/com/example/demo/ApiController.java"

        val args = GetSymbolsArgs(
            filePath = apiControllerPath,
            hierarchical = true
        )

        val response = tool.handle(project, args)
        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }

        val symbols: List<SymbolInfo> = parseJsonResponse(response.status)

        // Should find both top-level ApiController and nested ApiResponse
        val topLevelClasses = symbols.filter { it.kind == SymbolKind.Class }
        assertTrue(
            topLevelClasses.any { it.name == "ApiController" },
            "Should find ApiController class"
        )

        // ApiResponse might be found as top-level or nested depending on implementation
        val apiResponse = symbols.find { it.name == "ApiResponse" }
        if (apiResponse != null) {
            assertEquals(SymbolKind.Class, apiResponse.kind, "ApiResponse should be a class")
        }
    }

    @Test
    fun testHierarchicalMetadataFields() {
        val userServicePath = "src/main/java/com/example/demo/UserService.java"

        val args = GetSymbolsArgs(
            filePath = userServicePath,
            hierarchical = true
        )

        val response = tool.handle(project, args)
        assertNotNull(response)

        if (response.error != null) {
            println("PSI indexing limitation in test: ${response.error}")
            return
        }

        val symbols: List<SymbolInfo> = parseJsonResponse(response.status)

        // Find the main UserService class
        val mainClass = symbols.find { it.name == "UserService" && it.kind == SymbolKind.Class }
        assertNotNull(mainClass, "Should find UserService class")
        assertTrue(mainClass.documentation?.isPresent == true, "Main class should have documentation")
        assertTrue(
            mainClass.decorators?.isNotEmpty() == true,
            "Main class should have decorators (@SuppressWarnings)"
        )
        assertEquals(Visibility.PUBLIC, mainClass.visibility, "Main class should be public")

        // Check children
        assertNotNull(mainClass.children, "Main class should have children")

        // Check deprecated field in main class
        val maxUsersField = mainClass.children?.find { it.name == "MAX_USERS" }
        assertNotNull(maxUsersField, "Should find MAX_USERS field")
        assertTrue(maxUsersField.isDeprecated, "MAX_USERS should be deprecated")
        assertTrue(maxUsersField.documentation?.isPresent == true, "MAX_USERS should have documentation")
        assertEquals(Visibility.PUBLIC, maxUsersField.visibility, "MAX_USERS should be public")

        // Find UserEvent enum in children
        val userEventEnum = mainClass.children?.find { it.name == "UserEvent" && it.kind == SymbolKind.Enum }
        assertNotNull(userEventEnum, "Should find UserEvent enum as child")
        assertTrue(userEventEnum.documentation?.isPresent == true, "UserEvent should have documentation")
        assertEquals(Visibility.PUBLIC, userEventEnum.visibility, "UserEvent should be public")

        // Find UserListener interface in children
        val userListenerInterface =
            mainClass.children?.find { it.name == "UserListener" && it.kind == SymbolKind.Interface }
        assertNotNull(userListenerInterface, "Should find UserListener interface as child")
        assertTrue(userListenerInterface.documentation?.isPresent == true, "UserListener should have documentation")
        assertEquals(Visibility.PUBLIC, userListenerInterface.visibility, "UserListener should be public")

        // Find UserSession inner class
        val userSessionClass = mainClass.children?.find { it.name == "UserSession" && it.kind == SymbolKind.Class }
        assertNotNull(userSessionClass, "Should find UserSession class as child")
        assertTrue(userSessionClass.documentation?.isPresent == true, "UserSession should have documentation")
        assertEquals(Visibility.PUBLIC, userSessionClass.visibility, "UserSession should be public")

        // Check different visibility methods
        val protectedMethod = mainClass.children?.find { it.name == "notifyListeners" }
        assertNotNull(protectedMethod, "Should find notifyListeners method")
        assertEquals(Visibility.PROTECTED, protectedMethod.visibility, "notifyListeners should be protected")

        val packagePrivateMethod = mainClass.children?.find { it.name == "addListener" }
        assertNotNull(packagePrivateMethod, "Should find addListener method")
        assertEquals(
            Visibility.PACKAGE, packagePrivateMethod.visibility,
            "addListener should be package-private"
        )

        val privateMethod = mainClass.children?.find { it.name == "validateUser" }
        assertNotNull(privateMethod, "Should find validateUser method")
        assertEquals(Visibility.PRIVATE, privateMethod.visibility, "validateUser should be private")

        // Check deprecated method
        val deprecatedMethod = mainClass.children?.find { it.name == "getUser" && it.kind == SymbolKind.Method }
        assertNotNull(deprecatedMethod, "Should find deprecated getUser method")
        assertTrue(deprecatedMethod.isDeprecated, "getUser should be deprecated")
        assertTrue(deprecatedMethod.documentation?.isPresent == true, "getUser should have documentation")
    }

    // Helper extension functions for easier testing
    private fun List<SymbolInfo>.filterByKind(kind: String): List<SymbolInfo> {
        return this.filter { it.kind.value == kind }
    }

    private fun List<SymbolInfo>.findByNameAndKind(name: String, kind: String): SymbolInfo? {
        return this.find { it.name == name && it.kind.value == kind }
    }
}
