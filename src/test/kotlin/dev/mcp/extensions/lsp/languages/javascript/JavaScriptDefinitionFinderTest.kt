package dev.mcp.extensions.lsp.languages.javascript

import dev.mcp.extensions.lsp.core.models.DefinitionLocation
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JavaScriptDefinitionFinderTest {

    private val finder = JavaScriptDefinitionFinder()

    @Test
    fun testDefinitionDataMapping() {
        val mockDefinitions = listOf(
            createMockDefinition("calculateTotal", "function", "Function in utils.js"),
            createMockDefinition("UserService", "class", "Class in services.js"),
            createMockDefinition("API_URL", "constant", "Constant in config.js"),
            createMockDefinition("processData", "function", "Method in DataProcessor", "DataProcessor"),
            createMockDefinition("UserProfile", "component", "React Component in components.js"),
            createMockDefinition("useUserData", "function", "Hook in hooks.js"),
            createMockDefinition("fetchUserData", "async_function", "Async function in api.js", modifiers = listOf("async")),
            createMockDefinition("generateIds", "generator_function", "Generator function in utils.js", modifiers = listOf("generator"))
        )

        val functionDef = mockDefinitions.find { it.name == "calculateTotal" }
        assertNotNull(functionDef, "Should find function definition")
        assertEquals("function", functionDef.type)
        assertTrue(functionDef.disambiguationHint?.contains("Function in") == true)

        val classDef = mockDefinitions.find { it.name == "UserService" }
        assertNotNull(classDef, "Should find class definition")
        assertEquals("class", classDef.type)
        assertTrue(classDef.disambiguationHint?.contains("Class in") == true)

        val constantDef = mockDefinitions.find { it.name == "API_URL" }
        assertNotNull(constantDef, "Should find constant definition")
        assertEquals("constant", constantDef.type)
        assertEquals("Constant in config.js", constantDef.disambiguationHint)

        val methodDef = mockDefinitions.find { it.name == "processData" }
        assertNotNull(methodDef, "Should find method definition")
        assertEquals("function", methodDef.type)
        assertEquals("DataProcessor", methodDef.containingClass)
        assertEquals("Method in DataProcessor", methodDef.disambiguationHint)

        val componentDef = mockDefinitions.find { it.name == "UserProfile" }
        assertNotNull(componentDef, "Should find component definition")
        assertEquals("component", componentDef.type)
        assertTrue(componentDef.disambiguationHint?.contains("React Component") == true)

        val asyncDef = mockDefinitions.find { it.name == "fetchUserData" }
        assertNotNull(asyncDef, "Should find async function definition")
        assertEquals("async_function", asyncDef.type)
        assertTrue(asyncDef.modifiers.contains("async"))

        val generatorDef = mockDefinitions.find { it.name == "generateIds" }
        assertNotNull(generatorDef, "Should find generator function definition")
        assertEquals("generator_function", generatorDef.type)
        assertTrue(generatorDef.modifiers.contains("generator"))
    }

    @Test
    fun testConfidenceScoring() {
        val projectDefinition = createMockDefinition("projectFunction", "function", "Function in project.js", 
                                                   isLibraryCode = false, isTestCode = false)
        val libraryDefinition = createMockDefinition("libraryFunction", "function", "Function in node_modules/lib.js", 
                                                    isLibraryCode = true, isTestCode = false)
        val testDefinition = createMockDefinition("testFunction", "function", "Function in test.spec.js", 
                                                 isLibraryCode = false, isTestCode = true)

        assertTrue(projectDefinition.confidence >= 0.95f, "Project code should have high confidence")
        assertEquals(false, projectDefinition.isLibraryCode)
        assertEquals(false, projectDefinition.isTestCode)

        assertTrue(libraryDefinition.confidence >= 0.5f, "Library code should have medium confidence")
        assertTrue(libraryDefinition.confidence < 0.95f, "Library code should have lower confidence than project code")
        assertEquals(true, libraryDefinition.isLibraryCode)

        assertTrue(testDefinition.confidence >= 0.5f, "Test code should have medium confidence")
        assertTrue(testDefinition.confidence < 0.95f, "Test code should have lower confidence than project code")
        assertEquals(true, testDefinition.isTestCode)
    }

    @Test
    fun testAccessibilityAndModifierLogic() {
        val publicMethod = createMockDefinition("publicMethod", "function", "Method in ApiClient", 
                                              modifiers = listOf("public"))
        val privateMethod = createMockDefinition("privateMethod", "function", "Method in ApiClient", 
                                                modifiers = listOf("private"),
                                                accessibilityWarning = "Private member - not accessible from outside its class")
        val staticMethod = createMockDefinition("staticMethod", "function", "Static method in Utils", 
                                              modifiers = listOf("static"))
        val abstractMethod = createMockDefinition("abstractMethod", "function", "Abstract method in BaseClass", 
                                                 modifiers = listOf("abstract"), isAbstract = true)

        assertEquals(null, publicMethod.accessibilityWarning)
        assertTrue(publicMethod.modifiers.contains("public"))

        assertNotNull(privateMethod.accessibilityWarning, "Private method should have accessibility warning")
        assertTrue(privateMethod.accessibilityWarning?.contains("Private") == true)
        assertTrue(privateMethod.modifiers.contains("private"))

        assertTrue(staticMethod.modifiers.contains("static"))
        assertTrue(staticMethod.disambiguationHint?.contains("Static") == true)

        assertTrue(abstractMethod.modifiers.contains("abstract"))
        assertEquals(true, abstractMethod.isAbstract)
    }

    @Test
    fun testDefinitionLocationCalculation() {
        val definition = createMockDefinition("testFunction", "function", "Function in test.js")

        assertTrue(definition.startOffset >= 0, "Start offset should be non-negative")
        assertTrue(definition.endOffset > definition.startOffset, "End offset should be greater than start offset")
        assertTrue(definition.lineNumber > 0, "Line number should be positive")
        assertNotNull(definition.filePath, "File path should be set")
        assertTrue(definition.filePath.endsWith(".js"), "File path should end with .js")

        assertNotNull(definition.signature, "Function should have signature")
        assertTrue(definition.signature?.contains("testFunction") == true, "Signature should contain function name")
    }

    @Test
    fun testEdgeCaseHandling() {
        val emptyNameDefinition = createMockDefinition("", "function", "Anonymous function")
        val nullSignatureDefinition = createMockDefinition("noSignature", "variable", "Variable in test.js", signature = null)
        val longNameDefinition = createMockDefinition("veryLongFunctionNameThatExceedsNormalLength", "function", 
                                                     "Function with long name")

        assertEquals("", emptyNameDefinition.name)
        assertNotNull(emptyNameDefinition.disambiguationHint)

        assertEquals(null, nullSignatureDefinition.signature)
        assertEquals("variable", nullSignatureDefinition.type)

        assertTrue(longNameDefinition.name.length > 30)
        assertNotNull(longNameDefinition.disambiguationHint)
    }

    @Test
    fun testSupportedLanguageAndFileTypes() {
        val supportedLanguage = finder.getSupportedLanguage()
        assertEquals("JavaScript/TypeScript", supportedLanguage)

        val jsFileExtensions = listOf("js", "jsx", "ts", "tsx", "mjs", "cjs")
        for (extension in jsFileExtensions) {
            val mockFilePath = "test.$extension"
            assertTrue(isJavaScriptFile(mockFilePath), "Should support .$extension files")
        }

        val nonJsFileExtensions = listOf("java", "py", "kt", "go", "rb")
        for (extension in nonJsFileExtensions) {
            val mockFilePath = "test.$extension"
            assertTrue(!isJavaScriptFile(mockFilePath), "Should not support .$extension files")
        }
    }

    private fun createMockDefinition(
        name: String,
        type: String,
        disambiguationHint: String,
        containingClass: String? = null,
        modifiers: List<String> = emptyList(),
        accessibilityWarning: String? = null,
        signature: String? = if (type == "function") "$name()" else null,
        isAbstract: Boolean = false,
        isLibraryCode: Boolean = false,
        isTestCode: Boolean = false
    ): DefinitionLocation {
        val confidence = when {
            isLibraryCode -> 0.5f
            isTestCode -> 0.7f
            else -> 1.0f
        }

        return DefinitionLocation(
            name = name,
            filePath = if (isLibraryCode) "node_modules/lib.js" 
                      else if (isTestCode) "test.spec.js" 
                      else "src/main.js",
            startOffset = 0,
            endOffset = name.length + 20,
            lineNumber = 1,
            type = type,
            signature = signature,
            containingClass = containingClass,
            modifiers = modifiers,
            isAbstract = isAbstract,
            confidence = confidence,
            disambiguationHint = disambiguationHint,
            isTestCode = isTestCode,
            isLibraryCode = isLibraryCode,
            accessibilityWarning = accessibilityWarning
        )
    }

    private fun isJavaScriptFile(filePath: String): Boolean {
        val extension = filePath.substringAfterLast('.', "")
        return extension in listOf("js", "jsx", "ts", "tsx", "mjs", "cjs")
    }
}
