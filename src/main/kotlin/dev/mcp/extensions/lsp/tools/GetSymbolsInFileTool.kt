package dev.mcp.extensions.lsp.tools

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import dev.mcp.extensions.lsp.core.factories.SymbolExtractorFactory
import dev.mcp.extensions.lsp.core.models.GetSymbolsArgs
import dev.mcp.extensions.lsp.core.models.SymbolInfo
import dev.mcp.extensions.lsp.core.models.Visibility
import dev.mcp.extensions.lsp.core.utils.PsiUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool

/**
 * MCP tool for extracting symbols from files.
 * Provides rich, language-agnostic symbol information.
 */
class GetSymbolsInFileTool : AbstractMcpTool<GetSymbolsArgs>(GetSymbolsArgs.serializer()) {
    private val logger = Logger.getInstance(GetSymbolsInFileTool::class.java)

    override val name: String = "get_symbols_in_file"
    override val description: String = """
        Extract symbols from a file with LSP-standard symbol type filtering.
        
        **symbolTypes** (optional): Filter by Language Server Protocol standard symbol types.
        
        **Core Type Definitions (LSP 5,10,11,23):**
        - `"class"` - Class definitions (UserService, ApiController)
        - `"interface"` - Interface definitions (UserListener, Comparable)  
        - `"enum"` - Enum type definitions (UserEvent, Color, Status)
        - `"struct"` - Struct definitions (C/C++/Rust/Go)
        
        **Data & Storage (LSP 7,8,13,14,22):**
        - `"field"` - Instance/class properties (name, email, description)
        - `"constant"` - Static final values (DEFAULT_ROLE, MAX_USERS, PI)
        - `"variable"` - Local variables and parameters
        - `"property"` - Properties with getters/setters (Kotlin/C#)
        - `"enum_member"` - Enum values/constants (CREATED, UPDATED, DELETED)
        
        **Behavior & Logic (LSP 6,9,12,24,25):**
        - `"method"` - Class methods and procedures
        - `"constructor"` - Object constructors and initializers
        - `"function"` - Standalone/global functions
        - `"event"` - Event definitions (C#/JavaScript)
        - `"operator"` - Operator overloads (C++/Python)
        
        **Organization (LSP 1,2,3,4,26):**
        - `"file"` - File-level symbols
        - `"module"` - Module definitions (Python/JavaScript)
        - `"namespace"` - Namespace definitions (C++/C#)
        - `"package"` - Package definitions (Java/Go)
        - `"type_parameter"` - Generic type parameters (<T>, <K,V>)
        
        **Modern Extensions (Beyond LSP):**
        - `"component"` - UI components (React/Vue/Angular)
        - `"hook"` - React hooks (useState, useEffect)
        - `"async_function"` - Async/await functions
        - `"generator"` - Generator functions
        - `"decorator"` - Decorators/annotations
        - `"import"` - Import statements
        
        **Common Usage Patterns:**
        - All type definitions: `["class", "interface", "enum", "struct"]`
        - All data: `["field", "constant", "variable", "property", "enum_member"]`
        - All behavior: `["method", "constructor", "function"]`
        - Constants only: `["constant", "enum_member"]`
        - Java basics: `["class", "interface", "enum", "method", "field", "constant"]`
        - Find the position of a symbol
        
        **Other Parameters:**
        - filePath: Path to the file relative to project root
        - hierarchical: If true, returns nested structure (methods inside classes). If false, returns flat list
        - includeImports: Whether to include import statements
        - includePrivate: Include private members (default: true)
        - includeGenerated: Include generated/synthetic code (default: false)
        - maxDepth: Maximum nesting depth for hierarchical view
        
        Returns: List of symbols with exact positions, types, modifiers, and metadata.
        Each result includes startOffset/endOffset for follow-up interactions like precise code modifications.
        
        Supports: ${SymbolExtractorFactory.getSupportedLanguages().joinToString(", ")}
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
                    logArguments(args)

                    // Validate file exists
                    if (!PsiUtils.fileExists(project, args.filePath)) {
                        logger.warn("File not found: ${args.filePath}")
                        return@measureOperation Response(null, "File not found: ${args.filePath}")
                    }

                    // Get PSI file
                    val psiFile = PsiUtils.getPsiFile(project, args.filePath)
                    if (psiFile == null) {
                        logger.error("Cannot parse file: ${args.filePath}")
                        return@measureOperation Response(null, "Cannot parse file: ${args.filePath}")
                    }

                    logger.info("File language: ${psiFile.language.displayName} (${psiFile.language.id})")

                    // Get language-specific extractor
                    val extractor = try {
                        SymbolExtractorFactory.getExtractor(psiFile)
                    } catch (e: UnsupportedOperationException) {
                        logger.error("Unsupported file type", e)
                        return@measureOperation Response(null, e.message)
                    }

                    logger.debug("Using extractor: ${extractor.getSupportedLanguage()}")

                    // Extract symbols
                    val symbols = extractSymbols(extractor, psiFile, args)
                    logger.info("Extracted ${symbols.size} symbols from ${args.filePath}")

                    // Serialize to JSON
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
     * Extract symbols using the appropriate extractor and apply filters.
     */
    private fun extractSymbols(
        extractor: dev.mcp.extensions.lsp.core.interfaces.SymbolExtractor,
        psiFile: com.intellij.psi.PsiFile,
        args: GetSymbolsArgs
    ): List<SymbolInfo> {
        val symbols = if (args.hierarchical) {
            measureOperation("extractSymbolsHierarchical") {
                extractor.extractSymbolsHierarchical(psiFile, args)
            }
        } else {
            measureOperation("extractSymbolsFlat") {
                extractor.extractSymbolsFlat(psiFile, args)
            }
        }

        // Apply additional filters
        return filterSymbols(symbols, args)
    }

    /**
     * Apply filtering based on arguments.
     */
    private fun filterSymbols(symbols: List<SymbolInfo>, args: GetSymbolsArgs): List<SymbolInfo> {
        var filtered = symbols

        // Filter by symbol types if specified
        if (!args.symbolTypes.isNullOrEmpty()) {
            val types = args.symbolTypes.map { it.lowercase() }.toSet()
            filtered = filtered.filter { symbol ->
                types.contains(symbol.kind.value.lowercase())
            }
        }

        // Filter by visibility if includePrivate is false
        if (!args.includePrivate) {
            filtered = filtered.filter { symbol ->
                symbol.visibility != Visibility.PRIVATE
            }
        }

        // Filter out generated code if includeGenerated is false
        if (!args.includeGenerated) {
            filtered = filtered.filter { !it.isSynthetic }
        }

        // Apply max depth for hierarchical results
        if (args.hierarchical && args.maxDepth < Int.MAX_VALUE) {
            filtered = filtered.map { limitDepth(it, args.maxDepth, 0) }
        }

        return filtered
    }

    /**
     * Limit the depth of hierarchical symbols.
     */
    private fun limitDepth(symbol: SymbolInfo, maxDepth: Int, currentDepth: Int): SymbolInfo {
        return if (currentDepth >= maxDepth) {
            symbol.copy(children = null)
        } else {
            symbol.copy(
                children = symbol.children?.map { 
                    limitDepth(it, maxDepth, currentDepth + 1) 
                }
            )
        }
    }

    /**
     * Log extraction arguments for debugging.
     */
    private fun logArguments(args: GetSymbolsArgs) {
        logger.debug(buildString {
            append("Parameters: ")
            append("hierarchical=${args.hierarchical}, ")
            append("symbolTypes=${args.symbolTypes}, ")
            append("includeImports=${args.includeImports}, ")
            append("includePrivate=${args.includePrivate}, ")
            append("includeGenerated=${args.includeGenerated}, ")
            append("maxDepth=${args.maxDepth}")
        })
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
