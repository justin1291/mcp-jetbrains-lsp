package dev.mcp.extensions.lsp.tools

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import dev.mcp.extensions.lsp.core.factories.HoverInfoProviderFactory
import dev.mcp.extensions.lsp.core.factories.SymbolExtractorFactory
import dev.mcp.extensions.lsp.core.models.GetHoverArgs
import dev.mcp.extensions.lsp.core.utils.PsiUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool

/**
 * MCP tool for getting hover information about symbols.
 * Delegates to language-specific implementations via HoverInfoProviderFactory.
 */
class GetHoverInfoTool : AbstractMcpTool<GetHoverArgs>(GetHoverArgs.serializer()) {
    private val logger = Logger.getInstance(GetHoverInfoTool::class.java)

    override val name: String = "get_hover_info"
    override val description: String = """Get type info and docs at position.

Use when: need type info, read docs, check signatures, see modifiers

Returns:
- Basic: name, type, signature, modifiers, javaDoc, jsDoc
- Classes: superTypes, implementedBy (interfaces)
- Methods: overriddenBy, calledByCount, complexity, throwsExceptions
- Extracted tags: @since, @see, @deprecated message
- Fields: usage count

Params:
- filePath: relative to project
- position: char offset (use get_symbols_in_file startOffset)

Workflow: get_symbols_in_file -> use startOffset -> hover for details

Supported languages: ${SymbolExtractorFactory.getSupportedLanguages().joinToString(", ")}
""".trimIndent()

    /**
     * Handles the hover information request for a specific position in a file.
     *
     * @param project The IntelliJ project context
     * @param args The arguments containing file path and position
     * @return Response containing hover information or error message
     */
    override fun handle(project: Project, args: GetHoverArgs): Response {
        return ReadAction.compute<Response, Exception> {
            measureOperation("get_hover_info") {
                try {
                    logger.info("Processing hover info request for file: ${args.filePath}, position: ${args.position}")

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

                    val provider = try {
                        HoverInfoProviderFactory.getProvider(psiFile)
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

                    logger.debug("Using provider: ${provider.getSupportedLanguage()}")

                    val hoverInfo = measureOperation("getHoverInfoAtPosition") {
                        provider.getHoverInfoAtPosition(psiFile, args.position)
                    }

                    if (hoverInfo == null) {
                        logger.warn("No element found at position ${args.position}")
                        return@measureOperation Response(null, "No element at position ${args.position}")
                    }

                    logger.info("Got hover info for: ${hoverInfo.elementName} (${hoverInfo.elementType})")

                    Response(Json.encodeToString(hoverInfo))
                } catch (e: Exception) {
                    logger.error("Error getting hover info", e)
                    Response(null, "Error getting hover info: ${e.message}")
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
