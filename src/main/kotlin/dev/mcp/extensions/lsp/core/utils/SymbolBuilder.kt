package dev.mcp.extensions.lsp.core.utils

import dev.mcp.extensions.lsp.core.models.*

/**
 * Builder for creating SymbolInfo instances with a fluent API.
 * Makes it easier for language implementations to construct symbols.
 */
class SymbolBuilder {
    private var name: String = ""
    private var qualifiedName: String? = null
    private var kind: String = "unknown"
    private var startOffset: Int = 0
    private var endOffset: Int = 0
    private var lineNumber: Int = 0
    private var column: Int? = null
    private val modifiers = mutableSetOf<String>()
    private var typeInfo: TypeInfo? = null
    private var signature: String? = null
    private var visibility: String = "default"
    private var documentation: DocumentationInfo? = null
    private var overrides: OverrideInfo? = null
    private val decorators = mutableListOf<DecoratorInfo>()
    private val implements = mutableListOf<String>()
    private val languageData = mutableMapOf<String, String>()
    private var parent: String? = null
    private val children = mutableListOf<SymbolInfo>()
    
    // Flags
    private var isDeprecated: Boolean = false
    private var isAbstract: Boolean = false
    private var isStatic: Boolean = false
    private var isAsync: Boolean = false
    private var isGenerator: Boolean = false
    private var isSynthetic: Boolean = false
    
    fun name(value: String) = apply { name = value }
    fun qualifiedName(value: String?) = apply { qualifiedName = value }
    fun kind(value: String) = apply { kind = value }
    
    fun location(start: Int, end: Int, line: Int, col: Int? = null) = apply {
        startOffset = start
        endOffset = end
        lineNumber = line
        column = col
    }
    
    fun modifier(value: String) = apply { 
        modifiers.add(value)
        // Auto-detect flags from modifiers
        when (value.lowercase()) {
            "deprecated" -> isDeprecated = true
            "abstract" -> isAbstract = true
            "static" -> isStatic = true
            "async" -> isAsync = true
            "generator" -> isGenerator = true
            "synthetic" -> isSynthetic = true
        }
    }
    
    fun modifiers(values: Collection<String>) = apply { 
        values.forEach { modifier(it) }
    }
    
    fun type(info: TypeInfo?) = apply { typeInfo = info }
    fun type(displayName: String, baseType: String? = null) = apply { 
        typeInfo = TypeInfo(
            displayName = displayName,
            baseType = baseType ?: displayName
        )
    }
    
    fun signature(value: String?) = apply { signature = value }
    fun visibility(value: String) = apply { visibility = value }
    
    fun documentation(present: Boolean = false, summary: String? = null, format: String? = null) = apply {
        documentation = DocumentationInfo(
            summary = summary,
            isPresent = present,
            format = format
        )
    }
    
    fun overrides(parentClass: String, methodName: String, explicit: Boolean = true) = apply {
        overrides = OverrideInfo(parentClass, methodName, explicit)
    }
    
    fun decorator(name: String, arguments: List<String>? = null, builtin: Boolean = false) = apply {
        decorators.add(DecoratorInfo(name, arguments, builtin))
    }
    
    fun implements(interfaceName: String) = apply { implements.add(interfaceName) }
    fun languageData(key: String, value: String) = apply { languageData[key] = value }
    fun parent(value: String?) = apply { parent = value }
    fun child(symbol: SymbolInfo) = apply { children.add(symbol) }
    fun children(symbols: List<SymbolInfo>) = apply { children.addAll(symbols) }
    
    // Flag setters
    fun deprecated(value: Boolean = true) = apply { isDeprecated = value }
    fun abstract(value: Boolean = true) = apply { isAbstract = value }
    fun static(value: Boolean = true) = apply { isStatic = value }
    fun async(value: Boolean = true) = apply { isAsync = value }
    fun generator(value: Boolean = true) = apply { isGenerator = value }
    fun synthetic(value: Boolean = true) = apply { isSynthetic = value }
    
    fun build(): SymbolInfo {
        val symbolKind = parseSymbolKind(kind)
        val category = categorizeSymbol(symbolKind)
        
        return SymbolInfo(
            name = name,
            qualifiedName = qualifiedName,
            kind = symbolKind,
            category = category,
            location = Location(startOffset, endOffset, lineNumber, column),
            typeInfo = typeInfo,
            signature = signature,
            modifiers = modifiers,
            visibility = parseVisibility(visibility),
            decorators = decorators.takeIf { it.isNotEmpty() },
            parent = parent,
            children = if (children.isNotEmpty()) children else null,
            overrides = overrides,
            implements = implements.takeIf { it.isNotEmpty() },
            documentation = documentation,
            languageData = languageData.takeIf { it.isNotEmpty() },
            isDeprecated = isDeprecated,
            isAbstract = isAbstract,
            isStatic = isStatic,
            isAsync = isAsync,
            isGenerator = isGenerator,
            isSynthetic = isSynthetic
        )
    }
    
    companion object {
        fun parseSymbolKind(kind: String): SymbolKind {
            return when (kind.lowercase()) {
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
                else -> SymbolKind.Custom(kind)
            }
        }
        
        fun categorizeSymbol(kind: SymbolKind): SymbolCategory {
            return when (kind) {
                is SymbolKind.Class, is SymbolKind.Interface, is SymbolKind.Enum, 
                is SymbolKind.TypeAlias, is SymbolKind.Struct -> SymbolCategory.TYPE
                
                is SymbolKind.Function, is SymbolKind.Method, is SymbolKind.Constructor,
                is SymbolKind.Generator, is SymbolKind.AsyncFunction, is SymbolKind.Event,
                is SymbolKind.Operator -> SymbolCategory.FUNCTION
                
                is SymbolKind.Field, is SymbolKind.Variable, is SymbolKind.Parameter,
                is SymbolKind.Property, is SymbolKind.EnumMember, is SymbolKind.Constant -> SymbolCategory.VARIABLE
                
                is SymbolKind.Module, is SymbolKind.Namespace, is SymbolKind.Package,
                is SymbolKind.File -> SymbolCategory.MODULE
                
                else -> SymbolCategory.OTHER
            }
        }
        
        fun parseVisibility(visibility: String): Visibility {
            return when (visibility.lowercase()) {
                "public" -> Visibility.PUBLIC
                "protected" -> Visibility.PROTECTED
                "internal" -> Visibility.INTERNAL
                "private" -> Visibility.PRIVATE
                "package", "package-private" -> Visibility.PACKAGE
                else -> Visibility.DEFAULT
            }
        }
    }
}

/**
 * Extension function for easy builder creation.
 */
fun symbol(block: SymbolBuilder.() -> Unit): SymbolInfo {
    return SymbolBuilder().apply(block).build()
}
