package dev.mcp.extensions.lsp.languages.java

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import dev.mcp.extensions.lsp.JavaBaseTest
import dev.mcp.extensions.lsp.core.models.GetSymbolsArgs
import dev.mcp.extensions.lsp.core.models.SymbolKind
import dev.mcp.extensions.lsp.core.models.Visibility
import org.junit.jupiter.api.Test

/**
 * Unit tests for JavaSymbolExtractor.
 * Tests the extractor directly without going through the tool layer.
 * Uses physical demo files that are copied by BaseTest.
 */
class JavaSymbolExtractorTest : JavaBaseTest() {

    private val extractor: JavaSymbolExtractor = JavaSymbolExtractor()

    @Suppress("DEPRECATION")
    private fun getPsiFileByName(filename: String) = FilenameIndex
        .getFilesByName(fixtureProject, filename, GlobalSearchScope.allScope(fixtureProject))
        .firstOrNull()

    @Test
    fun testExtractSymbolsFromUserService() {
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getPsiFileByName("UserService.java")
            require(psiFile != null) { "UserService.java should exist in test project" }

            val symbols = extractor.extractSymbolsFlat(
                psiFile, GetSymbolsArgs(
                    filePath = "src/main/java/com/example/demo/UserService.java",
                    hierarchical = false,
                    includeImports = true
                )
            )

            assert(symbols.isNotEmpty()) { "Should extract symbols from UserService.java" }

            val classes = symbols.filter { it.kind == SymbolKind.Class }
            assert(classes.any { it.name == "UserService" }) {
                "Should find UserService class. Found classes: ${classes.map { it.name }}"
            }

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

            val constants = symbols.filter { it.kind.value == "constant" }
            val constantNames = constants.map { it.name }
            assert(constants.any { it.name == "DEFAULT_ROLE" }) {
                "Should find DEFAULT_ROLE constant. Found constants: $constantNames"
            }
            assert(constants.any { it.name == "MAX_USERS" }) {
                "Should find MAX_USERS constant. Found constants: $constantNames"
            }

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

            val innerClasses = classes.filter { it.name == "UserSession" }
            assert(innerClasses.isNotEmpty()) {
                "Should find UserSession inner class. Found classes: ${classes.map { it.name }}"
            }

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
            val psiFile = getPsiFileByName("User.java")
            require(psiFile != null) { "User.java should exist in test project" }

            val symbols = extractor.extractSymbolsFlat(
                psiFile, GetSymbolsArgs(
                    filePath = "src/main/java/com/example/demo/User.java",
                    hierarchical = false,
                    includeImports = true
                )
            )

            assert(symbols.isNotEmpty()) { "Should extract symbols from User.java" }

            val classes = symbols.filter { it.kind == SymbolKind.Class }
            assert(classes.any { it.name == "User" }) { "Should find User class" }
            assert(classes.any { it.name == "BaseEntity" }) { "Should find BaseEntity class" }

            val methods = symbols.filter { it.kind == SymbolKind.Method }
            assert(methods.any { it.name == "getId" }) { "Should find getId method" }
            assert(methods.any { it.name == "getName" }) { "Should find getName method" }
            assert(methods.any { it.name == "setId" }) { "Should find setId method" }
            assert(methods.any { it.name == "setName" }) { "Should find setName method" }

            assert(methods.any { it.name == "toString" }) { "Should find toString method" }
            assert(methods.any { it.name == "equals" }) { "Should find equals method" }
            assert(methods.any { it.name == "hashCode" }) { "Should find hashCode method" }

            val fields = symbols.filter { it.kind == SymbolKind.Field }
            assert(fields.any { it.name == "id" }) { "Should find id field" }
            assert(fields.any { it.name == "name" }) { "Should find name field" }
            assert(fields.any { it.name == "email" }) { "Should find email field" }

            val constructors = symbols.filter { it.kind == SymbolKind.Constructor }
            assert(constructors.any { it.name == "User" }) { "Should find User constructor" }

            assert(constructors.count { it.name == "User" } >= 1) {
                "Should find at least one User constructor"
            }
        }
    }

    @Test
    fun testExtractSymbolsFromApiController() {
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getPsiFileByName("ApiController.java")
            require(psiFile != null) { "ApiController.java should exist in test project" }

            val symbols = extractor.extractSymbolsFlat(
                psiFile, GetSymbolsArgs(
                    filePath = "src/main/java/com/example/demo/ApiController.java",
                    hierarchical = false,
                    includeImports = true
                )
            )

            assert(symbols.isNotEmpty()) { "Should extract symbols from ApiController.java" }

            val classes = symbols.filter { it.kind == SymbolKind.Class }
            assert(classes.any { it.name == "ApiController" }) { "Should find ApiController class" }
            assert(classes.any { it.name == "ApiResponse" }) { "Should find ApiResponse class" }

            val methods = symbols.filter { it.kind == SymbolKind.Method }
            assert(methods.any { it.name == "createUser" }) { "Should find createUser method" }
            assert(methods.any { it.name == "getUser" }) { "Should find getUser method" }
            assert(methods.any { it.name == "updateUser" }) { "Should find updateUser method" }
            assert(methods.any { it.name == "deleteUser" }) { "Should find deleteUser method" }

            val constants = symbols.filter { it.kind.value == "constant" }
            assert(constants.any { it.name == "STATUS_SUCCESS" }) { "Should find STATUS_SUCCESS constant" }
            assert(constants.any { it.name == "STATUS_ERROR" }) { "Should find STATUS_ERROR constant" }
        }
    }

    @Test
    fun testHierarchicalExtraction() {
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getPsiFileByName("UserService.java")
            require(psiFile != null) { "UserService.java should exist in test project" }

            val symbols = extractor.extractSymbolsHierarchical(
                psiFile, GetSymbolsArgs(
                    filePath = "src/main/java/com/example/demo/UserService.java",
                    hierarchical = true,
                    includeImports = true,
                    symbolTypes = null  // Don't filter types to ensure we get all children
                )
            )

            assert(symbols.isNotEmpty()) { "Should extract hierarchical symbols" }

            val imports = symbols.filter { it.kind == SymbolKind.Import }
            assert(imports.isNotEmpty()) { "Should have imports at top level" }

            val topLevelClasses = symbols.filter { it.kind == SymbolKind.Class }
            assert(topLevelClasses.any { it.name == "UserService" }) {
                "Should find UserService at top level"
            }

            val userServiceClass = topLevelClasses.find { it.name == "UserService" }
            require(userServiceClass != null) { "Should find UserService class" }

            val children = userServiceClass.children

            val nestedTypes = children?.filter {
                it.kind == SymbolKind.Enum || it.kind == SymbolKind.Interface || it.kind == SymbolKind.Class
            }
            assert(nestedTypes?.any { it.name == "UserEvent" } ?: false ) {
                "Should find UserEvent enum as nested"
            }
            assert(nestedTypes?.any { it.name == "UserListener" } ?: false ) {
                "Should find UserListener interface as nested"
            }
            assert(nestedTypes?.any { it.name == "UserSession" } ?: false) {
                "Should find UserSession class as nested"
            }
        }
    }

    @Test
    fun testSymbolTypeFiltering() {
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getPsiFileByName("UserService.java")
            require(psiFile != null) { "UserService.java should exist in test project" }

            val methodSymbols = extractor.extractSymbolsFlat(
                psiFile, GetSymbolsArgs(
                    filePath = "src/main/java/com/example/demo/UserService.java",
                    hierarchical = false,
                    symbolTypes = listOf("method")
                )
            )

            assert(methodSymbols.all { it.kind.value == "method" }) { "Should only have methods" }
            assert(methodSymbols.any { it.name == "addUser" }) { "Should include addUser method" }

            val fieldSymbols = extractor.extractSymbolsFlat(
                psiFile, GetSymbolsArgs(
                    filePath = "src/main/java/com/example/demo/UserService.java",
                    hierarchical = false,
                    symbolTypes = listOf("field")
                )
            )

            assert(fieldSymbols.all { it.kind.value == "field" }) { "Should only have fields" }
            assert(fieldSymbols.none { it.name == "DEFAULT_ROLE" }) {
                "Should NOT include DEFAULT_ROLE constant when filtering for fields"
            }

            val constructorSymbols = extractor.extractSymbolsFlat(
                psiFile, GetSymbolsArgs(
                    filePath = "src/main/java/com/example/demo/UserService.java",
                    hierarchical = false,
                    symbolTypes = listOf("constructor")
                )
            )

            assert(constructorSymbols.all { it.kind.value == "constructor" }) {
                "Should only have constructors"
            }
        }
    }

    @Test
    fun testSemanticFiltering() {
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getPsiFileByName("UserService.java")
            require(psiFile != null) { "UserService.java should exist in test project" }

            val constantSymbols = extractor.extractSymbolsFlat(
                psiFile, GetSymbolsArgs(
                    filePath = "src/main/java/com/example/demo/UserService.java",
                    symbolTypes = listOf("constant")
                )
            )

            assert(constantSymbols.all { it.kind.value == "constant" }) {
                "Should only have constants, found: ${constantSymbols.map { "${it.name} (${it.kind.value})" }}"
            }
            assert(constantSymbols.any { it.name == "DEFAULT_ROLE" }) {
                "Should find DEFAULT_ROLE as constant"
            }
            assert(constantSymbols.any { it.name == "MAX_USERS" }) {
                "Should find MAX_USERS as constant"
            }

            assert(constantSymbols.none { it.name == "CREATED" }) {
                "Should NOT find CREATED enum member as constant"
            }
            assert(constantSymbols.none { it.name == "UPDATED" }) {
                "Should NOT find UPDATED enum member as constant"
            }

            val enumMemberSymbols = extractor.extractSymbolsFlat(
                psiFile, GetSymbolsArgs(
                    filePath = "src/main/java/com/example/demo/UserService.java",
                    symbolTypes = listOf("enum_member")
                )
            )

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

            val fieldSymbols = extractor.extractSymbolsFlat(
                psiFile, GetSymbolsArgs(
                    filePath = "src/main/java/com/example/demo/UserService.java",
                    symbolTypes = listOf("field")
                )
            )

            assert(fieldSymbols.all { it.kind.value == "field" }) {
                "Should only have fields, found: ${fieldSymbols.map { "${it.name} (${it.kind.value})" }}"
            }

            val fieldNames = fieldSymbols.map { it.name }
            assert(fieldSymbols.any { it.name == "users" || it.name == "logger" || it.name == "userListeners" }) {
                "Should find instance fields. Found fields: $fieldNames"
            }
            assert(fieldSymbols.none { it.name == "DEFAULT_ROLE" }) {
                "Should NOT find DEFAULT_ROLE constant in field results"
            }
            assert(fieldSymbols.none { it.name == "MAX_USERS" }) {
                "Should NOT find MAX_USERS constant in field results"
            }
            assert(fieldSymbols.none { it.name == "CREATED" }) {
                "Should NOT find CREATED enum member in field results"
            }

            val dataSymbols = extractor.extractSymbolsFlat(
                psiFile, GetSymbolsArgs(
                    filePath = "src/main/java/com/example/demo/UserService.java",
                    symbolTypes = listOf("field", "constant", "enum_member")
                )
            )

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
            val psiFile = getPsiFileByName("User.java")
            require(psiFile != null) { "User.java should exist in test project" }

            val symbols = extractor.extractSymbolsFlat(
                psiFile, GetSymbolsArgs(
                    filePath = "src/main/java/com/example/demo/User.java",
                    hierarchical = false
                )
            )

            val oldDefaultRole = symbols.find { it.name == "OLD_DEFAULT_ROLE" }
            require(oldDefaultRole != null) { "Should find OLD_DEFAULT_ROLE" }
            assert(oldDefaultRole.kind.value == "constant") { "OLD_DEFAULT_ROLE should be a constant" }
            assert(oldDefaultRole.isDeprecated) { "OLD_DEFAULT_ROLE should be marked as deprecated" }

            val validateMethod = symbols.find { it.name == "validate" }
            require(validateMethod != null) { "Should find validate method" }
            assert(validateMethod.isDeprecated) { "validate method should be marked as deprecated" }

            assert(oldDefaultRole.documentation?.isPresent == true) {
                "OLD_DEFAULT_ROLE should have documentation"
            }

            val toStringMethod = symbols.find { it.name == "toString" && it.kind == SymbolKind.Method }
            require(toStringMethod != null) { "Should find toString method" }
            assert(toStringMethod.documentation?.isPresent == true) {
                "toString method should have documentation"
            }

            require(toStringMethod.overrides != null) { "toString should be marked as override" }

            val equalsMethod = symbols.find { it.name == "equals" }
            require(equalsMethod != null) { "Should find equals method" }
            require(equalsMethod.overrides != null) { "equals should be marked as override" }

            val hashCodeMethod = symbols.find { it.name == "hashCode" }
            require(hashCodeMethod != null) { "Should find hashCode method" }
            require(hashCodeMethod.overrides != null) { "hashCode should be marked as override" }

            assert(toStringMethod.overrides.parentClass == "java.lang.Object") {
                "toString should override Object.toString"
            }
            assert(toStringMethod.overrides.methodName == "toString") {
                "Override method name should be toString"
            }

            assert(oldDefaultRole.visibility == Visibility.PROTECTED) {
                "OLD_DEFAULT_ROLE should be protected"
            }
            assert(validateMethod.visibility == Visibility.PROTECTED) {
                "validate method should be protected"
            }

            val updateTimestamp = symbols.find { it.name == "updateTimestamp" }
            require(updateTimestamp != null) { "Should find updateTimestamp method" }
            assert(updateTimestamp.visibility == Visibility.PRIVATE) {
                "updateTimestamp should be private"
            }

            val notifyChange = symbols.find { it.name == "notifyChange" }
            require(notifyChange != null) { "Should find notifyChange method" }
            assert(notifyChange.visibility == Visibility.PACKAGE) {
                "notifyChange should be package-private"
            }

            assert(oldDefaultRole.decorators?.isNotEmpty() == true) {
                "OLD_DEFAULT_ROLE should have decorators"
            }
            assert(oldDefaultRole.decorators?.any { it.name == "Deprecated" } == true) {
                "OLD_DEFAULT_ROLE decorators should include @Deprecated"
            }

            assert(toStringMethod.decorators?.isNotEmpty() == true) {
                "toString should have decorators"
            }
            assert(toStringMethod.decorators?.any { it.name == "Override" } == true) {
                "toString decorators should include @Override"
            }

            assert(validateMethod.decorators?.isNotEmpty() == true) {
                "validate should have decorators"
            }
            assert(validateMethod.decorators?.any { it.name == "Deprecated" } == true) {
                "validate decorators should include @Deprecated"
            }
            assert(validateMethod.decorators?.any { it.name == "SuppressWarnings" } == true) {
                "validate decorators should include @SuppressWarnings"
            }
        }
    }

    @Test
    fun testMethodParametersAndReturnTypes() {
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getPsiFileByName("UserService.java")
            require(psiFile != null) { "UserService.java should exist in test project" }

            val symbols = extractor.extractSymbolsFlat(
                psiFile, GetSymbolsArgs(
                    filePath = "src/main/java/com/example/demo/UserService.java",
                    hierarchical = false
                )
            )

            val addUser = symbols.find { it.name == "addUser" && it.kind == SymbolKind.Method }
            require(addUser != null) { "Should find addUser method" }
            assert(addUser.typeInfo?.displayName == "boolean") { "addUser should return boolean" }
            val addUserParams = addUser.languageData?.get("parameters")
            assert(addUserParams?.contains("User") == true) { "addUser should have User parameter" }

            val findUser = symbols.find { it.name == "findUser" }
            require(findUser != null) { "Should find findUser method" }
            assert(findUser.typeInfo?.displayName?.contains("Optional") == true) {
                "findUser should return Optional"
            }
            val findUserParams = findUser.languageData?.get("parameters")
            assert(findUserParams?.contains("String") == true) { "findUser should have String parameter" }

            val getUserCount = symbols.find { it.name == "getUserCount" }
            require(getUserCount != null) { "Should find getUserCount method" }
            assert(getUserCount.typeInfo?.displayName == "int") { "getUserCount should return int" }
            assert(getUserCount.languageData?.get("parameterCount") == "0") {
                "getUserCount should have no parameters"
            }

            val isValidUser = symbols.find { it.name == "isValidUser" }
            require(isValidUser != null) { "Should find isValidUser method" }
            assert(isValidUser.modifiers.contains("static")) { "isValidUser should be static" }
            assert(isValidUser.modifiers.contains("public")) { "isValidUser should be public" }
        }
    }

    @Test
    fun testNestedClassExtraction() {
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getPsiFileByName("UserService.java")
            require(psiFile != null) { "UserService.java should exist in test project" }

            val symbols = extractor.extractSymbolsHierarchical(
                psiFile, GetSymbolsArgs(
                    filePath = "src/main/java/com/example/demo/UserService.java",
                    hierarchical = true,
                    symbolTypes = null  // Don't filter types to ensure we get all children
                )
            )

            val topLevelClasses = symbols.filter { it.kind == SymbolKind.Class }
            val userService = topLevelClasses.find { it.name == "UserService" }
            require(userService != null) { "Should find UserService class" }

            val userServiceChildren = userService.children
            require(userServiceChildren != null) { "UserService should have children" }

            val nestedClasses = userServiceChildren.filter { it.kind == SymbolKind.Class }
            val userSession = nestedClasses.find { it.name == "UserSession" }
            require(userSession != null) { "Should find UserSession as nested class" }

            val userSessionChildren = userSession.children
            require(userSessionChildren != null) { "UserSession should have children" }

            val sessionConstructors = userSessionChildren.filter { it.kind == SymbolKind.Constructor }
            assert(sessionConstructors.any { it.name == "UserSession" }) {
                "UserSession should have constructor"
            }

            val sessionMethods = userSessionChildren.filter { it.kind == SymbolKind.Method }
            assert(sessionMethods.any { it.name == "getSessionId" }) {
                "UserSession should have getSessionId method"
            }
            assert(sessionMethods.any { it.name == "getUserId" }) {
                "UserSession should have getUserId method"
            }
        }
    }

    @Test
    fun testLineNumberAccuracy() {
        ApplicationManager.getApplication().runReadAction {
            val psiFile = getPsiFileByName("User.java")
            require(psiFile != null) { "User.java should exist in test project" }

            val symbols = extractor.extractSymbolsFlat(
                psiFile, GetSymbolsArgs(
                    filePath = "src/main/java/com/example/demo/User.java",
                    hierarchical = false
                )
            )

            symbols.forEach { symbol ->
                assert(symbol.location.lineNumber > 0) {
                    "${symbol.name} should have positive line number"
                }
            }

            val userClass = symbols.find { it.name == "User" && it.kind == SymbolKind.Class }
            val getId = symbols.find { it.name == "getId" }
            val getName = symbols.find { it.name == "getName" }

            require(userClass != null) { "Should find User class" }
            require(getId != null) { "Should find getId method" }
            require(getName != null) { "Should find getName method" }

            assert(getId.location.lineNumber > userClass.location.lineNumber) {
                "getId method should be after User class declaration"
            }
            assert(getName.location.lineNumber > getId.location.lineNumber) {
                "getName should be after getId in the file"
            }
        }
    }
}
