package dev.mcp.extensions.lsp.tools

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool
import java.io.File

@Serializable
data class GetHoverArgs(
    val filePath: String,
    val position: Int
)

@Serializable
data class HoverInfo(
    val elementName: String,
    val elementType: String,
    val type: String? = null,
    val presentableText: String? = null,
    val javaDoc: String? = null,
    val signature: String? = null,
    val modifiers: List<String> = emptyList()
)

class GetHoverInfoTool : AbstractMcpTool<GetHoverArgs>(GetHoverArgs.serializer()) {
    override val name: String = "get_hover_info"
    override val description: String = """
        Get type information and documentation for a symbol at a specific position.
        
        Use this tool when you need to:
        - See the type of a variable or expression
        - Read documentation without navigating away
        - Understand method signatures and parameters
        - Check the modifiers of a field or method
        - Get quick information about any code element
        
        Parameters:
        - filePath: Path to the file relative to project root
        - position: Character offset in the file where to get hover info
        
        Returns detailed information about the element at the position including:
        - Element name and type
        - Full type information for variables
        - Method signatures with parameters
        - JavaDoc/KDoc documentation
        - Access modifiers
        
        This is equivalent to hovering over code in an IDE or using Ctrl+Q for quick documentation.
    """.trimIndent()

    override fun handle(project: Project, args: GetHoverArgs): Response {
        return ReadAction.compute<Response, Exception> {
            try {
                val file = File(project.basePath, args.filePath)
                if (!file.exists()) {
                    return@compute Response(null, "File not found: ${args.filePath}")
                }

                val psiFile = PsiManager.getInstance(project).findFile(
                    VfsUtil.findFileByIoFile(file, true) ?: return@compute Response(null, "Cannot find file in VFS")
                ) ?: return@compute Response(null, "Cannot parse file")

                val element = psiFile.findElementAt(args.position) 
                    ?: return@compute Response(null, "No element at position ${args.position}")

                // Try to resolve reference first
                val reference = element.parent?.reference ?: element.reference
                val resolved = reference?.resolve()
                
                // Get the most relevant element
                val targetElement = resolved 
                    ?: PsiTreeUtil.getParentOfType(element, PsiNamedElement::class.java)
                    ?: element

                val hoverInfo = createHoverInfo(targetElement)
                Response(Json.encodeToString(hoverInfo))
            } catch (e: Exception) {
                Response(null, "Error getting hover info: ${e.message}")
            }
        }
    }

    private fun createHoverInfo(element: PsiElement): HoverInfo {
        return when (element) {
            is PsiClass -> createClassHoverInfo(element)
            is PsiMethod -> createMethodHoverInfo(element)
            is PsiField -> createFieldHoverInfo(element)
            is PsiVariable -> createVariableHoverInfo(element)
            is PsiAnnotation -> createAnnotationHoverInfo(element)
            else -> createGenericHoverInfo(element)
        }
    }

    private fun createClassHoverInfo(psiClass: PsiClass): HoverInfo {
        val type = when {
            psiClass.isInterface -> "interface"
            psiClass.isEnum -> "enum"
            psiClass.isAnnotationType -> "annotation"
            else -> "class"
        }
        
        return HoverInfo(
            elementName = psiClass.name ?: "anonymous",
            elementType = type,
            type = psiClass.qualifiedName,
            presentableText = buildClassPresentableText(psiClass),
            javaDoc = extractJavaDoc(psiClass),
            signature = buildClassSignature(psiClass),
            modifiers = extractModifiers(psiClass.modifierList)
        )
    }

    private fun createMethodHoverInfo(method: PsiMethod): HoverInfo {
        val params = method.parameterList.parameters.joinToString(", ") { 
            "${it.type.presentableText} ${it.name}" 
        }
        
        return HoverInfo(
            elementName = method.name,
            elementType = if (method.isConstructor) "constructor" else "method",
            type = method.returnType?.presentableText ?: if (method.isConstructor) "" else "void",
            presentableText = "${method.name}($params): ${method.returnType?.presentableText ?: "void"}",
            javaDoc = extractJavaDoc(method),
            signature = buildMethodSignature(method),
            modifiers = extractModifiers(method.modifierList)
        )
    }

    private fun createFieldHoverInfo(field: PsiField): HoverInfo {
        return HoverInfo(
            elementName = field.name ?: "anonymous",
            elementType = "field",
            type = field.type.presentableText,
            presentableText = "${field.name}: ${field.type.presentableText}",
            javaDoc = extractJavaDoc(field),
            signature = "${field.type.presentableText} ${field.name}",
            modifiers = extractModifiers(field.modifierList)
        )
    }

    private fun createVariableHoverInfo(variable: PsiVariable): HoverInfo {
        val varType = when (variable) {
            is PsiParameter -> "parameter"
            is PsiLocalVariable -> "variable"
            else -> "variable"
        }
        
        return HoverInfo(
            elementName = variable.name ?: "anonymous",
            elementType = varType,
            type = variable.type.presentableText,
            presentableText = "${variable.name}: ${variable.type.presentableText}",
            javaDoc = null,
            signature = "${variable.type.presentableText} ${variable.name}",
            modifiers = extractModifiers(variable.modifierList)
        )
    }

    private fun createAnnotationHoverInfo(annotation: PsiAnnotation): HoverInfo {
        val qualifiedName = annotation.qualifiedName ?: "unknown"
        return HoverInfo(
            elementName = qualifiedName.substringAfterLast('.'),
            elementType = "annotation",
            type = qualifiedName,
            presentableText = "@${qualifiedName.substringAfterLast('.')}",
            javaDoc = null,
            signature = annotation.text,
            modifiers = emptyList()
        )
    }

    private fun createGenericHoverInfo(element: PsiElement): HoverInfo {
        val namedElement = element as? PsiNamedElement
        return HoverInfo(
            elementName = namedElement?.name ?: element.text?.take(50) ?: "unknown",
            elementType = element.javaClass.simpleName.lowercase().replace("psi", "").replace("impl", ""),
            type = null,
            presentableText = element.text?.lines()?.firstOrNull()?.trim(),
            javaDoc = null,
            signature = null,
            modifiers = emptyList()
        )
    }

    private fun buildClassPresentableText(psiClass: PsiClass): String {
        val type = when {
            psiClass.isInterface -> "interface"
            psiClass.isEnum -> "enum"
            psiClass.isAnnotationType -> "@interface"
            else -> "class"
        }
        return "$type ${psiClass.name}"
    }

    private fun buildClassSignature(psiClass: PsiClass): String {
        val modifiers = psiClass.modifierList?.text ?: ""
        val type = when {
            psiClass.isInterface -> "interface"
            psiClass.isEnum -> "enum"
            psiClass.isAnnotationType -> "@interface"
            else -> "class"
        }
        val extends = psiClass.extendsList?.referencedTypes?.firstOrNull()?.presentableText?.let { " extends $it" } ?: ""
        val implements = psiClass.implementsList?.referencedTypes?.joinToString(", ") { it.presentableText }?.let { " implements $it" } ?: ""
        
        return "$modifiers $type ${psiClass.name}$extends$implements".trim()
    }

    private fun buildMethodSignature(method: PsiMethod): String {
        val params = method.parameterList.parameters.joinToString(", ") { 
            "${it.type.presentableText} ${it.name}" 
        }
        val returnType = if (method.isConstructor) "" else "${method.returnType?.presentableText ?: "void"} "
        val modifiers = method.modifierList.text
        val throws = method.throwsList.referencedTypes.takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { it.presentableText }
            ?.let { " throws $it" } ?: ""
        
        return "$modifiers $returnType${method.name}($params)$throws".trim()
    }

    private fun extractJavaDoc(element: PsiDocCommentOwner): String? {
        val docComment = element.docComment ?: return null
        
        // Build JavaDoc string
        val builder = StringBuilder()
        builder.append("/**\n")
        
        // Add description
        val descriptionElements = docComment.descriptionElements
        if (descriptionElements.isNotEmpty()) {
            builder.append(" * ")
            builder.append(descriptionElements.joinToString("") { it.text }.trim())
            builder.append("\n")
        }
        
        // Add tags
        docComment.tags.forEach { tag ->
            builder.append(" * @${tag.name}")
            tag.dataElements.forEach { data ->
                builder.append(" ${data.text}")
            }
            builder.append("\n")
        }
        
        builder.append(" */")
        return builder.toString()
    }

    private fun extractModifiers(modifierList: PsiModifierList?): List<String> {
        if (modifierList == null) return emptyList()

        return listOf(
            PsiModifier.PUBLIC,
            PsiModifier.PRIVATE,
            PsiModifier.PROTECTED,
            PsiModifier.STATIC,
            PsiModifier.FINAL,
            PsiModifier.ABSTRACT,
            PsiModifier.SYNCHRONIZED,
            PsiModifier.VOLATILE,
            PsiModifier.TRANSIENT,
            PsiModifier.NATIVE,
            PsiModifier.STRICTFP
        ).filter { modifierList.hasModifierProperty(it) }
    }
}
