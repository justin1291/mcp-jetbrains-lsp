package dev.mcp.extensions.lsp.tools

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import dev.mcp.extensions.lsp.core.factories.DefinitionFinderFactory
import dev.mcp.extensions.lsp.core.factories.SymbolExtractorFactory
import dev.mcp.extensions.lsp.core.models.FindDefinitionArgs
import dev.mcp.extensions.lsp.core.utils.PsiUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool

/**
 * MCP tool for finding symbol definitions.
 * Delegates to language-specific implementations via DefinitionFinderFactory.
 */
class FindSymbolDefinitionTool : AbstractMcpTool<FindDefinitionArgs>(FindDefinitionArgs.serializer()) {
    private val logger = Logger.getInstance(FindSymbolDefinitionTool::class.java)

    override val name: String = "find_symbol_definition"
    override val description: String = """
        Go to symbol definition with disambiguation. Find where declared.
        
        Use when: find declaration, jump to source, understand impl, resolve ambiguous refs
        
        Returns sorted by confidence:
        - 1.0: exact match/direct ref
        - 0.95: project member match  
        - 0.5: library match
        - lower: partial matches
        
        Each result has:
        - disambiguationHint: "Constructor in UserService", "Static method in Utils"
        - isTestCode/isLibraryCode flags
        - accessibilityWarning: "Private member - not accessible"
        - exact offsets for edits
        
        Search by:
        - symbolName: "User" or "ClassName.methodName"
        - filePath + position: resolve ref at location
        
        Supported languages: ${SymbolExtractorFactory.getSupportedLanguages().joinToString(", ")}
        
        Better than LSP: confidence scoring, disambiguation, accessibility checks
    """.trimIndent()

    /**
     * Handles the symbol definition finding request.
     *
     * @param project The IntelliJ project context
     * @param args The arguments containing either symbol name or file path with position
     * @return Response containing definition locations or error message
     */
    override fun handle(project: Project, args: FindDefinitionArgs): Response {
        return ReadAction.compute<Response, Exception> {
            measureOperation("find_symbol_definition") {
                try {
                    logger.info(
                        "Processing definition request: symbolName=${args.symbolName}, " +
                                "filePath=${args.filePath}, position=${args.position}"
                    )

                    if (args.filePath != null && args.position != null) {
                        findDefinitionByPosition(project, args.filePath, args.position)
                    } else if (args.symbolName != null) {
                        findDefinitionByName(project, args.symbolName)
                    } else {
                        logger.warn("Neither symbolName nor filePath+position provided")
                        Response(null, "Either symbolName or filePath+position must be provided")
                    }
                } catch (e: Exception) {
                    logger.error("Error finding symbol definition", e)
                    Response(null, "Error finding symbol definition: ${e.message}")
                }
            }
        }
    }

    /**
     * Finds symbol definition at a specific position in a file.
     *
     * @param project The IntelliJ project context
     * @param filePath Path to the file relative to project root
     * @param position Character offset in the file
     * @return Response containing definition locations
     */
    private fun findDefinitionByPosition(project: Project, filePath: String, position: Int): Response {
        logger.debug("Finding definition by position: $filePath:$position")

        if (!PsiUtils.fileExists(project, filePath)) {
            logger.warn("File not found: $filePath")
            return Response(null, "File not found: $filePath")
        }

        val psiFile = PsiUtils.getPsiFile(project, filePath)
        if (psiFile == null) {
            logger.error("Cannot parse file: $filePath")
            return Response(null, "Cannot parse file: $filePath")
        }

        logger.info("File language: ${psiFile.language.displayName} (${psiFile.language.id})")

        val finder = try {
            DefinitionFinderFactory.getFinder(psiFile)
        } catch (e: UnsupportedOperationException) {
            logger.error("Unsupported file type", e)
            return Response(
                null,
                "Unsupported language: ${psiFile.language.displayName}. " +
                        "Supported languages: ${SymbolExtractorFactory.getSupportedLanguages().joinToString(", ")}"
            )
        }

        logger.debug("Using finder: ${finder.getSupportedLanguage()}")

        val definitions = measureOperation("findDefinitionByPosition") {
            finder.findDefinitionByPosition(psiFile, position)
        }

        logger.info("Found ${definitions.size} definitions at position $position")

        return Response(Json.encodeToString(definitions))
    }

    /**
     * Finds symbol definitions by name across the entire project.
     *
     * @param project The IntelliJ project context
     * @param symbolName Name of the symbol to find
     * @return Response containing definition locations sorted by confidence
     */
    private fun findDefinitionByName(project: Project, symbolName: String): Response {
        logger.debug("Finding definition by name: $symbolName")

        val allDefinitions = mutableListOf<dev.mcp.extensions.lsp.core.models.DefinitionLocation>()

        try {
            val projectFiles = PsiUtils.getAllProjectFiles(project)
            val processedLanguages = mutableSetOf<String>()

            for (psiFile in projectFiles) {
                val language = psiFile.language.id

                if (language in processedLanguages) continue

                try {
                    val finder = DefinitionFinderFactory.getFinder(psiFile)
                    processedLanguages.add(language)

                    val definitions = measureOperation("findDefinitionByName_$language") {
                        finder.findDefinitionByName(project, symbolName)
                    }

                    allDefinitions.addAll(definitions)
                } catch (e: UnsupportedOperationException) {
                    logger.debug("Skipping unsupported language: $language")
                }
            }

            allDefinitions.sortByDescending { it.confidence }

            logger.info("Found ${allDefinitions.size} definitions for '$symbolName' across all languages")

            return Response(Json.encodeToString(allDefinitions))
        } catch (e: Exception) {
            logger.error("Error finding definitions by name", e)
            return Response(
                null,
                "Error finding definitions: ${e.message}"
            )
        }
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
