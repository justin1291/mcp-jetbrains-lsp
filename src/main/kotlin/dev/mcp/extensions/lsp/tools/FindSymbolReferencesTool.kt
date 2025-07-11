package dev.mcp.extensions.lsp.tools

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import dev.mcp.extensions.lsp.core.factories.ReferenceFinderFactory
import dev.mcp.extensions.lsp.core.factories.SymbolExtractorFactory
import dev.mcp.extensions.lsp.core.interfaces.ReferenceFinder
import dev.mcp.extensions.lsp.core.models.FindReferencesArgs
import dev.mcp.extensions.lsp.core.models.GroupedReferencesResult
import dev.mcp.extensions.lsp.core.models.ReferenceSummary
import dev.mcp.extensions.lsp.core.utils.PsiUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool

/**
 * MCP tool for finding symbol references/usages.
 * Delegates to language-specific implementations via ReferenceFinderFactory.
 */
class FindSymbolReferencesTool : AbstractMcpTool<FindReferencesArgs>(FindReferencesArgs.serializer()) {
    private val logger = Logger.getInstance(FindSymbolReferencesTool::class.java)

    override val name: String = "find_symbol_references"
    override val description: String = """
        Find all usages of symbol. Returns grouped results with insights.
        
        Use when: need impact analysis, find callers, refactoring prep, usage patterns
        
        Returns:
        - Grouped by usage type (method_call, field_read, field_write, etc)
        - Insights: "Primary usage in UserController (15 calls)", "No test usage - add tests", "3 deprecated usages"
        - Data flow context: "passed as argument", "returned from method", "used in condition"
        - Each ref has: location, usage type, test/deprecated flags, surrounding code
        
        Usage types: method_call, static_method_call, getter_call, setter_call, field_read, field_write, field_increment, constructor_call, type_reference, method_override
        
        Params:
        - symbolName: name to find
        - filePath + position: find at specific location
        - includeDeclaration: include original declaration
        
        Supported languages: ${SymbolExtractorFactory.getSupportedLanguages().joinToString(", ")}
        
        Offsets included for precise edits. Better than basic find-usages: categorizes, provides insights, shows data flow.
    """.trimIndent()

    /**
     * Handles the symbol references finding request.
     *
     * @param project The IntelliJ project context
     * @param args The arguments containing search criteria
     * @return Response containing grouped references with insights
     */
    override fun handle(project: Project, args: FindReferencesArgs): Response {
        return ReadAction.compute<Response, Exception> {
            measureOperation("find_symbol_references") {
                try {
                    logger.info(
                        "Processing references request: symbolName=${args.symbolName}, " +
                                "filePath=${args.filePath}, position=${args.position}, " +
                                "includeDeclaration=${args.includeDeclaration}"
                    )

                    val targetElementAndFinder = findTargetElementAndFinder(project, args)
                    if (targetElementAndFinder == null) {
                        logger.warn("Target element not found")
                        return@measureOperation Response(
                            Json.encodeToString(createEmptyResult())
                        )
                    }

                    val (targetElement, finder) = targetElementAndFinder

                    logger.info("Found target element: ${targetElement.javaClass.simpleName}")
                    logger.debug("Element language: ${targetElement.language.displayName} (${targetElement.language.id})")
                    logger.debug("Using finder: ${finder.getSupportedLanguage()}")

                    val references = measureOperation("findReferences") {
                        finder.findReferences(project, targetElement, args)
                    }

                    logger.info("Found ${references.size} references")

                    val groupedResult = measureOperation("createGroupedResult") {
                        finder.createGroupedResult(references, targetElement)
                    }

                    logger.debug("Created grouped result with ${groupedResult.insights.size} insights")

                    Response(Json.encodeToString(groupedResult))
                } catch (e: Exception) {
                    logger.error("Error finding symbol references", e)
                    Response(null, "Error finding symbol references: ${e.message}")
                }
            }
        }
    }

    /**
     * Finds the target element and appropriate reference finder.
     *
     * @param project The IntelliJ project context
     * @param args The search arguments
     * @return Pair of target element and its reference finder, or null if not found
     */
    private fun findTargetElementAndFinder(
        project: Project,
        args: FindReferencesArgs
    ): Pair<com.intellij.psi.PsiElement, ReferenceFinder>? {
        return if (args.filePath != null && args.position != null) {
            findByPosition(project, args)
        } else if (args.symbolName != null) {
            findByName(project, args)
        } else {
            logger.warn("Neither symbolName nor filePath+position provided")
            null
        }
    }

    /**
     * Finds target element by position in file.
     *
     * @param project The IntelliJ project context
     * @param args The search arguments with file path and position
     * @return Pair of target element and finder, or null if not found
     */
    private fun findByPosition(
        project: Project,
        args: FindReferencesArgs
    ): Pair<com.intellij.psi.PsiElement, ReferenceFinder>? {
        val psiFile = PsiUtils.getPsiFile(project, args.filePath!!)
        if (psiFile == null) {
            logger.warn("Cannot find file: ${args.filePath}")
            return null
        }

        val elementAtPosition = psiFile.findElementAt(args.position!!)
        if (elementAtPosition == null) {
            logger.warn("No element at position ${args.position} in file ${args.filePath}")
            return null
        }

        val finder = try {
            ReferenceFinderFactory.getFinder(elementAtPosition)
        } catch (e: UnsupportedOperationException) {
            logger.error("Unsupported file type for references", e)
            return null
        }

        val targetElement = finder.findTargetElement(project, args)
        if (targetElement == null) {
            logger.warn("Cannot resolve target element at position")
            return null
        }

        return targetElement to finder
    }

    /**
     * Finds target element by name across the project.
     *
     * @param project The IntelliJ project context
     * @param args The search arguments with symbol name
     * @return Pair of target element and finder, or null if not found
     */
    private fun findByName(
        project: Project,
        args: FindReferencesArgs
    ): Pair<com.intellij.psi.PsiElement, ReferenceFinder>? {
        val projectFiles = PsiUtils.getAllProjectFiles(project)

        for (psiFile in projectFiles) {
            try {
                val dummyElement = psiFile.firstChild ?: continue
                val finder = try {
                    ReferenceFinderFactory.getFinder(dummyElement)
                } catch (e: UnsupportedOperationException) {
                    continue
                }

                val targetElement = finder.findTargetElement(project, args)
                if (targetElement != null) {
                    logger.info("Found target element '${args.symbolName}' using ${finder.getSupportedLanguage()} finder")
                    return targetElement to finder
                }
            } catch (e: Exception) {
                logger.debug("Error searching in file ${psiFile.name}: ${e.message}")
            }
        }

        logger.warn("Symbol '${args.symbolName}' not found in any supported language")
        return null
    }

    /**
     * Creates an empty result when no references are found.
     *
     * @return Empty grouped references result
     */
    private fun createEmptyResult(): GroupedReferencesResult {
        return GroupedReferencesResult(
            summary = ReferenceSummary(0, 0, false),
            usagesByType = emptyMap(),
            insights = listOf("Symbol not found"),
            allReferences = emptyList()
        )
    }

    /**
     * Measures the execution time of an operation and logs the result.
     *
     * @param operationName Name of the operation for logging
     * @param block The operation to measure
     * @return The result of the operation
     */
    private fun <T> measureOperation(operationName: String, block: () -> T): T {
        val start = System.currentTimeMillis()
        return try {
            block().also {
                val duration = System.currentTimeMillis() - start
                logger.debug("Operation '$operationName' completed in ${duration}ms")
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            logger.error("Operation '$operationName' failed after ${duration}ms", e)
            throw e
        }
    }
}
