package dev.mcp.extensions.lsp.core.models

import kotlinx.serialization.Serializable

/**
 * Arguments for getting hover information.
 */
@Serializable
data class GetHoverArgs(
    val filePath: String,
    val position: Int
)

/**
 * Hover information for a symbol.
 */
@Serializable
data class HoverInfo(
    val elementName: String,
    val elementType: String,
    val type: String? = null,
    val presentableText: String? = null,
    val javaDoc: String? = null,
    val signature: String? = null,
    val modifiers: List<String> = emptyList(),
    val superTypes: List<String> = emptyList(),
    val implementedBy: List<String> = emptyList(),
    val overriddenBy: List<String> = emptyList(),
    val calledByCount: Int = 0,
    val complexity: Int? = null,
    val throwsExceptions: List<String> = emptyList(),
    val deprecationMessage: String? = null,
    val since: String? = null,
    val seeAlso: List<String> = emptyList(),
    val isDeprecated: Boolean = false,
    val module: String? = null
)
