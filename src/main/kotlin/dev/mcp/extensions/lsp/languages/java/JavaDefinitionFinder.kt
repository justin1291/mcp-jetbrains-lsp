package dev.mcp.extensions.lsp.languages.java

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiTreeUtil
import dev.mcp.extensions.lsp.core.interfaces.DefinitionFinder
import dev.mcp.extensions.lsp.core.models.DefinitionLocation
import dev.mcp.extensions.lsp.languages.base.BaseLanguageHandler

/**
 * Definition finder implementation for Java and Kotlin languages.
 * 
 * Registered as a service in mcp-lsp-java.xml when Java module is available.
 */
class JavaDefinitionFinder : BaseLanguageHandler(), DefinitionFinder {
    
    override fun findDefinitionByPosition(psiFile: PsiFile, position: Int): List<DefinitionLocation> {
        logger.info("Finding definition at position $position in ${psiFile.name}")
        
        val element = psiFile.findElementAt(position) 
            ?: return emptyList()

        // Find the reference at this position
        val reference = element.parent?.reference ?: element.reference
        if (reference != null) {
            val resolved = reference.resolve()
            if (resolved != null) {
                val location = createLocation(resolved)
                return listOf(
                    location.copy(
                        confidence = 1.0f, // Direct reference resolution
                        disambiguationHint = generateDisambiguationHint(resolved)
                    )
                )
            }
        }

        // Try to find a named element at this position
        val namedElement = PsiTreeUtil.getParentOfType(element, PsiNamedElement::class.java)
        if (namedElement != null) {
            val location = createLocation(namedElement)
            return listOf(
                location.copy(
                    confidence = 0.9f, // Found at position but not through reference
                    disambiguationHint = generateDisambiguationHint(namedElement)
                )
            )
        }

        return emptyList()
    }
    
    override fun findDefinitionByName(project: Project, symbolName: String): List<DefinitionLocation> {
        logger.info("Finding definition by name: $symbolName")
        
        val scope = GlobalSearchScope.projectScope(project)
        val definitions = mutableListOf<DefinitionLocation>()
        val cache = PsiShortNamesCache.getInstance(project)

        // Check if it's a qualified name (e.g., "ClassName.methodName")
        val parts = symbolName.split(".")
        if (parts.size == 2) {
            // Search for method in specific class
            val className = parts[0]
            val methodName = parts[1]
            val classes = JavaPsiFacade.getInstance(project).findClasses(className, scope)
            for (psiClass in classes) {
                val methods = psiClass.findMethodsByName(methodName, true)
                methods.forEach {
                    val location = createLocation(it, symbolName)
                    definitions.add(
                        location.copy(
                            confidence = 1.0f, // Exact qualified match
                            disambiguationHint = "Method in ${psiClass.qualifiedName}"
                        )
                    )
                }
            }
        } else {
            // Search for classes
            val psiClasses = JavaPsiFacade.getInstance(project).findClasses(symbolName, scope)
            psiClasses.forEach {
                val location = createLocation(it, symbolName)
                definitions.add(
                    location.copy(
                        confidence = calculateConfidence(it, symbolName, isClass = true),
                        disambiguationHint = generateDisambiguationHint(it)
                    )
                )
            }

            // Search for methods
            cache.getMethodsByName(symbolName, scope).forEach {
                val location = createLocation(it, symbolName)
                definitions.add(
                    location.copy(
                        confidence = calculateConfidence(it, symbolName, isClass = false),
                        disambiguationHint = generateDisambiguationHint(it)
                    )
                )
            }

            // Search for fields
            cache.getFieldsByName(symbolName, scope).forEach {
                val location = createLocation(it, symbolName)
                definitions.add(
                    location.copy(
                        confidence = calculateConfidence(it, symbolName, isClass = false),
                        disambiguationHint = generateDisambiguationHint(it)
                    )
                )
            }
        }

        // Sort by confidence (highest first)
        val sortedDefinitions = definitions.sortedByDescending { it.confidence }
        
        logger.debug("Found ${sortedDefinitions.size} definitions for '$symbolName'")
        return sortedDefinitions
    }
    
    override fun createLocation(element: PsiElement, searchTerm: String?): DefinitionLocation {
        val containingFile = element.containingFile
        val virtualFile = containingFile.virtualFile
        val project = element.project
        val basePath = project.basePath ?: ""
        val relativePath = virtualFile.path.removePrefix(basePath).removePrefix("/")

        val textRange = element.textRange
        val document = PsiDocumentManager.getInstance(project).getDocument(containingFile)
        val lineNumber = document?.getLineNumber(textRange.startOffset) ?: 0

        // Check if in test or library code
        val isTestCode = isInTestCode(virtualFile)
        val isLibraryCode = isInLibraryCode(virtualFile, project)

        // Generate accessibility warning
        val accessibilityWarning = generateAccessibilityWarning(element)

        return when (element) {
            is PsiClass -> DefinitionLocation(
                name = element.name ?: "anonymous",
                filePath = relativePath,
                startOffset = textRange.startOffset,
                endOffset = textRange.endOffset,
                lineNumber = lineNumber + 1,
                type = when {
                    element.isInterface -> "interface"
                    element.isEnum -> "enum"
                    element.isAnnotationType -> "annotation"
                    else -> "class"
                },
                signature = buildClassSignature(element),
                containingClass = element.containingClass?.qualifiedName,
                modifiers = extractModifiers(element.modifierList),
                isAbstract = element.hasModifierProperty(PsiModifier.ABSTRACT),
                isTestCode = isTestCode,
                isLibraryCode = isLibraryCode,
                accessibilityWarning = accessibilityWarning
            )

            is PsiMethod -> DefinitionLocation(
                name = element.name,
                filePath = relativePath,
                startOffset = textRange.startOffset,
                endOffset = textRange.endOffset,
                lineNumber = lineNumber + 1,
                type = if (element.isConstructor) "constructor" else "method",
                signature = buildMethodSignature(element),
                containingClass = element.containingClass?.qualifiedName,
                modifiers = extractModifiers(element.modifierList),
                isAbstract = element.hasModifierProperty(PsiModifier.ABSTRACT),
                isTestCode = isTestCode,
                isLibraryCode = isLibraryCode,
                accessibilityWarning = accessibilityWarning
            )

            is PsiField -> DefinitionLocation(
                name = element.name ?: "anonymous",
                filePath = relativePath,
                startOffset = textRange.startOffset,
                endOffset = textRange.endOffset,
                lineNumber = lineNumber + 1,
                type = "field",
                signature = "${element.type.presentableText} ${element.name}",
                containingClass = element.containingClass?.qualifiedName,
                modifiers = extractModifiers(element.modifierList),
                isTestCode = isTestCode,
                isLibraryCode = isLibraryCode,
                accessibilityWarning = accessibilityWarning
            )

            is PsiVariable -> DefinitionLocation(
                name = element.name ?: "anonymous",
                filePath = relativePath,
                startOffset = textRange.startOffset,
                endOffset = textRange.endOffset,
                lineNumber = lineNumber + 1,
                type = "variable",
                signature = "${element.type.presentableText} ${element.name}",
                containingClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)?.qualifiedName,
                modifiers = extractModifiers(element.modifierList),
                isTestCode = isTestCode,
                isLibraryCode = isLibraryCode,
                accessibilityWarning = accessibilityWarning
            )

            else -> DefinitionLocation(
                name = (element as? PsiNamedElement)?.name ?: "unknown",
                filePath = relativePath,
                startOffset = textRange.startOffset,
                endOffset = textRange.endOffset,
                lineNumber = lineNumber + 1,
                type = "unknown",
                signature = element.text?.lines()?.firstOrNull()?.trim(),
                isTestCode = isTestCode,
                isLibraryCode = isLibraryCode
            )
        }
    }
    
    override fun supportsFile(psiFile: PsiFile): Boolean {
        val languageId = psiFile.language.id
        return languageId == "JAVA" || languageId == "kotlin" || languageId == "Kotlin"
    }
    
    override fun supportsElement(element: PsiElement): Boolean {
        val languageId = element.language.id
        return languageId == "JAVA" || languageId == "kotlin" || languageId == "Kotlin"
    }
    
    override fun getSupportedLanguage(): String {
        return "Java/Kotlin"
    }
    
    private fun buildClassSignature(psiClass: PsiClass): String {
        val modifiers = psiClass.modifierList?.text ?: ""
        val type = when {
            psiClass.isInterface -> "interface"
            psiClass.isEnum -> "enum"
            psiClass.isAnnotationType -> "@interface"
            else -> "class"
        }
        return "$modifiers $type ${psiClass.name}".trim()
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

    private fun calculateConfidence(element: PsiElement, searchTerm: String, isClass: Boolean): Float {
        val elementName = (element as? PsiNamedElement)?.name ?: return 0.5f
        val virtualFile = element.containingFile.virtualFile
        val project = element.project

        return when {
            // Exact name match in project code
            elementName == searchTerm && !isInLibraryCode(virtualFile, project) -> {
                when {
                    isClass && element is PsiClass -> 1.0f
                    !isClass && element is PsiMember -> 0.95f
                    else -> 0.9f
                }
            }
            // Exact name match in library code
            elementName == searchTerm && isInLibraryCode(virtualFile, project) -> 0.5f
            // Case-insensitive match
            elementName.equals(searchTerm, ignoreCase = true) -> 0.7f
            // Partial match
            elementName.contains(searchTerm, ignoreCase = true) -> 0.3f
            else -> 0.1f
        }
    }

    private fun generateDisambiguationHint(element: PsiElement): String? {
        return when (element) {
            is PsiMethod -> {
                val containingClass = element.containingClass?.name ?: "Unknown"
                when {
                    element.isConstructor -> "Constructor in $containingClass"
                    element.hasModifierProperty(PsiModifier.STATIC) -> "Static method in $containingClass"
                    element.hasModifierProperty(PsiModifier.ABSTRACT) -> "Abstract method in $containingClass"
                    else -> "Method in $containingClass"
                }
            }

            is PsiField -> {
                val containingClass = element.containingClass?.qualifiedName ?: "Unknown"
                when {
                    element.hasModifierProperty(PsiModifier.STATIC) &&
                            element.hasModifierProperty(PsiModifier.FINAL) -> "Constant in $containingClass"

                    element.hasModifierProperty(PsiModifier.STATIC) -> "Static field in $containingClass"
                    else -> "Field in $containingClass"
                }
            }

            is PsiClass -> {
                val packageName = (element.containingFile as? PsiJavaFile)?.packageName
                when {
                    element.isInterface -> "Interface in ${packageName ?: "default package"}"
                    element.isEnum -> "Enum in ${packageName ?: "default package"}"
                    element.isAnnotationType -> "Annotation in ${packageName ?: "default package"}"
                    element.hasModifierProperty(PsiModifier.ABSTRACT) -> "Abstract class in ${packageName ?: "default package"}"
                    else -> "Class in ${packageName ?: "default package"}"
                }
            }

            is PsiVariable -> {
                when (element) {
                    is PsiParameter -> "Parameter"
                    is PsiLocalVariable -> "Local variable"
                    else -> "Variable"
                }
            }

            else -> null
        }
    }

    private fun generateAccessibilityWarning(element: PsiElement): String? {
        if (element !is PsiModifierListOwner) return null

        return when {
            element.hasModifierProperty(PsiModifier.PRIVATE) ->
                "Private member - not accessible from outside the declaring class"

            element.hasModifierProperty(PsiModifier.PROTECTED) ->
                "Protected member - only accessible from subclasses or same package"

            !element.hasModifierProperty(PsiModifier.PUBLIC) &&
                    !element.hasModifierProperty(PsiModifier.PROTECTED) &&
                    !element.hasModifierProperty(PsiModifier.PRIVATE) ->
                "Package-private member - only accessible from the same package"

            else -> null
        }
    }
}
