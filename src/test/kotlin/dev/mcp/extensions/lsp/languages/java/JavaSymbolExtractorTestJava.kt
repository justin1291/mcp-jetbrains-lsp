package dev.mcp.extensions.lsp.languages.java

import com.intellij.openapi.application.ApplicationManager
import dev.mcp.extensions.lsp.JavaBaseTest
import dev.mcp.extensions.lsp.core.models.GetSymbolsArgs
import dev.mcp.extensions.lsp.core.models.SymbolKind
import dev.mcp.extensions.lsp.core.models.Visibility
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for JavaSymbolExtractor.
 * Tests the extractor directly without going through the tool layer.
 * Uses physical demo files that are copied by BaseTest.
 */
class JavaSymbolExtractorTestJava : JavaBaseTest() {
    
    private lateinit var extractor: JavaSymbolExtractor
    
    @BeforeEach
    override fun setUp() {
        super.setUp()
        extractor = JavaSymbolExtractor()
    }

    @Test
    fun testExtractSymbolsFromUserService() {
        ApplicationManager.getApplication().runReadAction {
            val userServicePath = "src/main/java/com/example/demo/UserService.java"
            
            // Get the PSI file that was copied by BaseTest
            val virtualFile = myFixture.findFileInTempDir(userServicePath)
            assertNotNull(virtualFile, "UserService.java should exist in test project")
            
            val psiFile = myFixture.psiManager.findFile(virtualFile)
            assertNotNull(psiFile, "Should be able to get PSI file")
            
            val symbols = extractor.extractSymbolsFlat(psiFile, GetSymbolsArgs(
                filePath = userServicePath,
                hierarchical = false,
                includeImports = true
            ))
            
            // Power Assert will show exactly what symbols were found
            assert(symbols.isNotEmpty()) { "Should extract symbols from UserService.java" }
            
            // Check for UserService class
            val classes = symbols.filter { it.kind == SymbolKind.Class }
            assert(classes.any { it.name == "UserService" }) {
                "Should find UserService class. Found classes: ${classes.map { it.name }}"
            }
            
            // Check for key methods - Power Assert will show which methods were actually found
            val methods = symbols.filter { it.kind == SymbolKind.Method }
            val methodNames = methods.map { it.name }
            assert(methods.any { it.name == "addUser" }) {
                "Should find addUser method. Found methods: $methodNames"
            }
            assert(methods.any { it.name == "findUser" }) {
                "Should find findUser method. Found methods: $methodNames"
            }
            assert(methods.any { it.name == "removeUser" }) {
                "Should find removeUser method. Found methods: $methodNames"
            }
            assert(methods.any { it.name == "getUserCount" }) {
                "Should find getUserCount method. Found methods: $methodNames"
            }
            assert(methods.any { it.name == "isValidUser" }) {
                "Should find isValidUser static method. Found methods: $methodNames"
            }
            
            // Check for constants (now marked as "constant" not "field")
            val constants = symbols.filter { it.kind.value == "constant" }
            val constantNames = constants.map { it.name }
            assert(constants.any { it.name == "DEFAULT_ROLE" }) {
                "Should find DEFAULT_ROLE constant. Found constants: $constantNames"
            }
            assert(constants.any { it.name == "MAX_USERS" }) {
                "Should find MAX_USERS constant. Found constants: $constantNames"
            }
            
            // Check for nested types
            val enums = symbols.filter { it.kind == SymbolKind.Enum }
            val enumNames = enums.map { it.name }
            assert(enums.any { it.name == "UserEvent" }) {
                "Should find UserEvent enum. Found enums: $enumNames"
            }
            
            val interfaces = symbols.filter { it.kind == SymbolKind.Interface }
            val interfaceNames = interfaces.map { it.name }
            assert(interfaces.any { it.name == "UserListener" }) {
                "Should find UserListener interface. Found interfaces: $interfaceNames"
            }
            
            // Check for inner class
            val innerClasses = classes.filter { it.name == "UserSession" }
            assert(innerClasses.isNotEmpty()) {
                "Should find UserSession inner class. Found classes: ${classes.map { it.name }}"
            }
            
            // Check for imports
            val imports = symbols.filter { it.kind == SymbolKind.Import }
            val importNames = imports.map { it.name }
            assert(imports.any { it.name.contains("java.util") }) {
                "Should find java.util imports. Found imports: $importNames"
            }
        }
    }

    @Test
    fun testExtractSymbolsFromUser() {
        ApplicationManager.getApplication().runReadAction {
            val userPath = "src/main/java/com/example/demo/User.java"
            
            val virtualFile = myFixture.findFileInTempDir(userPath)
            assertNotNull(virtualFile, "User.java should exist in test project")
            
            val psiFile = myFixture.psiManager.findFile(virtualFile)
            assertNotNull(psiFile, "Should be able to get PSI file")
            
            val symbols = extractor.extractSymbolsFlat(psiFile, GetSymbolsArgs(
                filePath = userPath,
                hierarchical = false,
                includeImports = true
            ))
            
            assertTrue(symbols.isNotEmpty(), "Should extract symbols from User.java")
            
            // Check for User class
            val classes = symbols.filter { it.kind == SymbolKind.Class }
            assertTrue(classes.any { it.name == "User" }, 
                "Should find User class")
            assertTrue(classes.any { it.name == "BaseEntity" }, 
                "Should find BaseEntity class")
            
            // Check for getter/setter methods
            val methods = symbols.filter { it.kind == SymbolKind.Method }
            assertTrue(methods.any { it.name == "getId" }, 
                "Should find getId method")
            assertTrue(methods.any { it.name == "getName" }, 
                "Should find getName method")
            assertTrue(methods.any { it.name == "setId" }, 
                "Should find setId method")
            assertTrue(methods.any { it.name == "setName" }, 
                "Should find setName method")
            
            // Check for Object method overrides
            assertTrue(methods.any { it.name == "toString" }, 
                "Should find toString method")
            assertTrue(methods.any { it.name == "equals" }, 
                "Should find equals method")
            assertTrue(methods.any { it.name == "hashCode" }, 
                "Should find hashCode method")
            
            // Check for fields
            val fields = symbols.filter { it.kind == SymbolKind.Field }
            assertTrue(fields.any { it.name == "id" }, 
                "Should find id field")
            assertTrue(fields.any { it.name == "name" }, 
                "Should find name field")
            assertTrue(fields.any { it.name == "email" }, 
                "Should find email field")
            
            // Check for constructors
            val constructors = symbols.filter { it.kind == SymbolKind.Constructor }
            assertTrue(constructors.any { it.name == "User" }, 
                "Should find User constructor")
            
            // Should find both constructors (default and parameterized)
            assertTrue(constructors.count { it.name == "User" } >= 1, 
                "Should find at least one User constructor")
        }
    }

    @Test
    fun testExtractSymbolsFromApiController() {
        ApplicationManager.getApplication().runReadAction {
            val apiControllerPath = "src/main/java/com/example/demo/ApiController.java"
            
            val virtualFile = myFixture.findFileInTempDir(apiControllerPath)
            assertNotNull(virtualFile, "ApiController.java should exist in test project")
            
            val psiFile = myFixture.psiManager.findFile(virtualFile)
            assertNotNull(psiFile, "Should be able to get PSI file")
            
            val symbols = extractor.extractSymbolsFlat(psiFile, GetSymbolsArgs(
                filePath = apiControllerPath,
                hierarchical = false,
                includeImports = true
            ))
            
            assertTrue(symbols.isNotEmpty(), "Should extract symbols from ApiController.java")
            
            // Check for ApiController class
            val classes = symbols.filter { it.kind == SymbolKind.Class }
            assertTrue(classes.any { it.name == "ApiController" }, 
                "Should find ApiController class")
            assertTrue(classes.any { it.name == "ApiResponse" }, 
                "Should find ApiResponse class")
            
            // Check for API methods
            val methods = symbols.filter { it.kind == SymbolKind.Method }
            assertTrue(methods.any { it.name == "createUser" }, 
                "Should find createUser method")
            assertTrue(methods.any { it.name == "getUser" }, 
                "Should find getUser method")
            assertTrue(methods.any { it.name == "updateUser" }, 
                "Should find updateUser method")
            assertTrue(methods.any { it.name == "deleteUser" }, 
                "Should find deleteUser method")
            
            // Check for constants (now marked as "constant" not "field")
            val constants = symbols.filter { it.kind.value == "constant" }
            assertTrue(constants.any { it.name == "STATUS_SUCCESS" }, 
                "Should find STATUS_SUCCESS constant")
            assertTrue(constants.any { it.name == "STATUS_ERROR" }, 
                "Should find STATUS_ERROR constant")
        }
    }

    @Test
    fun testHierarchicalExtraction() {
        ApplicationManager.getApplication().runReadAction {
            val userServicePath = "src/main/java/com/example/demo/UserService.java"
            
            val virtualFile = myFixture.findFileInTempDir(userServicePath)
            assertNotNull(virtualFile, "UserService.java should exist in test project")
            
            val psiFile = myFixture.psiManager.findFile(virtualFile)
            assertNotNull(psiFile, "Should be able to get PSI file")
            
            val symbols = extractor.extractSymbolsHierarchical(psiFile, GetSymbolsArgs(
                filePath = userServicePath,
                hierarchical = true,
                includeImports = true,
                symbolTypes = null  // Don't filter types to ensure we get all children
            ))
            
            println("\n=== HIERARCHICAL EXTRACTION DEBUG ===")
            println("Total symbols: ${symbols.size}")
            symbols.forEach { symbol ->
                println("Top-level: ${symbol.name} (${symbol.kind.value})")
                if (symbol.children != null) {
                    println("  Has ${symbol.children.size} children:")
                    symbol.children.forEach { child ->
                        println("    - ${child.name} (${child.kind.value})")
                    }
                } else {
                    println("  No children")
                }
            }
            
            assertTrue(symbols.isNotEmpty(), "Should extract hierarchical symbols")
            
            // Check top-level structure
            val imports = symbols.filter { it.kind == SymbolKind.Import }
            assertTrue(imports.isNotEmpty(), "Should have imports at top level")
            
            val topLevelClasses = symbols.filter { it.kind == SymbolKind.Class }
            assertTrue(topLevelClasses.any { it.name == "UserService" }, 
                "Should find UserService at top level")
            
            // Check for nested types (enum and interface)
            val userServiceClass = topLevelClasses.find { it.name == "UserService" }
            assertNotNull(userServiceClass, "Should find UserService class")
            
            val children = userServiceClass.children
            if (children == null) {
                println("DEBUG: UserService children is null!")
                println("DEBUG: UserService details: ${userServiceClass}")
                // Force test to fail with debug output
                throw AssertionError("UserService has no children - check console output above")
            }
            assertNotNull(children, "UserService should have children")
            
            val nestedTypes = children.filter { 
                it.kind == SymbolKind.Enum || it.kind == SymbolKind.Interface || it.kind == SymbolKind.Class
            }
            assertTrue(nestedTypes.any { it.name == "UserEvent" }, 
                "Should find UserEvent enum as nested")
            assertTrue(nestedTypes.any { it.name == "UserListener" }, 
                "Should find UserListener interface as nested")
            assertTrue(nestedTypes.any { it.name == "UserSession" }, 
                "Should find UserSession class as nested")
        }
    }

    @Test
    fun testSymbolTypeFiltering() {
        ApplicationManager.getApplication().runReadAction {
            val userServicePath = "src/main/java/com/example/demo/UserService.java"
            
            val virtualFile = myFixture.findFileInTempDir(userServicePath)
            assertNotNull(virtualFile, "UserService.java should exist in test project")
            
            val psiFile = myFixture.psiManager.findFile(virtualFile)
            assertNotNull(psiFile, "Should be able to get PSI file")
            
            // Test filtering only methods
            val methodSymbols = extractor.extractSymbolsFlat(psiFile, GetSymbolsArgs(
                filePath = userServicePath,
                hierarchical = false,
                symbolTypes = listOf("method")
            ))
            
            assertTrue(methodSymbols.all { it.kind.value == "method" }, 
                "Should only have methods")
            assertTrue(methodSymbols.any { it.name == "addUser" }, 
                "Should include addUser method")
            
            // Test filtering only fields (should NOT include constants now)
            val fieldSymbols = extractor.extractSymbolsFlat(psiFile, GetSymbolsArgs(
                filePath = userServicePath,
                hierarchical = false,
                symbolTypes = listOf("field")
            ))
            
            assertTrue(fieldSymbols.all { it.kind.value == "field" }, "Should only have fields")
            // DEFAULT_ROLE is now a constant, not a field
            assertTrue(fieldSymbols.none { it.name == "DEFAULT_ROLE" }, 
                "Should NOT include DEFAULT_ROLE constant when filtering for fields")
            
            // Test filtering constructors
            val constructorSymbols = extractor.extractSymbolsFlat(psiFile, GetSymbolsArgs(
                filePath = userServicePath,
                hierarchical = false,
                symbolTypes = listOf("constructor")
            ))
            
            assertTrue(constructorSymbols.all { it.kind.value == "constructor" }, 
                "Should only have constructors")
        }
    }

    @Test
    fun testSemanticFiltering() {
        ApplicationManager.getApplication().runReadAction {
            val userServicePath = "src/main/java/com/example/demo/UserService.java"
            
            val virtualFile = myFixture.findFileInTempDir(userServicePath)
            assertNotNull(virtualFile, "UserService.java should exist in test project")
            
            val psiFile = myFixture.psiManager.findFile(virtualFile)
            assertNotNull(psiFile, "Should be able to get PSI file")
            
            // Get all symbols to see what we have
            val allSymbols = extractor.extractSymbolsFlat(psiFile, GetSymbolsArgs(
                filePath = userServicePath,
                hierarchical = false
            ))
            
            // Test constant filtering (static finals)
            val constantSymbols = extractor.extractSymbolsFlat(psiFile, GetSymbolsArgs(
                filePath = userServicePath,
                symbolTypes = listOf("constant")
            ))
            
            // Should include static finals, NOT enum members
            assert(constantSymbols.all { it.kind.value == "constant" }) {
                "Should only have constants, found: ${constantSymbols.map { "${it.name} (${it.kind.value})" }}"
            }
            assert(constantSymbols.any { it.name == "DEFAULT_ROLE" }) {
                "Should find DEFAULT_ROLE as constant"
            }
            assert(constantSymbols.any { it.name == "MAX_USERS" }) {
                "Should find MAX_USERS as constant"
            }
            
            // Enum members from UserEvent should NOT be included as constants
            assert(constantSymbols.none { it.name == "CREATED" }) {
                "Should NOT find CREATED enum member as constant"
            }
            assert(constantSymbols.none { it.name == "UPDATED" }) {
                "Should NOT find UPDATED enum member as constant"
            }
            
            // Test enum_member filtering  
            val enumMemberSymbols = extractor.extractSymbolsFlat(psiFile, GetSymbolsArgs(
                filePath = userServicePath,
                symbolTypes = listOf("enum_member")
            ))
            
            assert(enumMemberSymbols.all { it.kind.value == "enum_member" }) {
                "Should only have enum members, found: ${enumMemberSymbols.map { "${it.name} (${it.kind.value})" }}"
            }
            assert(enumMemberSymbols.any { it.name == "CREATED" }) {
                "Should find CREATED as enum member"
            }
            assert(enumMemberSymbols.any { it.name == "UPDATED" }) {
                "Should find UPDATED as enum member"
            }
            assert(enumMemberSymbols.any { it.name == "DELETED" }) {
                "Should find DELETED as enum member"
            }
            assert(enumMemberSymbols.none { it.name == "DEFAULT_ROLE" }) {
                "Should NOT find DEFAULT_ROLE as enum member"
            }
            
            // Test field filtering (pure fields only)
            val fieldSymbols = extractor.extractSymbolsFlat(psiFile, GetSymbolsArgs(
                filePath = userServicePath,
                symbolTypes = listOf("field")  
            ))
            
            assert(fieldSymbols.all { it.kind.value == "field" }) {
                "Should only have fields, found: ${fieldSymbols.map { "${it.name} (${it.kind.value})" }}"
            }
            
            // Should include regular fields
            val fieldNames = fieldSymbols.map { it.name }
            assert(fieldSymbols.any { it.name == "users" || it.name == "logger" || it.name == "userListeners" }) {
                "Should find instance fields. Found fields: $fieldNames"
            }
            
            // Should NOT include constants or enum members in field results
            assert(fieldSymbols.none { it.name == "DEFAULT_ROLE" }) {
                "Should NOT find DEFAULT_ROLE constant in field results"
            }
            assert(fieldSymbols.none { it.name == "MAX_USERS" }) {
                "Should NOT find MAX_USERS constant in field results"
            }
            assert(fieldSymbols.none { it.name == "CREATED" }) {
                "Should NOT find CREATED enum member in field results"
            }
            
            // Test combined filtering for data-related symbols
            val dataSymbols = extractor.extractSymbolsFlat(psiFile, GetSymbolsArgs(
                filePath = userServicePath,
                symbolTypes = listOf("field", "constant", "enum_member")
            ))
            
            // Should include all three types when explicitly requested
            assert(dataSymbols.any { it.kind.value == "field" }) {
                "Combined filter should include fields"
            }
            assert(dataSymbols.any { it.kind.value == "constant" }) {
                "Combined filter should include constants"
            }
            assert(dataSymbols.any { it.kind.value == "enum_member" }) {
                "Combined filter should include enum members"
            }
        }
    }

    @Test
    fun testModifiersAndMetadata() {
        ApplicationManager.getApplication().runReadAction {
            val userPath = "src/main/java/com/example/demo/User.java"
            
            val virtualFile = myFixture.findFileInTempDir(userPath)
            assertNotNull(virtualFile, "User.java should exist in test project")
            
            val psiFile = myFixture.psiManager.findFile(virtualFile)
            assertNotNull(psiFile, "Should be able to get PSI file")
            
            val symbols = extractor.extractSymbolsFlat(psiFile, GetSymbolsArgs(
                filePath = userPath,
                hierarchical = false
            ))
            
            // Debug all symbols to see what we're getting
            println("All symbols in User.java:")
            symbols.forEach { symbol ->
                println("  ${symbol.name} - kind: ${symbol.kind.value}, modifiers: ${symbol.modifiers}")
            }
            
            // Test isDeprecated field - OLD_DEFAULT_ROLE is now a constant
            val oldDefaultRole = symbols.find { it.name == "OLD_DEFAULT_ROLE" }
            assertNotNull(oldDefaultRole, "Should find OLD_DEFAULT_ROLE")
            assertEquals("constant", oldDefaultRole.kind.value, "OLD_DEFAULT_ROLE should be a constant")
            assertTrue(oldDefaultRole.isDeprecated, "OLD_DEFAULT_ROLE should be marked as deprecated")
            
            val validateMethod = symbols.find { it.name == "validate" }
            assertNotNull(validateMethod, "Should find validate method")
            assertTrue(validateMethod.isDeprecated, "validate method should be marked as deprecated")
            
            // Test documentation field
            assertTrue(oldDefaultRole.documentation?.isPresent == true, 
                "OLD_DEFAULT_ROLE should have documentation")
            
            val toStringMethod = symbols.find { it.name == "toString" && it.kind == SymbolKind.Method }
            assertNotNull(toStringMethod, "Should find toString method")
            assertTrue(toStringMethod.documentation?.isPresent == true, 
                "toString method should have documentation")
            
            // Test override field
            if (toStringMethod.overrides == null) {
                println("ERROR: toString.overrides is null!")
                println("toString method details:")
                println("  name: ${toStringMethod.name}")
                println("  decorators: ${toStringMethod.decorators}")
                println("  modifiers: ${toStringMethod.modifiers}")
            }
            assertNotNull(toStringMethod.overrides, "toString should be marked as override")
            
            val equalsMethod = symbols.find { it.name == "equals" }
            assertNotNull(equalsMethod, "Should find equals method")
            assertNotNull(equalsMethod.overrides, "equals should be marked as override")
            
            val hashCodeMethod = symbols.find { it.name == "hashCode" }
            assertNotNull(hashCodeMethod, "Should find hashCode method")
            assertNotNull(hashCodeMethod.overrides, "hashCode should be marked as override")
            
            // Test overrides details - adding debug output
            println("toString overrides: ${toStringMethod.overrides}")
            println("equals overrides: ${equalsMethod.overrides}")
            println("hashCode overrides: ${hashCodeMethod.overrides}")
            
            assertEquals("java.lang.Object", toStringMethod.overrides.parentClass, 
                "toString should override Object.toString")
            assertEquals("toString", toStringMethod.overrides.methodName)
            
            // Test visibility field
            assertEquals(Visibility.PROTECTED, oldDefaultRole.visibility, 
                "OLD_DEFAULT_ROLE should be protected")
            assertEquals(Visibility.PROTECTED, validateMethod.visibility, 
                "validate method should be protected")
            
            val updateTimestamp = symbols.find { it.name == "updateTimestamp" }
            assertNotNull(updateTimestamp, "Should find updateTimestamp method")
            assertEquals(Visibility.PRIVATE, updateTimestamp.visibility, 
                "updateTimestamp should be private")
            
            val notifyChange = symbols.find { it.name == "notifyChange" }
            assertNotNull(notifyChange, "Should find notifyChange method")
            assertEquals(Visibility.PACKAGE, notifyChange.visibility, 
                "notifyChange should be package-private")
            
            // Test decorators field (annotations)
            assertTrue(oldDefaultRole.decorators?.isNotEmpty() == true, 
                "OLD_DEFAULT_ROLE should have decorators")
            assertTrue(oldDefaultRole.decorators.any { it.name == "Deprecated" }, 
                "OLD_DEFAULT_ROLE decorators should include @Deprecated")
            
            assertTrue(toStringMethod.decorators?.isNotEmpty() == true, 
                "toString should have decorators")
            assertTrue(toStringMethod.decorators.any { it.name == "Override" }, 
                "toString decorators should include @Override")
            
            assertTrue(validateMethod.decorators?.isNotEmpty() == true, 
                "validate should have decorators")
            assertTrue(validateMethod.decorators.any { it.name == "Deprecated" }, 
                "validate decorators should include @Deprecated")
            assertTrue(validateMethod.decorators.any { it.name == "SuppressWarnings" }, 
                "validate decorators should include @SuppressWarnings")
        }
    }

    @Test
    fun testMethodParametersAndReturnTypes() {
        ApplicationManager.getApplication().runReadAction {
            val userServicePath = "src/main/java/com/example/demo/UserService.java"
            
            val virtualFile = myFixture.findFileInTempDir(userServicePath)
            assertNotNull(virtualFile, "UserService.java should exist in test project")
            
            val psiFile = myFixture.psiManager.findFile(virtualFile)
            assertNotNull(psiFile, "Should be able to get PSI file")
            
            val symbols = extractor.extractSymbolsFlat(psiFile, GetSymbolsArgs(
                filePath = userServicePath,
                hierarchical = false
            ))
            
            // Check addUser method
            val addUser = symbols.find { it.name == "addUser" && it.kind == SymbolKind.Method }
            assertNotNull(addUser, "Should find addUser method")
            assertEquals("boolean", addUser.typeInfo?.displayName, "addUser should return boolean")
            val addUserParams = addUser.languageData?.get("parameters")
            assertTrue(addUserParams?.contains("User") == true, "addUser should have User parameter")
            
            // Check findUser method
            val findUser = symbols.find { it.name == "findUser" }
            assertNotNull(findUser, "Should find findUser method")
            assertTrue(findUser.typeInfo?.displayName?.contains("Optional") == true, 
                "findUser should return Optional")
            val findUserParams = findUser.languageData?.get("parameters")
            assertTrue(findUserParams?.contains("String") == true, "findUser should have String parameter")
            
            // Check getUserCount method
            val getUserCount = symbols.find { it.name == "getUserCount" }
            assertNotNull(getUserCount, "Should find getUserCount method")
            assertEquals("int", getUserCount.typeInfo?.displayName, 
                "getUserCount should return int")
            assertEquals("0", getUserCount.languageData?.get("parameterCount"), 
                "getUserCount should have no parameters")
            
            // Check static method modifiers
            val isValidUser = symbols.find { it.name == "isValidUser" }
            assertNotNull(isValidUser, "Should find isValidUser method")
            assertTrue(isValidUser.modifiers.contains("static"), 
                "isValidUser should be static")
            assertTrue(isValidUser.modifiers.contains("public"), 
                "isValidUser should be public")
        }
    }

    @Test
    fun testNestedClassExtraction() {
        ApplicationManager.getApplication().runReadAction {
            val userServicePath = "src/main/java/com/example/demo/UserService.java"
            
            val virtualFile = myFixture.findFileInTempDir(userServicePath)
            assertNotNull(virtualFile, "UserService.java should exist in test project")
            
            val psiFile = myFixture.psiManager.findFile(virtualFile)
            assertNotNull(psiFile, "Should be able to get PSI file")
            
            val symbols = extractor.extractSymbolsHierarchical(psiFile, GetSymbolsArgs(
                filePath = userServicePath,
                hierarchical = true,
                symbolTypes = null  // Don't filter types to ensure we get all children
            ))
            
            // Should find both top-level UserService and nested UserSession
            val topLevelClasses = symbols.filter { it.kind == SymbolKind.Class }
            val userService = topLevelClasses.find { it.name == "UserService" }
            assertNotNull(userService, "Should find UserService class")
            
            // UserSession should be found as a nested class
            val userServiceChildren = userService.children
            assertNotNull(userServiceChildren, "UserService should have children")
            
            val nestedClasses = userServiceChildren.filter { it.kind == SymbolKind.Class }
            val userSession = nestedClasses.find { it.name == "UserSession" }
            assertNotNull(userSession, "Should find UserSession as nested class")
            
            // Check that UserSession has its own methods
            val userSessionChildren = userSession.children
            assertNotNull(userSessionChildren, "UserSession should have children")
            
            val sessionConstructors = userSessionChildren.filter { it.kind == SymbolKind.Constructor }
            assertTrue(sessionConstructors.any { it.name == "UserSession" }, 
                "UserSession should have constructor")
            
            val sessionMethods = userSessionChildren.filter { it.kind == SymbolKind.Method }
            assertTrue(sessionMethods.any { it.name == "getSessionId" }, 
                "UserSession should have getSessionId method")
            assertTrue(sessionMethods.any { it.name == "getUserId" }, 
                "UserSession should have getUserId method")
        }
    }

    @Test
    fun testLineNumberAccuracy() {
        ApplicationManager.getApplication().runReadAction {
            val userPath = "src/main/java/com/example/demo/User.java"
            
            val virtualFile = myFixture.findFileInTempDir(userPath)
            assertNotNull(virtualFile, "User.java should exist in test project")
            
            val psiFile = myFixture.psiManager.findFile(virtualFile)
            assertNotNull(psiFile, "Should be able to get PSI file")
            
            val symbols = extractor.extractSymbolsFlat(psiFile, GetSymbolsArgs(
                filePath = userPath,
                hierarchical = false
            ))
            
            // Check that line numbers are reasonable (> 0)
            symbols.forEach { symbol ->
                assertTrue(symbol.location.lineNumber > 0, 
                    "${symbol.name} should have positive line number")
            }
            
            // Check relative ordering of some symbols
            val userClass = symbols.find { it.name == "User" && it.kind == SymbolKind.Class }
            val getId = symbols.find { it.name == "getId" }
            val getName = symbols.find { it.name == "getName" }
            
            assertNotNull(userClass, "Should find User class")
            assertNotNull(getId, "Should find getId method")
            assertNotNull(getName, "Should find getName method")
            
            assertTrue(getId.location.lineNumber > userClass.location.lineNumber, 
                "getId method should be after User class declaration")
            assertTrue(getName.location.lineNumber > getId.location.lineNumber, 
                "getName should be after getId in the file")
        }
    }
}
