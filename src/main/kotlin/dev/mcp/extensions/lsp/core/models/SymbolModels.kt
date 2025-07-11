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
    val includeImports: Boolean = false
)

/**
 * Information about a symbol in a file.
 */
@Serializable
data class SymbolInfo(
    val name: String,
    val type: String,
    val kind: String,
    val startOffset: Int,
    val endOffset: Int,
    val lineNumber: Int,
    val modifiers: List<String> = emptyList(),
    val parameters: List<String>? = null,
    val returnType: String? = null,
    val children: List<SymbolInfo>? = null,  // For hierarchical structure
    val isDeprecated: Boolean = false,
    val hasJavadoc: Boolean = false,
    val isOverride: Boolean = false,
    val overrides: String? = null,
    val visibility: String = "package-private",
    val annotations: List<String> = emptyList()
)
