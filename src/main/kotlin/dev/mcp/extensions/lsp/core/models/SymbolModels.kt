package dev.mcp.extensions.lsp.core.models

import kotlinx.serialization.Serializable

/**
 * Arguments for symbol extraction.
 */
@Serializable
data class GetSymbolsArgs(
    val filePath: String,
    val hierarchical: Boolean = true,
    val symbolTypes: List<String>? = null,
    val includeImports: Boolean = false,
    val includePrivate: Boolean = true,
    val includeGenerated: Boolean = false,
    val maxDepth: Int = 10
)

/**
 * Enhanced symbol information with language-agnostic design.
 */
@Serializable
data class SymbolInfo(
    // Core identification
    val name: String,
    val qualifiedName: String? = null,
    @Serializable(with = SymbolKindSerializer::class)
    val kind: SymbolKind,
    val category: SymbolCategory,
    
    // Location information  
    val location: Location,
    
    // Type information
    val typeInfo: TypeInfo? = null,
    val signature: String? = null,
    
    // Modifiers and metadata
    val modifiers: Set<String> = emptySet(),
    val visibility: Visibility,
    val decorators: List<DecoratorInfo>? = null,
    
    // Relationships
    val parent: String? = null,
    val children: List<SymbolInfo>? = null,
    val overrides: OverrideInfo? = null,
    val implements: List<String>? = null,
    
    // Documentation
    val documentation: DocumentationInfo? = null,
    
    // Language-specific data
    val languageData: Map<String, String>? = null,
    
    // Semantic flags
    val isDeprecated: Boolean = false,
    val isAbstract: Boolean = false,
    val isStatic: Boolean = false,
    val isAsync: Boolean = false,
    val isGenerator: Boolean = false,
    val isSynthetic: Boolean = false
)

/**
 * Symbol kind using sealed class for extensibility.
 */
sealed class SymbolKind {
    abstract val value: String
    
    // Common kinds across languages
    data object Class : SymbolKind() { override val value = "class" }
    data object Interface : SymbolKind() { override val value = "interface" }
    data object Function : SymbolKind() { override val value = "function" }
    data object Method : SymbolKind() { override val value = "method" }
    data object Field : SymbolKind() { override val value = "field" }
    data object Variable : SymbolKind() { override val value = "variable" }
    data object Parameter : SymbolKind() { override val value = "parameter" }
    data object Constructor : SymbolKind() { override val value = "constructor" }
    data object Property : SymbolKind() { override val value = "property" }
    data object Enum : SymbolKind() { override val value = "enum" }
    data object EnumMember : SymbolKind() { override val value = "enum_member" }
    data object Module : SymbolKind() { override val value = "module" }
    data object Namespace : SymbolKind() { override val value = "namespace" }
    data object Package : SymbolKind() { override val value = "package" }
    data object Import : SymbolKind() { override val value = "import" }
    
    // Special kinds
    data object Generator : SymbolKind() { override val value = "generator" }
    data object AsyncFunction : SymbolKind() { override val value = "async_function" }
    data object Component : SymbolKind() { override val value = "component" }
    data object Hook : SymbolKind() { override val value = "hook" }
    data object Decorator : SymbolKind() { override val value = "decorator" }
    data object TypeAlias : SymbolKind() { override val value = "type_alias" }
    data object TypeParameter : SymbolKind() { override val value = "type_parameter" }
    
    // LSP Standard Types (added for full compatibility)
    data object Constant : SymbolKind() { override val value = "constant" }
    data object File : SymbolKind() { override val value = "file" }
    data object Struct : SymbolKind() { override val value = "struct" }
    data object Event : SymbolKind() { override val value = "event" }
    data object Operator : SymbolKind() { override val value = "operator" }
    data object StringLiteral : SymbolKind() { override val value = "string" }
    data object NumberLiteral : SymbolKind() { override val value = "number" }
    data object BooleanLiteral : SymbolKind() { override val value = "boolean" }
    data object ArrayType : SymbolKind() { override val value = "array" }
    data object ObjectType : SymbolKind() { override val value = "object" }
    data object Key : SymbolKind() { override val value = "key" }
    data object NullLiteral : SymbolKind() { override val value = "null" }
    
    // Language-specific with generic representation
    data class Custom(override val value: String) : SymbolKind()
}

/**
 * High-level symbol categories for filtering.
 */
@Serializable
enum class SymbolCategory {
    TYPE,        // Classes, interfaces, enums, etc.
    FUNCTION,    // Functions, methods, constructors
    VARIABLE,    // Fields, variables, properties, parameters
    MODULE,      // Modules, namespaces, packages
    OTHER        // Imports, decorators, etc.
}

/**
 * Location information for a symbol.
 */
@Serializable
data class Location(
    val startOffset: Int,
    val endOffset: Int,
    val lineNumber: Int,
    val column: Int? = null
)

/**
 * Structured type information.
 */
@Serializable
data class TypeInfo(
    val displayName: String,
    val baseType: String,
    val typeParameters: List<TypeInfo>? = null,
    val isNullable: Boolean = false,
    val isArray: Boolean = false,
    val isOptional: Boolean = false,
    val unionTypes: List<TypeInfo>? = null,
    val rawType: String? = null
)

/**
 * Visibility levels (language-agnostic).
 */
@Serializable
enum class Visibility {
    PUBLIC,
    PROTECTED,
    INTERNAL,
    PRIVATE,
    PACKAGE,
    DEFAULT
}

/**
 * Decorator/annotation information.
 */
@Serializable
data class DecoratorInfo(
    val name: String,
    val arguments: List<String>? = null,
    val isBuiltin: Boolean = false
)

/**
 * Override information.
 */
@Serializable
data class OverrideInfo(
    val parentClass: String,
    val methodName: String,
    val isExplicit: Boolean = true
)

/**
 * Documentation information.
 */
@Serializable
data class DocumentationInfo(
    val summary: String? = null,
    val description: String? = null,
    val isPresent: Boolean = false,
    val format: String? = null
)
