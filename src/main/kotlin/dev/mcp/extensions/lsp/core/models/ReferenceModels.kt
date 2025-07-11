package dev.mcp.extensions.lsp.core.models

import kotlinx.serialization.Serializable

/**
 * Arguments for finding symbol references.
 */
@Serializable
data class FindReferencesArgs(
    val symbolName: String?,
    val filePath: String? = null,
    val position: Int? = null,
    val includeDeclaration: Boolean = false
)

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
    val isInDeprecatedCode: Boolean = false
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
