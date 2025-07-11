package dev.mcp.extensions.lsp.tools

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import dev.mcp.extensions.lsp.core.factories.SymbolExtractorFactory
import dev.mcp.extensions.lsp.core.models.GetSymbolsArgs
import dev.mcp.extensions.lsp.core.utils.PsiUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool

/**
 * MCP tool for extracting symbols from files.
 * Delegates to language-specific implementations via SymbolExtractorFactory.
 */
class GetSymbolsInFileTool : AbstractMcpTool<GetSymbolsArgs>(GetSymbolsArgs.serializer()) {
    private val logger = Logger.getInstance(GetSymbolsInFileTool::class.java)

    override val name: String = "get_symbols_in_file"
    override val description: String = """
        🔑 START HERE: Extract all symbols (classes, methods, fields, etc.) from a specific file.
        
        This is the ESSENTIAL first step for exploring code. Use this tool when you need to:
        - Get an overview of all classes, methods, and fields in a file
        - Check for deprecated APIs before using them
        - See which methods override parent class methods
        - Identify public vs private APIs
        - Find documented vs undocumented code
        - Understand the structure and organization of code
        
        The tool provides rich metadata for each symbol:
        - isDeprecated: Avoid using deprecated APIs
        - isOverride/overrides: Understand inheritance relationships
        - visibility: Know what's accessible (public/private/protected)
        - hasJavadoc: Identify well-documented code
        - annotations: See @Override, @Deprecated, @Test etc.
        
        Parameters:
        - filePath: Path to the file relative to project root
        - hierarchical: If true, returns nested structure (methods inside classes). If false, returns flat list
        - symbolTypes: Optional filter for specific types ["class", "interface", "method", "constructor", "field", "import"]
        - includeImports: Whether to include import statements
        
        ⚡ Precise Editing Support:
        Each result includes exact character positions:
        - startOffset: Character position where symbol begins
        - endOffset: Character position where symbol ends  
        - lineNumber: Line number (1-based)

        Use these offsets with your file editing capabilities for surgical code modifications - 
        change just a method or field without replacing entire files.
        
        💡 The startOffset values in results can be used as position parameters in other tools:
        - find_symbol_definition: Navigate to symbol declarations
        - find_symbol_references: Find all usages of symbols
        - get_hover_info: Get detailed type information
        
        Supported languages: ${SymbolExtractorFactory.getSupportedLanguages().joinToString(", ")}
        
        Returns a list of symbols with their locations, types, modifiers, and important metadata.
        Always start here to understand a file before making changes.
    """.trimIndent()

    /**
     * Handles the symbol extraction request for a file.
     *
     * @param project The IntelliJ project context
     * @param args The arguments containing file path and extraction options
     * @return Response containing extracted symbols or error message
     */
    override fun handle(project: Project, args: GetSymbolsArgs): Response {
        return ReadAction.compute<Response, Exception> {
            measureOperation("get_symbols_in_file") {
                try {
                    logger.info("Processing symbols request for file: ${args.filePath}")
                    logger.debug(
                        "Parameters: hierarchical=${args.hierarchical}, " +
                                "symbolTypes=${args.symbolTypes}, " +
                                "includeImports=${args.includeImports}"
                    )

                    if (!PsiUtils.fileExists(project, args.filePath)) {
                        logger.warn("File not found: ${args.filePath}")
                        return@measureOperation Response(null, "File not found: ${args.filePath}")
                    }

                    val psiFile = PsiUtils.getPsiFile(project, args.filePath)
                    if (psiFile == null) {
                        logger.error("Cannot parse file: ${args.filePath}")
                        return@measureOperation Response(null, "Cannot parse file: ${args.filePath}")
                    }

                    logger.info("File language: ${psiFile.language.displayName} (${psiFile.language.id})")

                    val extractor = try {
                        SymbolExtractorFactory.getExtractor(psiFile)
                    } catch (e: UnsupportedOperationException) {
                        logger.error("Unsupported file type", e)
                        return@measureOperation Response(
                            null,
                            "Unsupported language: ${psiFile.language.displayName}. " +
                                    "Supported languages: ${
                                        SymbolExtractorFactory.getSupportedLanguages().joinToString(", ")
                                    }"
                        )
                    }

                    logger.debug("Using extractor: ${extractor.getSupportedLanguage()}")

                    val symbols = if (args.hierarchical) {
                        measureOperation("extractSymbolsHierarchical") {
                            extractor.extractSymbolsHierarchical(psiFile, args)
                        }
                    } else {
                        measureOperation("extractSymbolsFlat") {
                            extractor.extractSymbolsFlat(psiFile, args)
                        }
                    }

                    logger.info("Extracted ${symbols.size} symbols from ${args.filePath}")

                    val json = Json.encodeToString(symbols)
                    logger.debug("Response size: ${json.length} characters")

                    Response(json)
                } catch (e: Exception) {
                    logger.error("Error extracting symbols from ${args.filePath}", e)
                    Response(null, "Error extracting symbols: ${e.message}")
                }
            }
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
