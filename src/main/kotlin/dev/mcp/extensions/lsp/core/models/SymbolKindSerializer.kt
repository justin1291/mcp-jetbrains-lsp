package dev.mcp.extensions.lsp.core.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Custom serializer for SymbolKind that serializes it as a simple string value
 * instead of the full object structure. This maintains backward compatibility
 * with existing API consumers.
 */
object SymbolKindSerializer : KSerializer<SymbolKind> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("SymbolKind", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: SymbolKind) {
        encoder.encodeString(value.value)
    }
    
    override fun deserialize(decoder: Decoder): SymbolKind {
        val value = decoder.decodeString()
        return when (value) {
            "class" -> SymbolKind.Class
            "interface" -> SymbolKind.Interface
            "function" -> SymbolKind.Function
            "method" -> SymbolKind.Method
            "field" -> SymbolKind.Field
            "constant" -> SymbolKind.Constant
            "variable" -> SymbolKind.Variable
            "parameter" -> SymbolKind.Parameter
            "constructor" -> SymbolKind.Constructor
            "property" -> SymbolKind.Property
            "enum" -> SymbolKind.Enum
            "enum_member" -> SymbolKind.EnumMember
            "module" -> SymbolKind.Module
            "namespace" -> SymbolKind.Namespace
            "package" -> SymbolKind.Package
            "import" -> SymbolKind.Import
            "generator" -> SymbolKind.Generator
            "async_function" -> SymbolKind.AsyncFunction
            "component" -> SymbolKind.Component
            "hook" -> SymbolKind.Hook
            "decorator" -> SymbolKind.Decorator
            "type_alias" -> SymbolKind.TypeAlias
            "type_parameter" -> SymbolKind.TypeParameter
            "file" -> SymbolKind.File
            "struct" -> SymbolKind.Struct
            "event" -> SymbolKind.Event
            "operator" -> SymbolKind.Operator
            "string" -> SymbolKind.StringLiteral
            "number" -> SymbolKind.NumberLiteral
            "boolean" -> SymbolKind.BooleanLiteral
            "array" -> SymbolKind.ArrayType
            "object" -> SymbolKind.ObjectType
            "key" -> SymbolKind.Key
            "null" -> SymbolKind.NullLiteral
            else -> SymbolKind.Custom(value)
        }
    }
}
