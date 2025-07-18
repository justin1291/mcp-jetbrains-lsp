package dev.mcp.extensions.lsp.core.models

import kotlinx.serialization.Serializable

/**
 * Arguments for finding symbol references.
 * 
 * Usage modes:
 * 1. By name: Provide only symbolName
 * 2. By position: Provide filePath and position
 * 3. Hybrid: Provide all three for disambiguation
 * 
 * Either symbolName OR (filePath AND position) must be provided.
 */
@Serializable
data class FindReferencesArgs(
    val symbolName: String? = null,
    val filePath: String? = null,
    val position: Int? = null,
    val includeDeclaration: Boolean = false
) {
    init {
        require(symbolName != null || (filePath != null && position != null)) {
            "Either symbolName or both filePath and position must be provided"
        }
    }
}

/**
 * Information about a symbol reference.
 */
@Serializable
data class ReferenceInfo(
    val filePath: String,
    val startOffset: Int,
    val endOffset: Int,
    val lineNumber: Int,
    val usageType: String,
    val elementText: String? = null,
    val preview: String? = null,
    val containingMethod: String? = null,
    val containingClass: String? = null,
    val isInTestCode: Boolean = false,
    val isInComment: Boolean = false,
    val accessModifier: String? = null,
    val surroundingContext: String? = null,
    val dataFlowContext: String? = null,
    val isInDeprecatedCode: Boolean = false,
    val languageFeatures: Map<String, String>? = null
)

/**
 * Grouped references result with insights.
 */
@Serializable
data class GroupedReferencesResult(
    val summary: ReferenceSummary,
    val usagesByType: Map<String, List<ReferenceInfo>>,
    val insights: List<String>,
    val allReferences: List<ReferenceInfo>
)

/**
 * Summary of reference search results.
 */
@Serializable
data class ReferenceSummary(
    val totalReferences: Int,
    val fileCount: Int,
    val hasTestUsages: Boolean,
    val primaryUsageLocation: String? = null,
    val deprecatedUsageCount: Int = 0
)
