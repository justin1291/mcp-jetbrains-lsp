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
    override val description: String = """Go to symbol definition. Find where declared.
Use to understand symbol without reading entire file(s). 
1. Position-only: filePath + position (cursor at symbol)
2. Name-only: symbolName (searches project-wide)
3. Hybrid: all three parameters (disambiguation)

Use `get_symbols_in_file` tool to find correct position.

Params:
- symbolName: "User" or "ClassName.methodName"
- filePath (optional without position)
- position (required with filePath)

Supported: ${SymbolExtractorFactory.getSupportedLanguages().joinToString(", ")}""".trimIndent()

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
                    if (args.filePath != null && args.position != null) {
                        findDefinitionByPosition(project, args.filePath, args.position)
                    } else if (args.symbolName != null) {
                        findDefinitionByName(project, args.symbolName)
                    } else {
                        return@measureOperation Response(null, "Either symbolName or filePath+position must be provided")
                    }
                } catch (e: Exception) {
                    logger.error("Error finding symbol definition", e)
                    return@measureOperation Response(null, "Error finding symbol definition: ${e.message}")
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
        // Validate file existence
        if (!PsiUtils.fileExists(project, filePath)) {
            return Response(null, "File not found: $filePath")
        }

        // Get PSI file with null safety
        val psiFile = PsiUtils.getPsiFile(project, filePath)
        if (psiFile == null) {
            return Response(null, "Cannot parse file: $filePath")
        }

        // Validate position bounds
        val fileLength = psiFile.textLength
        if (position < 0 || position >= fileLength) {
            return Response(null, "Position $position is out of bounds for file $filePath (file length: $fileLength)")
        }

        // Get language-specific finder with error handling
        val finder = try {
            DefinitionFinderFactory.getFinder(psiFile)
        } catch (e: UnsupportedOperationException) {
            return Response(null, "Unsupported language: ${psiFile.language.displayName}")
        }

        // Find definitions with enhanced error handling
        val definitions = measureOperation("findDefinitionByPosition") {
            try {
                finder.findDefinitionByPosition(psiFile, position)
            } catch (e: Exception) {
                logger.error("Error in language-specific finder for ${psiFile.language.displayName}", e)
                emptyList()
            }
        }

        if (definitions.isEmpty()) {
            return Response(null, "No symbol definition found at position $position in $filePath")
        }

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
        // Validate symbol name
        if (symbolName.isBlank()) {
            return Response(null, "Symbol name cannot be empty")
        }

        val allDefinitions = mutableListOf<dev.mcp.extensions.lsp.core.models.DefinitionLocation>()
        val errors = mutableListOf<String>()

        try {
            val projectFiles = PsiUtils.getAllProjectFiles(project)
            if (projectFiles.isEmpty()) {
                return Response(null, "No files found in the project to search")
            }

            val processedLanguages = mutableSetOf<String>()

            for (psiFile in projectFiles) {
                val language = psiFile.language.id

                if (language in processedLanguages) continue

                try {
                    val finder = DefinitionFinderFactory.getFinder(psiFile)
                    processedLanguages.add(language)

                    val definitions = measureOperation("findDefinitionByName_$language") {
                        try {
                            finder.findDefinitionByName(project, symbolName)
                        } catch (e: Exception) {
                            logger.debug("Error searching in language $language", e)
                            errors.add("Error searching in $language: ${e.message}")
                            emptyList()
                        }
                    }

                    allDefinitions.addAll(definitions)
                } catch (e: UnsupportedOperationException) {
                    // Expected for unsupported languages
                }
            }

            allDefinitions.sortByDescending { it.confidence }

            if (allDefinitions.isEmpty()) {
                return Response(null, "No definitions found for symbol '$symbolName'")
            }

            return Response(Json.encodeToString(allDefinitions))
        } catch (e: Exception) {
            logger.error("Error finding definitions by name", e)
            return Response(null, "Error finding definitions: ${e.message}")
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
