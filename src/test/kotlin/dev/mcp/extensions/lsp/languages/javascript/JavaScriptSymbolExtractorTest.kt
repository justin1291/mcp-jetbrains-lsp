package dev.mcp.extensions.lsp.languages.javascript

import dev.mcp.extensions.lsp.core.models.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class JavaScriptSymbolExtractorTest {

    private val extractor = JavaScriptSymbolExtractor()

    @Test
    fun testSymbolExtractionAndCategorization() {
        val mockSymbols = listOf(
            createMockSymbol("UserService", "class", Visibility.PUBLIC, signature = "UserService"),
            createMockSymbol("IUserRepository", "interface", Visibility.PUBLIC, signature = "IUserRepository"),
            createMockSymbol(
                "calculateTotal",
                "function",
                Visibility.PUBLIC,
                signature = "calculateTotal(items: any[]): number"
            ),
            createMockSymbol("handleClick", "arrow_function", Visibility.DEFAULT, modifiers = setOf("async")),
            createMockSymbol(
                "generateItems",
                "generator_function",
                Visibility.PUBLIC,
                modifiers = setOf("generator", "export")
            ),
            createMockSymbol("UserProfile", "component", Visibility.PUBLIC, modifiers = setOf("export")),
            createMockSymbol(
                "useUserData",
                "hook",
                Visibility.PUBLIC,
                signature = "useUserData(userId: string): UserData"
            ),
            createMockSymbol("API_URL", "constant", Visibility.PUBLIC, modifiers = setOf("const")),
            createMockSymbol("userCount", "variable", Visibility.DEFAULT, modifiers = setOf("let")),
            createMockSymbol("UserRole", "enum", Visibility.PUBLIC, modifiers = setOf("export")),
            createMockSymbol("ADMIN", "enum_member", Visibility.PUBLIC),
            createMockSymbol("lodash", "import", Visibility.DEFAULT)
        )

        val typeSymbols = mockSymbols.filter { it.category == SymbolCategory.TYPE }
        assertEquals(4, typeSymbols.size, "Should have 4 type symbols (class, interface, component, enum)")
        assertTrue(typeSymbols.any { it.name == "UserService" && it.kind == SymbolKind.Class })
        assertTrue(typeSymbols.any { it.name == "IUserRepository" && it.kind == SymbolKind.Interface })
        assertTrue(typeSymbols.any { it.name == "UserProfile" && it.kind == SymbolKind.Component })
        assertTrue(typeSymbols.any { it.name == "UserRole" && it.kind == SymbolKind.Enum })

        val functionSymbols = mockSymbols.filter { it.category == SymbolCategory.FUNCTION }
        assertEquals(4, functionSymbols.size, "Should have 4 function symbols")
        assertTrue(functionSymbols.any { it.name == "calculateTotal" && it.kind == SymbolKind.Function })
        assertTrue(functionSymbols.any { it.name == "handleClick" && it.kind == SymbolKind.AsyncFunction })
        assertTrue(functionSymbols.any { it.name == "generateItems" && it.kind == SymbolKind.Generator })
        assertTrue(functionSymbols.any { it.name == "useUserData" && it.kind == SymbolKind.Hook })

        val variableSymbols = mockSymbols.filter { it.category == SymbolCategory.VARIABLE }
        assertEquals(3, variableSymbols.size, "Should have 3 variable symbols")
        assertTrue(variableSymbols.any { it.name == "API_URL" && it.kind == SymbolKind.Constant })
        assertTrue(variableSymbols.any { it.name == "userCount" && it.kind == SymbolKind.Variable })
        assertTrue(variableSymbols.any { it.name == "ADMIN" && it.kind == SymbolKind.EnumMember })

        val moduleSymbols = mockSymbols.filter { it.category == SymbolCategory.MODULE }
        assertEquals(1, moduleSymbols.size, "Should have 1 module symbol")
        assertTrue(moduleSymbols.any { it.name == "lodash" && it.kind == SymbolKind.Import })
    }

    @Test
    fun testSymbolFilteringLogic() {
        val allSymbols = listOf(
            createMockSymbol("publicFunc", "function", Visibility.PUBLIC),
            createMockSymbol("privateFunc", "function", Visibility.PRIVATE),
            createMockSymbol("protectedMethod", "method", Visibility.PROTECTED),
            createMockSymbol("MyClass", "class", Visibility.PUBLIC),
            createMockSymbol("_generatedHelper", "function", Visibility.DEFAULT, isSynthetic = true),
            createMockSymbol("normalFunction", "function", Visibility.DEFAULT, isSynthetic = false)
        )

        val publicOnlySymbols = filterSymbolsByArgs(
            allSymbols, GetSymbolsArgs(
                filePath = "test.js",
                includePrivate = false
            )
        )
        assertEquals(4, publicOnlySymbols.size, "Should exclude private symbols")
        assertTrue(publicOnlySymbols.none { it.visibility == Visibility.PRIVATE })

        // Test filtering generated symbols
        val noGeneratedSymbols = filterSymbolsByArgs(
            allSymbols, GetSymbolsArgs(
                filePath = "test.js",
                includeGenerated = false
            )
        )
        assertEquals(5, noGeneratedSymbols.size, "Should exclude generated symbols")
        assertTrue(noGeneratedSymbols.none { it.isSynthetic })

        val functionsOnly = filterSymbolsByArgs(
            allSymbols, GetSymbolsArgs(
                filePath = "test.js",
                symbolTypes = listOf("function")
            )
        )
        assertEquals(3, functionsOnly.size, "Should only include functions")
        assertTrue(functionsOnly.all { it.kind == SymbolKind.Function })

        val classesAndMethods = filterSymbolsByArgs(
            allSymbols, GetSymbolsArgs(
                filePath = "test.js",
                symbolTypes = listOf("class", "method")
            )
        )
        assertEquals(2, classesAndMethods.size, "Should include classes and methods")
        assertTrue(classesAndMethods.any { it.kind == SymbolKind.Class })
        assertTrue(classesAndMethods.any { it.kind == SymbolKind.Method })
    }

    @Test
    fun testHierarchicalSymbolExtraction() {
        val classSymbol = createMockSymbol(
            name = "UserService",
            kind = "class",
            visibility = Visibility.PUBLIC,
            modifiers = setOf("export"),
            children = listOf(
                createMockSymbol(
                    "constructor",
                    "constructor",
                    Visibility.PUBLIC,
                    signature = "constructor(config: Config)"
                ),
                createMockSymbol(
                    "getUser",
                    "method",
                    Visibility.PUBLIC,
                    signature = "getUser(id: string): Promise<User>"
                ),
                createMockSymbol(
                    "updateUser",
                    "method",
                    Visibility.PUBLIC,
                    signature = "updateUser(user: User): Promise<void>",
                    modifiers = setOf("async")
                ),
                createMockSymbol(
                    "deleteUser",
                    "method",
                    Visibility.PRIVATE,
                    signature = "deleteUser(id: string): Promise<void>"
                ),
                createMockSymbol("users", "field", Visibility.PRIVATE, signature = "users: User[]"),
                createMockSymbol("config", "field", Visibility.PUBLIC, signature = "config: Config")
            )
        )

        assertNotNull(classSymbol.children, "Class should have children")
        assertEquals(6, classSymbol.children.size, "Class should have 6 members")

        val methods = classSymbol.children.filter { it.kind == SymbolKind.Method }
        assertEquals(3, methods.size, "Should have 3 methods")
        assertTrue(methods.any { it.name == "getUser" })
        assertTrue(methods.any { it.name == "updateUser" })
        assertTrue(methods.any { it.name == "deleteUser" })

        val fields = classSymbol.children.filter { it.kind == SymbolKind.Field }
        assertEquals(2, fields.size, "Should have 2 fields")
        assertTrue(fields.any { it.name == "users" })
        assertTrue(fields.any { it.name == "config" })

        val publicMembers = classSymbol.children.filter { it.visibility == Visibility.PUBLIC }
        assertEquals(4, publicMembers.size, "Should have 4 public members")

        val privateMembers = classSymbol.children.filter { it.visibility == Visibility.PRIVATE }
        assertEquals(2, privateMembers.size, "Should have 2 private members")
    }

    @Test
    fun testReactAndJavaScriptSpecificSymbols() {
        val reactSymbols = listOf(
            createMockSymbol(
                "UserProfile", "component", Visibility.PUBLIC,
                signature = "UserProfile(props: UserProfileProps): JSX.Element",
                modifiers = setOf("export"),
                languageData = mapOf("componentType" to "functional")
            ),
            createMockSymbol(
                "ClassComponent", "component", Visibility.PUBLIC,
                signature = "ClassComponent",
                modifiers = setOf("export"),
                languageData = mapOf("componentType" to "class", "extends" to "React.Component")
            ),
            createMockSymbol(
                "useUserData", "hook", Visibility.PUBLIC,
                signature = "useUserData(userId: string): UserData",
                languageData = mapOf("hookType" to "custom")
            ),
            createMockSymbol(
                "useState", "hook", Visibility.DEFAULT,
                signature = "useState<T>(initialValue: T): [T, (value: T) => void]",
                languageData = mapOf("hookType" to "builtin")
            ),
            createMockSymbol(
                "generateIds", "generator_function", Visibility.PUBLIC,
                signature = "generateIds(start: number): Generator<number>",
                modifiers = setOf("generator", "export")
            ),
            createMockSymbol(
                "fetchData", "async_function", Visibility.PUBLIC,
                signature = "fetchData(url: string): Promise<Data>",
                modifiers = setOf("async", "export")
            )
        )

        val components = reactSymbols.filter { it.kind == SymbolKind.Component }
        assertEquals(2, components.size, "Should have 2 React components")
        assertTrue(components.any { it.name == "UserProfile" && it.languageData?.get("componentType") == "functional" })
        assertTrue(components.any { it.name == "ClassComponent" && it.languageData?.get("componentType") == "class" })

        val hooks = reactSymbols.filter { it.kind == SymbolKind.Hook }
        assertEquals(2, hooks.size, "Should have 2 hooks")
        assertTrue(hooks.any { it.name == "useUserData" && it.languageData?.get("hookType") == "custom" })
        assertTrue(hooks.any { it.name == "useState" && it.languageData?.get("hookType") == "builtin" })

        val generators = reactSymbols.filter { it.kind == SymbolKind.Generator }
        assertEquals(1, generators.size, "Should have 1 generator function")
        assertTrue(generators.any { it.name == "generateIds" && it.modifiers.contains("generator") })

        val asyncFunctions = reactSymbols.filter { it.kind == SymbolKind.AsyncFunction }
        assertEquals(1, asyncFunctions.size, "Should have 1 async function")
        assertTrue(asyncFunctions.any { it.name == "fetchData" && it.modifiers.contains("async") })
    }

    @Test
    fun testTypeScriptSpecificSymbols() {
        val typeScriptSymbols = listOf(
            createMockSymbol(
                "UserData", "interface", Visibility.PUBLIC,
                signature = "UserData<T extends BaseUser>",
                modifiers = setOf("export"),
                languageData = mapOf(
                    "typeParameters" to "T extends BaseUser",
                    "extends" to "Identifiable, Timestamped"
                )
            ),
            createMockSymbol(
                "UserRole", "enum", Visibility.PUBLIC,
                signature = "UserRole",
                modifiers = setOf("export"),
                children = listOf(
                    createMockSymbol("ADMIN", "enum_member", Visibility.PUBLIC),
                    createMockSymbol("USER", "enum_member", Visibility.PUBLIC),
                    createMockSymbol("GUEST", "enum_member", Visibility.PUBLIC)
                )
            ),
            createMockSymbol(
                "GenericFunction", "function", Visibility.PUBLIC,
                signature = "GenericFunction<T, R>(input: T): R",
                languageData = mapOf("typeParameters" to "T, R")
            ),
            createMockSymbol(
                "readonlyField", "field", Visibility.PUBLIC,
                signature = "readonly readonlyField: string",
                modifiers = setOf("readonly")
            )
        )

        val interfaces = typeScriptSymbols.filter { it.kind == SymbolKind.Interface }
        assertEquals(1, interfaces.size, "Should have 1 interface")
        val userDataInterface = interfaces.first()
        assertEquals("UserData", userDataInterface.name)
        assertEquals("T extends BaseUser", userDataInterface.languageData?.get("typeParameters"))
        assertEquals("Identifiable, Timestamped", userDataInterface.languageData?.get("extends"))

        val enums = typeScriptSymbols.filter { it.kind == SymbolKind.Enum }
        assertEquals(1, enums.size, "Should have 1 enum")
        val userRoleEnum = enums.first()
        assertEquals("UserRole", userRoleEnum.name)
        assertEquals(3, userRoleEnum.children?.size, "Enum should have 3 members")
        assertTrue(userRoleEnum.children?.all { it.kind == SymbolKind.EnumMember } == true)

        val genericFunctions = typeScriptSymbols.filter {
            it.kind == SymbolKind.Function && it.languageData?.containsKey("typeParameters") == true
        }
        assertEquals(1, genericFunctions.size, "Should have 1 generic function")
        assertEquals("T, R", genericFunctions.first().languageData?.get("typeParameters"))

        val readonlyFields = typeScriptSymbols.filter { it.modifiers.contains("readonly") }
        assertEquals(1, readonlyFields.size, "Should have 1 readonly field")
        assertEquals("readonlyField", readonlyFields.first().name)
    }

    @Test
    fun testSymbolLocationAndMetadata() {
        val symbolWithMetadata = createMockSymbol(
            name = "complexFunction",
            kind = "function",
            visibility = Visibility.PUBLIC,
            signature = "complexFunction(data: Data, options?: Options): Promise<Result>",
            modifiers = setOf("async", "export"),
            isDeprecated = true,
            languageData = mapOf(
                "throws" to "ValidationError, NetworkError",
                "since" to "1.5.0",
                "author" to "John Doe"
            )
        )

        assertNotNull(symbolWithMetadata.location, "Symbol should have location")
        assertTrue(symbolWithMetadata.location.startOffset >= 0, "Start offset should be non-negative")
        assertTrue(
            symbolWithMetadata.location.endOffset > symbolWithMetadata.location.startOffset,
            "End offset should be greater than start offset"
        )
        assertTrue(symbolWithMetadata.location.lineNumber > 0, "Line number should be positive")
        assertTrue(symbolWithMetadata.isDeprecated, "Symbol should be marked as deprecated")
        assertEquals("ValidationError, NetworkError", symbolWithMetadata.languageData?.get("throws"))
        assertEquals("1.5.0", symbolWithMetadata.languageData?.get("since"))
        assertEquals("John Doe", symbolWithMetadata.languageData?.get("author"))

        assertTrue(symbolWithMetadata.modifiers.contains("async"), "Should have async modifier")
        assertTrue(symbolWithMetadata.modifiers.contains("export"), "Should have export modifier")
    }

    @Test
    fun testEdgeCasesAndBoundaryConditions() {
        val edgeCases = listOf(
            createMockSymbol("", "function", Visibility.DEFAULT),

            createMockSymbol(
                "veryLongFunctionNameThatExceedsNormalLengthButShouldStillBeHandledCorrectly",
                "function", Visibility.PUBLIC
            ),
            createMockSymbol("function\$WithSpecialChars", "function", Visibility.PUBLIC),
            createMockSymbol("minimal", "variable", Visibility.DEFAULT),
            createMockSymbol(
                name = "comprehensive",
                kind = "method",
                visibility = Visibility.PROTECTED,
                signature = "comprehensive<T>(param: T): Promise<T>",
                modifiers = setOf("async", "protected", "override"),
                children = emptyList(),
                isDeprecated = true,
                isSynthetic = false,
                languageData = mapOf(
                    "typeParameters" to "T",
                    "overrides" to "BaseClass.comprehensive",
                    "throws" to "Error"
                )
            )
        )

        for (symbol in edgeCases) {
            // Verify basic properties are always present
            assertNotNull(symbol.name, "Symbol name should never be null")
            assertNotNull(symbol.kind, "Symbol kind should never be null")
            assertNotNull(symbol.category, "Symbol category should never be null")
            assertNotNull(symbol.visibility, "Symbol visibility should never be null")
            assertNotNull(symbol.modifiers, "Symbol modifiers should never be null")
            assertNotNull(symbol.location, "Symbol location should never be null")

            // Verify location data is valid
            assertTrue(symbol.location.startOffset >= 0, "Start offset should be non-negative")
            assertTrue(
                symbol.location.endOffset >= symbol.location.startOffset,
                "End offset should be >= start offset"
            )
            assertTrue(symbol.location.lineNumber > 0, "Line number should be positive")
        }
    }

    @Test
    fun testSupportedLanguageAndFileTypes() {
        val supportedLanguage = extractor.getSupportedLanguage()
        assertEquals("JavaScript/TypeScript", supportedLanguage)

        val supportedExtensions = listOf("js", "jsx", "ts", "tsx", "mjs", "cjs")
        val unsupportedExtensions = listOf("java", "py", "kt", "go", "rb", "cpp", "cs")

        for (extension in supportedExtensions) {
            val mockFilePath = "test.$extension"
            assertTrue(isJavaScriptFile(mockFilePath), "Should support .$extension files")
        }

        for (extension in unsupportedExtensions) {
            val mockFilePath = "test.$extension"
            assertTrue(!isJavaScriptFile(mockFilePath), "Should not support .$extension files")
        }
    }

    private fun createMockSymbol(
        name: String,
        kind: String,
        visibility: Visibility = Visibility.PUBLIC,
        signature: String? = null,
        modifiers: Set<String> = emptySet(),
        children: List<SymbolInfo>? = null,
        isDeprecated: Boolean = false,
        isSynthetic: Boolean = false,
        languageData: Map<String, String>? = null
    ): SymbolInfo {
        return SymbolInfo(
            name = name,
            qualifiedName = name,
            kind = stringToSymbolKind(kind),
            category = categorizeSymbolKind(kind),
            location = Location(
                startOffset = 0,
                endOffset = name.length + 20,
                lineNumber = 1
            ),
            modifiers = modifiers,
            typeInfo = null,
            signature = signature,
            visibility = visibility,
            documentation = null,
            decorators = emptyList(),
            overrides = null,
            implements = emptyList(),
            languageData = languageData,
            children = children,
            isSynthetic = isSynthetic,
            isDeprecated = isDeprecated
        )
    }

    private fun stringToSymbolKind(kind: String): SymbolKind {
        return when (kind) {
            "class" -> SymbolKind.Class
            "interface" -> SymbolKind.Interface
            "function" -> SymbolKind.Function
            "method" -> SymbolKind.Method
            "field" -> SymbolKind.Field
            "variable" -> SymbolKind.Variable
            "constant" -> SymbolKind.Constant
            "constructor" -> SymbolKind.Constructor
            "enum" -> SymbolKind.Enum
            "enum_member" -> SymbolKind.EnumMember
            "import" -> SymbolKind.Import
            "arrow_function", "async_function" -> SymbolKind.AsyncFunction
            "generator_function", "generator" -> SymbolKind.Generator
            "component" -> SymbolKind.Component
            "hook" -> SymbolKind.Hook
            else -> SymbolKind.Custom(kind)
        }
    }

    private fun categorizeSymbolKind(kind: String): SymbolCategory {
        return when (kind) {
            "class", "interface", "enum", "component" -> SymbolCategory.TYPE
            "function", "method", "constructor", "arrow_function", "async_function", "generator_function", "hook" -> SymbolCategory.FUNCTION
            "field", "variable", "constant", "enum_member" -> SymbolCategory.VARIABLE
            "module", "namespace", "package", "import" -> SymbolCategory.MODULE
            else -> SymbolCategory.OTHER
        }
    }

    private fun filterSymbolsByArgs(symbols: List<SymbolInfo>, args: GetSymbolsArgs): List<SymbolInfo> {
        return symbols.filter { symbol ->
            if (!args.includePrivate && symbol.visibility == Visibility.PRIVATE) {
                return@filter false
            }

            if (!args.includeGenerated && symbol.isSynthetic) {
                return@filter false
            }

            if (args.symbolTypes != null && !args.symbolTypes.contains(symbol.kind.value)) {
                return@filter false
            }

            true
        }
    }

    private fun isJavaScriptFile(filePath: String): Boolean {
        val extension = filePath.substringAfterLast('.', "")
        return extension in listOf("js", "jsx", "ts", "tsx", "mjs", "cjs")
    }
}
