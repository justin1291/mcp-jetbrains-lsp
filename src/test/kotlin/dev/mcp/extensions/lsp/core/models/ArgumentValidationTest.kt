package dev.mcp.extensions.lsp.core.models

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class ArgumentValidationTest {

    @Test
    fun testFindDefinitionArgs_ValidSymbolName() {
        val args = FindDefinitionArgs(symbolName = "TestClass")
        assertEquals("TestClass", args.symbolName)
    }

    @Test
    fun testFindDefinitionArgs_ValidPositionBased() {
        val args = FindDefinitionArgs(
            symbolName = null,
            filePath = "test.java",
            position = 100
        )
        assertEquals("test.java", args.filePath)
        assertEquals(100, args.position)
    }

    @Test
    fun testFindDefinitionArgs_ValidHybrid() {
        val args = FindDefinitionArgs(
            symbolName = "TestClass",
            filePath = "test.java",
            position = 100
        )
        assertEquals("TestClass", args.symbolName)
        assertEquals("test.java", args.filePath)
        assertEquals(100, args.position)
    }

    @Test
    fun testFindDefinitionArgs_InvalidNoParameters() {
        assertThrows<IllegalArgumentException> {
            FindDefinitionArgs(symbolName = null, filePath = null, position = null)
        }
    }

    @Test
    fun testFindDefinitionArgs_InvalidMissingPosition() {
        assertThrows<IllegalArgumentException> {
            FindDefinitionArgs(symbolName = null, filePath = "test.java", position = null)
        }
    }

    @Test
    fun testFindDefinitionArgs_InvalidMissingFilePath() {
        assertThrows<IllegalArgumentException> {
            FindDefinitionArgs(symbolName = null, filePath = null, position = 100)
        }
    }

    @Test
    fun testFindReferencesArgs_ValidSymbolName() {
        val args = FindReferencesArgs(symbolName = "TestClass")
        assertEquals("TestClass", args.symbolName)
        assertEquals(false, args.includeDeclaration) // default value
    }

    @Test
    fun testFindReferencesArgs_ValidPositionBased() {
        val args = FindReferencesArgs(
            symbolName = null,
            filePath = "test.java",
            position = 100,
            includeDeclaration = true
        )
        assertEquals("test.java", args.filePath)
        assertEquals(100, args.position)
        assertEquals(true, args.includeDeclaration)
    }

    @Test
    fun testFindReferencesArgs_InvalidNoParameters() {
        assertThrows<IllegalArgumentException> {
            FindReferencesArgs(
                symbolName = null,
                filePath = null,
                position = null,
                includeDeclaration = false
            )
        }
    }

    @Test
    fun testFindReferencesArgs_InvalidMissingPosition() {
        assertThrows<IllegalArgumentException> {
            FindReferencesArgs(
                symbolName = null,
                filePath = "test.java",
                position = null,
                includeDeclaration = false
            )
        }
    }

    @Test
    fun testFindReferencesArgs_InvalidMissingFilePath() {
        assertThrows<IllegalArgumentException> {
            FindReferencesArgs(
                symbolName = null,
                filePath = null,
                position = 100,
                includeDeclaration = false
            )
        }
    }
}
