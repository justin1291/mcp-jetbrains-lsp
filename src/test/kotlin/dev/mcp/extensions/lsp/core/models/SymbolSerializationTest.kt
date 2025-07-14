package dev.mcp.extensions.lsp.core.models

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SymbolSerializationTest {

    @Test
    fun testSymbolKindSerialization() {
        // Test that SymbolKind serializes as a simple string
        val symbolInfo = SymbolInfo(
            name = "TestClass",
            kind = SymbolKind.Class,
            category = SymbolCategory.TYPE,
            location = Location(0, 100, 1),
            visibility = Visibility.PUBLIC
        )

        val json = Json.encodeToString(symbolInfo)
        println("Serialized JSON: $json")

        // The kind should be serialized as "class", not as an object
        assert(json.contains("\"kind\":\"class\"")) {
            "SymbolKind should serialize as a simple string value, not an object. JSON: $json"
        }

        // Test deserialization
        val deserialized = Json.decodeFromString<SymbolInfo>(json)
        assertEquals(SymbolKind.Class, deserialized.kind)
        assertEquals("class", deserialized.kind.value)
    }

    @Test
    fun testAllSymbolKindsSerialization() {
        val kinds = listOf(
            SymbolKind.Class to "class",
            SymbolKind.Interface to "interface",
            SymbolKind.Method to "method",
            SymbolKind.Field to "field",
            SymbolKind.Constant to "constant",
            SymbolKind.EnumMember to "enum_member",
            SymbolKind.Import to "import",
            SymbolKind.Custom("custom_type") to "custom_type"
        )

        kinds.forEach { (kind, expectedValue) ->
            val symbolInfo = SymbolInfo(
                name = "Test",
                kind = kind,
                category = SymbolCategory.OTHER,
                location = Location(0, 0, 1),
                visibility = Visibility.PUBLIC
            )

            val json = Json.encodeToString(symbolInfo)
            assert(json.contains("\"kind\":\"$expectedValue\"")) {
                "SymbolKind.$kind should serialize as '$expectedValue'. JSON: $json"
            }

            val deserialized = Json.decodeFromString<SymbolInfo>(json)
            assertEquals(kind, deserialized.kind)
            assertEquals(expectedValue, deserialized.kind.value)
        }
    }

    @Test
    fun testHierarchicalSymbolSerialization() {
        // Test nested symbols with children
        val childSymbol = SymbolInfo(
            name = "childMethod",
            kind = SymbolKind.Method,
            category = SymbolCategory.FUNCTION,
            location = Location(50, 100, 5),
            visibility = Visibility.PUBLIC
        )

        val parentSymbol = SymbolInfo(
            name = "ParentClass",
            kind = SymbolKind.Class,
            category = SymbolCategory.TYPE,
            location = Location(0, 200, 1),
            visibility = Visibility.PUBLIC,
            children = listOf(childSymbol)
        )

        val json = Json { prettyPrint = true }.encodeToString(parentSymbol)
        println("Hierarchical JSON:\n$json")

        // Both parent and child should have string kinds
        assert(json.contains("\"kind\": \"class\"") || json.contains("\"kind\":\"class\"")) {
            "Parent kind should be serialized as string"
        }
        assert(json.contains("\"kind\": \"method\"") || json.contains("\"kind\":\"method\"")) {
            "Child kind should be serialized as string"
        }

        val deserialized = Json.decodeFromString<SymbolInfo>(json)
        assertEquals(SymbolKind.Class, deserialized.kind)
        assertEquals(1, deserialized.children?.size)
        assertEquals(SymbolKind.Method, deserialized.children?.first()?.kind)
    }
}
