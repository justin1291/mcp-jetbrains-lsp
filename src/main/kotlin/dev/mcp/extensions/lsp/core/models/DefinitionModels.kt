package dev.mcp.extensions.lsp.core.models

import kotlinx.serialization.Serializable

/**
 * Arguments for finding symbol definitions.
 * 
 * Usage modes:
 * 1. By name: Provide only symbolName
 * 2. By position: Provide filePath and position
 * 3. Hybrid: Provide all three for disambiguation
 * 
 * Either symbolName OR (filePath AND position) must be provided.
 */
@Serializable
data class FindDefinitionArgs(
    val symbolName: String? = null,
    val filePath: String? = null,
    val position: Int? = null
) {
    init {
        require(symbolName != null || (filePath != null && position != null)) {
            "Either symbolName or both filePath and position must be provided"
        }
    }
}

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
