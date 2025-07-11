package dev.mcp.extensions.lsp.core.models

import kotlinx.serialization.Serializable

/**
 * Arguments for finding symbol definitions.
 */
@Serializable
data class FindDefinitionArgs(
    val symbolName: String?,
    val filePath: String? = null,
    val position: Int? = null
)

/**
 * Location of a symbol definition.
 */
@Serializable
data class DefinitionLocation(
    val name: String,
    val filePath: String,
    val startOffset: Int,
    val endOffset: Int,
    val lineNumber: Int,
    val type: String,
    val signature: String? = null,
    val containingClass: String? = null,
    val modifiers: List<String> = emptyList(),
    val isAbstract: Boolean = false,
    val confidence: Float = 1.0f,
    val disambiguationHint: String? = null,
    val isTestCode: Boolean = false,
    val isLibraryCode: Boolean = false,
    val accessibilityWarning: String? = null
)
