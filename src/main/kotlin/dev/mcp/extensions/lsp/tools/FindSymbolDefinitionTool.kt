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
                        createErrorResponse(
                            "Invalid arguments",
                            "Either symbolName or filePath+position must be provided",
                            listOf("Provide a symbolName parameter (e.g., 'MyClass' or 'MyClass.method')",
                                   "Provide both filePath and position parameters for position-based lookup")
                        )
                    }
                } catch (e: Exception) {
                    logger.error("Error finding symbol definition", e)
                    createErrorResponse(
                        "Internal error",
                        "Error finding symbol definition: ${e.message}",
                        listOf("Check if the file exists and is readable",
                               "Verify the project is properly indexed",
                               "Try refreshing the project structure")
                    )
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
            return createErrorResponse(
                "File not found",
                "File not found: $filePath",
                listOf("Check if the file path is correct relative to project root",
                       "Verify the file exists in the project structure",
                       "Try refreshing the project")
            )
        }

        // Get PSI file with null safety
        val psiFile = PsiUtils.getPsiFile(project, filePath)
        if (psiFile == null) {
            return createErrorResponse(
                "File parsing error",
                "Cannot parse file: $filePath",
                listOf("Check if the file has valid syntax",
                       "Verify the file encoding is supported",
                       "Try closing and reopening the file")
            )
        }

        // Validate position bounds
        val fileLength = psiFile.textLength
        if (position < 0 || position >= fileLength) {
            return createErrorResponse(
                "Position out of bounds",
                "Position $position is out of bounds for file $filePath (file length: $fileLength)",
                listOf("Use position between 0 and ${fileLength - 1}",
                       "Check if you're using the correct position calculation method")
            )
        }

        // Get language-specific finder with error handling
        val finder = try {
            DefinitionFinderFactory.getFinder(psiFile)
        } catch (e: UnsupportedOperationException) {
            return createErrorResponse(
                "Unsupported language",
                "Unsupported language: ${psiFile.language.displayName}",
                listOf("Supported languages: ${SymbolExtractorFactory.getSupportedLanguages().joinToString(", ")}",
                       "Try using a supported file type",
                       "Check if the required language plugin is installed")
            )
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
            return createErrorResponse(
                "No definition found",
                "No symbol definition found at position $position in $filePath",
                listOf("Try positioning the cursor directly on a symbol name",
                       "Check if the symbol is defined in the current project",
                       "Verify the project is properly indexed")
            )
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
            return createErrorResponse(
                "Invalid symbol name",
                "Symbol name cannot be empty",
                listOf("Provide a valid symbol name (e.g., 'MyClass' or 'MyClass.method')")
            )
        }

        val allDefinitions = mutableListOf<dev.mcp.extensions.lsp.core.models.DefinitionLocation>()
        val errors = mutableListOf<String>()

        try {
            val projectFiles = PsiUtils.getAllProjectFiles(project)
            if (projectFiles.isEmpty()) {
                return createErrorResponse(
                    "No project files found",
                    "No files found in the project to search",
                    listOf("Check if the project is properly loaded",
                           "Verify project indexing is complete",
                           "Try refreshing the project structure")
                )
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
                val suggestions = mutableListOf<String>()
                suggestions.add("Check if the symbol name is spelled correctly")
                suggestions.add("Try using a qualified name (e.g., 'ClassName.methodName')")
                suggestions.add("Verify the symbol is defined in the current project")
                suggestions.add("Check if the project is properly indexed")
                
                if (errors.isNotEmpty()) {
                    suggestions.add("Some language searches failed - check the logs for details")
                }

                return createErrorResponse(
                    "No definitions found",
                    "No definitions found for symbol '$symbolName'",
                    suggestions
                )
            }

            return Response(Json.encodeToString(allDefinitions))
        } catch (e: Exception) {
            logger.error("Error finding definitions by name", e)
            return createErrorResponse(
                "Search error",
                "Error finding definitions: ${e.message}",
                listOf("Check if the project is properly loaded",
                       "Try refreshing the project structure",
                       "Verify project indexing is complete")
            )
        }
    }

    /**
     * Creates a structured error response with debugging information.
     *
     * @param errorType Type of error for categorization
     * @param message Main error message
     * @param suggestions List of suggestions for recovery
     * @return Response with structured error information
     */
    private fun createErrorResponse(errorType: String, message: String, suggestions: List<String>): Response {
        val errorInfo = mapOf(
            "error" to errorType,
            "message" to message,
            "suggestions" to suggestions,
            "timestamp" to System.currentTimeMillis()
        )
        
        return Response(
            status = Json.encodeToString(errorInfo),
            error = message
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
