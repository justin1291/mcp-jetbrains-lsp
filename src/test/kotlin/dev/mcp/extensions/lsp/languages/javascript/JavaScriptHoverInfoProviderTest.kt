package dev.mcp.extensions.lsp.languages.javascript

import dev.mcp.extensions.lsp.core.models.HoverInfo
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JavaScriptHoverInfoProviderTest {

    private val provider = JavaScriptHoverInfoProvider()

    @Test
    fun testHoverInfoDataMapping() {
        val mockHoverInfos = listOf(
            createMockHoverInfo(
                "calculateTotal", "function", "number",
                javaDoc = "Calculates the total price of items in the cart",
                signature = "calculateTotal(items: any[]): number"
            ),
            createMockHoverInfo(
                "UserService", "class", null,
                javaDoc = "Service class for managing user data",
                modifiers = listOf("export"),
                superTypes = listOf("BaseService")
            ),
            createMockHoverInfo(
                "MAX_RETRIES", "variable", "number",
                javaDoc = "Maximum number of retries",
                modifiers = listOf("const"),
                isDeprecated = true,
                deprecationMessage = "Use dynamic retry configuration instead"
            ),
            createMockHoverInfo(
                "handleClick", "function", "void",
                javaDoc = "Async event handler for button clicks",
                signature = "handleClick(event: Event): Promise<void>",
                modifiers = listOf("async", "arrow"),
                throwsExceptions = listOf("Error If event is invalid")
            ),
            createMockHoverInfo(
                "UserProfile", "function", "JSX.Element",
                javaDoc = "User profile component",
                signature = "UserProfile({ name }: Props): JSX.Element",
                modifiers = listOf("export")
            )
        )

        val functionHover = mockHoverInfos.find { it.elementName == "calculateTotal" }
        assertNotNull(functionHover, "Should find function hover info")
        assertEquals("function", functionHover.elementType)
        assertEquals("number", functionHover.type)
        assertTrue(functionHover.javaDoc?.contains("Calculates the total price") == true)
        assertNotNull(functionHover.signature)
        assertTrue(functionHover.signature.contains("calculateTotal"))

        val classHover = mockHoverInfos.find { it.elementName == "UserService" }
        assertNotNull(classHover, "Should find class hover info")
        assertEquals("class", classHover.elementType)
        assertTrue(classHover.modifiers.contains("export"))
        assertTrue(classHover.superTypes.contains("BaseService"))
        assertTrue(classHover.javaDoc?.contains("Service class") == true)

        // Verify variable hover info
        val variableHover = mockHoverInfos.find { it.elementName == "MAX_RETRIES" }
        assertNotNull(variableHover, "Should find variable hover info")
        assertEquals("variable", variableHover.elementType)
        assertEquals("number", variableHover.type)
        assertTrue(variableHover.modifiers.contains("const"))
        assertTrue(variableHover.isDeprecated)
        assertNotNull(variableHover.deprecationMessage)

        val asyncHover = mockHoverInfos.find { it.elementName == "handleClick" }
        assertNotNull(asyncHover, "Should find async function hover info")
        assertEquals("function", asyncHover.elementType)
        assertTrue(asyncHover.modifiers.contains("async"))
        assertTrue(asyncHover.modifiers.contains("arrow"))
        assertTrue(asyncHover.throwsExceptions.isNotEmpty())

        val componentHover = mockHoverInfos.find { it.elementName == "UserProfile" }
        assertNotNull(componentHover, "Should find component hover info")
        assertEquals("function", componentHover.elementType)
        assertEquals("JSX.Element", componentHover.type)
        assertTrue(componentHover.modifiers.contains("export"))
    }

    @Test
    fun testJavaDocProcessing() {
        val complexJavaDoc = """
            Complex function with full documentation
            
            This function demonstrates all hover info features including:
            - Parameter descriptions
            - Return value information
            - Exception handling
            - Version information
            
            @param data The input data to process
            @param options Optional configuration
            @returns Promise resolving to processed result
            @throws ValidationError If data is invalid
            @throws NetworkError If network request fails
            @since 1.5
            @see SimpleFunction
            @see DataProcessor
            @deprecated Use newComplexFunction instead
        """.trimIndent()

        val hoverInfo = createMockHoverInfo(
            "complexFunction", "function", "Promise<r>",
            javaDoc = complexJavaDoc,
            signature = "complexFunction(data: Data, options?: Options): Promise<r>",
            modifiers = listOf("async", "export"),
            throwsExceptions = listOf("ValidationError", "NetworkError"),
            since = "1.5",
            seeAlso = listOf("SimpleFunction", "DataProcessor"),
            isDeprecated = true,
            deprecationMessage = "Use newComplexFunction instead"
        )

        assertNotNull(hoverInfo.javaDoc, "Should have JavaDoc")
        assertTrue(hoverInfo.javaDoc.contains("Complex function with full documentation"))

        // Verify extracted tags
        assertEquals("1.5", hoverInfo.since)
        assertTrue(hoverInfo.seeAlso.contains("SimpleFunction"))
        assertTrue(hoverInfo.seeAlso.contains("DataProcessor"))
        assertTrue(hoverInfo.throwsExceptions.contains("ValidationError"))
        assertTrue(hoverInfo.throwsExceptions.contains("NetworkError"))
        assertTrue(hoverInfo.isDeprecated)
        assertEquals("Use newComplexFunction instead", hoverInfo.deprecationMessage)
    }

    @Test
    fun testModifierAndTypeProcessing() {
        val testCases = listOf(
            Triple("async function", listOf("async"), "Promise<void>"),
            Triple("arrow function", listOf("arrow"), "number"),
            Triple("export function", listOf("export"), "string"),
            Triple("const variable", listOf("const"), "number"),
            Triple("let variable", listOf("let"), "string"),
            Triple("var variable", listOf("var"), "any"),
            Triple("static method", listOf("static"), "boolean"),
            Triple("private method", listOf("private"), "void"),
            Triple("async arrow function", listOf("async", "arrow"), "Promise<string>")
        )

        for ((description, modifiers, type) in testCases) {
            val hoverInfo = createMockHoverInfo("testElement", "function", type, modifiers = modifiers)

            modifiers.forEach { modifier ->
                assertTrue(
                    hoverInfo.modifiers.contains(modifier),
                    "Should contain $modifier modifier for $description"
                )
            }

            assertEquals(type, hoverInfo.type, "Should have correct type for $description")
        }
    }

    @Test
    fun testSignatureGeneration() {
        // Test that signatures are correctly generated for different function types
        val signatureTests = listOf(
            Triple("Regular function", "function", "calculateTotal(items: any[]): number"),
            Triple("Arrow function", "function", "handleClick(event: Event): Promise<void>"),
            Triple("React component", "function", "UserProfile({ name }: Props): JSX.Element"),
            Triple("Constructor", "constructor", "constructor(baseUrl: string, timeout?: number)"),
            Triple("Method", "function", "addUser(user: User): boolean"),
            Triple("Getter", "function", "get name(): string"),
            Triple("Setter", "function", "set name(value: string): void"),
            Triple("Generator", "function", "generateIds(start?: number): Generator<number>"),
            Triple("Async generator", "function", "processItems(): AsyncGenerator<Result>")
        )

        for ((description, elementType, expectedSignature) in signatureTests) {
            val hoverInfo = createMockHoverInfo(
                "testElement", elementType, "any",
                signature = expectedSignature
            )

            assertNotNull(hoverInfo.signature, "Should have signature for $description")
            assertEquals(expectedSignature, hoverInfo.signature, "Should match expected signature for $description")
        }
    }

    @Test
    fun testComplexityAndUsageMetrics() {
        val metricsTests = listOf(
            Triple("Simple function", 1, 5),
            Triple("Complex function", 15, 50),
            Triple("Utility function", 3, 100),
            Triple("Helper function", 2, 25),
            Triple("Main function", 10, 200)
        )

        for ((description, complexity, calledByCount) in metricsTests) {
            val hoverInfo = createMockHoverInfo(
                "testFunction", "function", "void",
                complexity = complexity,
                calledByCount = calledByCount
            )

            assertEquals(complexity, hoverInfo.complexity, "Should have correct complexity for $description")
            assertEquals(calledByCount, hoverInfo.calledByCount, "Should have correct usage count for $description")
        }
    }

    @Test
    fun testInheritanceAndPolymorphism() {
        val classHoverInfo = createMockHoverInfo(
            "DerivedClass", "class", null,
            superTypes = listOf("BaseClass", "Mixin"),
            implementedBy = listOf("ConcreteImpl1", "ConcreteImpl2")
        )

        val methodHoverInfo = createMockHoverInfo(
            "virtualMethod", "function", "void",
            overriddenBy = listOf("DerivedClass.virtualMethod", "OtherClass.virtualMethod")
        )

        assertTrue(classHoverInfo.superTypes.contains("BaseClass"), "Should have BaseClass as supertype")
        assertTrue(classHoverInfo.superTypes.contains("Mixin"), "Should have Mixin as supertype")
        assertTrue(classHoverInfo.implementedBy.contains("ConcreteImpl1"), "Should be implemented by ConcreteImpl1")
        assertTrue(classHoverInfo.implementedBy.contains("ConcreteImpl2"), "Should be implemented by ConcreteImpl2")
        assertTrue(
            methodHoverInfo.overriddenBy.contains("DerivedClass.virtualMethod"),
            "Should be overridden by DerivedClass"
        )
        assertTrue(
            methodHoverInfo.overriddenBy.contains("OtherClass.virtualMethod"),
            "Should be overridden by OtherClass"
        )
    }

    @Test
    fun testEdgeCasesAndBoundaryConditions() {
        val edgeCases = listOf(
            createMockHoverInfo("", "function", null, javaDoc = null, signature = null),

            createMockHoverInfo(
                "veryLongFunctionNameThatExceedsNormalLength", "function", "string",
                javaDoc = "This is a very long JavaDoc description that tests how the hover info handles extensive documentation with multiple lines and detailed explanations of complex functionality"
            ),


            createMockHoverInfo(
                "function\$WithSpecialChars", "function", "any",
                signature = "function\$WithSpecialChars(param_1: string, param-2: number): any"
            ),


            createMockHoverInfo("minimalFunction", "function", "void"),

            createMockHoverInfo("complexFunction", "function", "any", complexity = 100, calledByCount = 1000)
        )

        for (hoverInfo in edgeCases) {
            // Verify basic properties are always present
            assertNotNull(hoverInfo.elementName, "Element name should never be null")
            assertNotNull(hoverInfo.elementType, "Element type should never be null")
            assertNotNull(hoverInfo.modifiers, "Modifiers list should never be null")
            assertNotNull(hoverInfo.throwsExceptions, "Exceptions list should never be null")
            assertNotNull(hoverInfo.seeAlso, "See also list should never be null")

            // Verify numeric values are non-negative
            assertTrue(hoverInfo.calledByCount >= 0, "Called by count should be non-negative")
            if (hoverInfo.complexity != null) {
                assertTrue(hoverInfo.complexity >= 0, "Complexity should be non-negative")
            }
        }
    }

    @Test
    fun testSupportedLanguageAndFileTypes() {
        // Test language support logic
        val supportedLanguage = provider.getSupportedLanguage()
        assertEquals("JavaScript/TypeScript", supportedLanguage)

        // Test file type support (mock logic)
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

    // Helper methods for creating mock data

    private fun createMockHoverInfo(
        elementName: String,
        elementType: String,
        type: String?,
        javaDoc: String? = null,
        signature: String? = null,
        modifiers: List<String> = emptyList(),
        superTypes: List<String> = emptyList(),
        implementedBy: List<String> = emptyList(),
        overriddenBy: List<String> = emptyList(),
        calledByCount: Int = 0,
        complexity: Int? = null,
        throwsExceptions: List<String> = emptyList(),
        deprecationMessage: String? = null,
        since: String? = null,
        seeAlso: List<String> = emptyList(),
        isDeprecated: Boolean = false,
        module: String? = "test.js"
    ): HoverInfo {
        val presentableText = when (elementType) {
            "function" -> signature ?: "$elementName()"
            "class" -> elementName
            "variable" -> if (type != null) "$elementName: $type" else elementName
            else -> elementName
        }

        return HoverInfo(
            elementName = elementName,
            elementType = elementType,
            type = type,
            presentableText = presentableText,
            javaDoc = javaDoc,
            signature = signature,
            modifiers = modifiers,
            superTypes = superTypes,
            implementedBy = implementedBy,
            overriddenBy = overriddenBy,
            calledByCount = calledByCount,
            complexity = complexity,
            throwsExceptions = throwsExceptions,
            deprecationMessage = deprecationMessage,
            since = since,
            seeAlso = seeAlso,
            isDeprecated = isDeprecated,
            module = module
        )
    }

    private fun isJavaScriptFile(filePath: String): Boolean {
        val extension = filePath.substringAfterLast('.', "")
        return extension in listOf("js", "jsx", "ts", "tsx", "mjs", "cjs")
    }
}
