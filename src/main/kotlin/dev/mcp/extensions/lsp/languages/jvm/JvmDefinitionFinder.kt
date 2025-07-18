package dev.mcp.extensions.lsp.languages.jvm

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import dev.mcp.extensions.lsp.core.interfaces.DefinitionFinder
import dev.mcp.extensions.lsp.core.models.DefinitionLocation
import dev.mcp.extensions.lsp.core.utils.LanguageUtils
import dev.mcp.extensions.lsp.languages.base.BaseLanguageHandler
import org.jetbrains.uast.*

@Service
class JvmDefinitionFinder : BaseLanguageHandler(), DefinitionFinder {

    override fun findDefinitionByName(project: Project, symbolName: String): List<DefinitionLocation> {
        if (symbolName.isBlank()) return emptyList()

        try {
            val definitions = mutableListOf<DefinitionLocation>()

            val projectScope = GlobalSearchScope.projectScope(project)
            val allScope = GlobalSearchScope.allScope(project)
            val everythingScope = GlobalSearchScope.everythingScope(project)

            val symbolParts = symbolName.split(".")
            if (symbolParts.size >= 2) {
                definitions.addAll(findQualifiedDefinition(project, symbolParts, projectScope))
                definitions.addAll(findQualifiedDefinition(project, symbolParts, allScope))
            } else {
                val cache = PsiShortNamesCache.getInstance(project)
                val javaPsiFacade = JavaPsiFacade.getInstance(project)

                definitions.addAll(searchInScope(cache, javaPsiFacade, symbolName, projectScope, 1.0f))
                definitions.addAll(searchInScope(cache, javaPsiFacade, symbolName, allScope, 0.7f))

                if (definitions.isEmpty()) {
                    definitions.addAll(searchInScope(cache, javaPsiFacade, symbolName, everythingScope, 0.5f))
                }
            }

            if (definitions.isEmpty()) {
                definitions.addAll(performManualSearch(project, symbolName))
            }

            return definitions.sortedByDescending { it.confidence }

        } catch (e: Exception) {
            logger.warn("Error finding definition by name '$symbolName': ${e.message}")
            return emptyList()
        }
    }

    private fun findQualifiedDefinition(
        project: Project,
        symbolParts: List<String>,
        scope: GlobalSearchScope
    ): List<DefinitionLocation> {
        val definitions = mutableListOf<DefinitionLocation>()
        val className = symbolParts[0]
        val memberName = symbolParts.subList(1, symbolParts.size).joinToString(".")

        val javaPsiFacade = JavaPsiFacade.getInstance(project)

        val psiClass = javaPsiFacade.findClass(className, scope)
            ?: PsiShortNamesCache.getInstance(project).getClassesByName(className, scope).firstOrNull()

        if (psiClass != null && LanguageUtils.isJvmLanguage(psiClass.language)) {
            findMemberInClass(psiClass, memberName, definitions, scope == GlobalSearchScope.projectScope(project))
        }

        return definitions
    }

    private fun findMemberInClass(
        psiClass: PsiClass,
        memberName: String,
        definitions: MutableList<DefinitionLocation>,
        isProjectScope: Boolean
    ) {
        psiClass.allFields.filter { it.name == memberName }.forEach { field ->
            val uField = field.toUElementOfType<UField>()
            if (uField != null) {
                val location = createLocationSafely(uField)
                if (location != null) {
                    definitions.add(
                        location.copy(
                            confidence = if (isProjectScope) 1.0f else 0.7f,
                            disambiguationHint = "Static field in ${psiClass.qualifiedName}",
                            accessibilityWarning = generateAccessibilityWarning(uField)
                        )
                    )
                }
            }
        }

        psiClass.allMethods.filter { it.name == memberName }.forEach { method ->
            val uMethod = method.toUElementOfType<UMethod>()
            if (uMethod != null) {
                val location = createLocationSafely(uMethod)
                if (location != null) {
                    definitions.add(
                        location.copy(
                            confidence = if (isProjectScope) 1.0f else 0.7f,
                            disambiguationHint = "Method in ${psiClass.qualifiedName}",
                            accessibilityWarning = generateAccessibilityWarning(uMethod)
                        )
                    )
                }
            }
        }

        psiClass.innerClasses.filter { it.name == memberName }.forEach { innerClass ->
            val uClass = innerClass.toUElementOfType<UClass>()
            if (uClass != null) {
                val location = createLocationSafely(uClass)
                if (location != null) {
                    definitions.add(
                        location.copy(
                            confidence = if (isProjectScope) 1.0f else 0.7f,
                            disambiguationHint = "Inner class in ${psiClass.qualifiedName}",
                            accessibilityWarning = generateAccessibilityWarning(uClass)
                        )
                    )
                }
            }
        }
    }

    private fun searchInScope(
        cache: PsiShortNamesCache,
        javaPsiFacade: JavaPsiFacade,
        symbolName: String,
        scope: GlobalSearchScope,
        baseConfidence: Float
    ): List<DefinitionLocation> {
        val definitions = mutableListOf<DefinitionLocation>()

        javaPsiFacade.findClass(symbolName, scope)?.let { psiClass ->
            if (LanguageUtils.isJvmLanguage(psiClass.language)) {
                val uClass = psiClass.toUElementOfType<UClass>()
                if (uClass != null) {
                    val location = createLocationSafely(uClass)
                    if (location != null) {
                        definitions.add(
                            location.copy(
                                confidence = baseConfidence,
                                disambiguationHint = generateDisambiguationHint(uClass),
                                accessibilityWarning = generateAccessibilityWarning(uClass)
                            )
                        )
                    }
                }
            }
        }

        cache.getClassesByName(symbolName, scope).forEach { psiClass ->
            if (LanguageUtils.isJvmLanguage(psiClass.language)) {
                val uClass = psiClass.toUElementOfType<UClass>()
                if (uClass != null) {
                    val location = createLocationSafely(uClass)
                    if (location != null) {
                        definitions.add(
                            location.copy(
                                confidence = calculateConfidence(uClass, symbolName, true) * baseConfidence,
                                disambiguationHint = generateDisambiguationHint(uClass),
                                accessibilityWarning = generateAccessibilityWarning(uClass)
                            )
                        )
                    }
                }
            }
        }

        cache.getMethodsByName(symbolName, scope).forEach { psiMethod ->
            if (LanguageUtils.isJvmLanguage(psiMethod.language)) {
                val uMethod = psiMethod.toUElementOfType<UMethod>()
                if (uMethod != null) {
                    val location = createLocationSafely(uMethod)
                    if (location != null) {
                        definitions.add(
                            location.copy(
                                confidence = calculateConfidence(uMethod, symbolName, false) * baseConfidence,
                                disambiguationHint = generateDisambiguationHint(uMethod),
                                accessibilityWarning = generateAccessibilityWarning(uMethod)
                            )
                        )
                    }
                }
            }
        }

        cache.getFieldsByName(symbolName, scope).forEach { psiField ->
            if (LanguageUtils.isJvmLanguage(psiField.language)) {
                val uField = psiField.toUElementOfType<UField>()
                if (uField != null) {
                    val location = createLocationSafely(uField)
                    if (location != null) {
                        definitions.add(
                            location.copy(
                                confidence = calculateConfidence(uField, symbolName, false) * baseConfidence,
                                disambiguationHint = generateDisambiguationHint(uField),
                                accessibilityWarning = generateAccessibilityWarning(uField)
                            )
                        )
                    }
                }
            }
        }

        return definitions
    }

    private fun performManualSearch(project: Project, symbolName: String): List<DefinitionLocation> {
        val definitions = mutableListOf<DefinitionLocation>()
        val visitedPaths = mutableSetOf<String>()

        project.basePath?.let { basePath ->
            val projectDirectory = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(basePath)
            projectDirectory?.let { root ->
                searchInDirectory(root, symbolName, definitions, visitedPaths, project)
            }
        }

        return definitions
    }

    private fun searchInDirectory(
        directory: com.intellij.openapi.vfs.VirtualFile,
        symbolName: String,
        definitions: MutableList<DefinitionLocation>,
        visitedPaths: MutableSet<String>,
        project: Project
    ) {
        if (!visitedPaths.add(directory.path)) return

        directory.children.forEach { file ->
            when {
                file.isDirectory && !file.name.startsWith(".") && file.name != "build" && file.name != "out" -> {
                    searchInDirectory(file, symbolName, definitions, visitedPaths, project)
                }
                file.extension in setOf("java", "kt", "scala", "groovy") -> {
                    val psiFile = PsiManager.getInstance(project).findFile(file)
                    if (psiFile != null) {
                        searchInFile(psiFile, symbolName, definitions)
                    }
                }
            }
        }
    }

    private fun searchInFile(psiFile: PsiFile, symbolName: String, definitions: MutableList<DefinitionLocation>) {
        val uFile = psiFile.toUElementOfType<UFile>() ?: return

        uFile.classes.forEach { uClass ->
            if (uClass.name == symbolName) {
                val location = createLocationSafely(uClass)
                if (location != null) {
                    definitions.add(
                        location.copy(
                            confidence = 0.9f,
                            disambiguationHint = "Manual search result"
                        )
                    )
                }
            }

            searchInClass(uClass, symbolName, definitions)
        }
    }

    private fun searchInClass(uClass: UClass, symbolName: String, definitions: MutableList<DefinitionLocation>) {
        uClass.methods.forEach { uMethod ->
            if (uMethod.name == symbolName) {
                val location = createLocationSafely(uMethod)
                if (location != null) {
                    definitions.add(
                        location.copy(
                            confidence = 0.9f,
                            disambiguationHint = "Method in ${uClass.qualifiedName}"
                        )
                    )
                }
            }
        }

        uClass.fields.forEach { uField ->
            if (uField.name == symbolName) {
                val location = createLocationSafely(uField)
                if (location != null) {
                    definitions.add(
                        location.copy(
                            confidence = 0.9f,
                            disambiguationHint = "Field in ${uClass.qualifiedName}"
                        )
                    )
                }
            }
        }

        uClass.innerClasses.forEach { innerClass ->
            searchInClass(innerClass, symbolName, definitions)
        }
    }

    override fun findDefinitionByPosition(psiFile: PsiFile, position: Int): List<DefinitionLocation> {
        if (psiFile.virtualFile == null || position < 0 || position >= psiFile.textLength) {
            return emptyList()
        }

        try {
            val elementAtPosition = findElementAtPositionWithContext(psiFile, position)
            if (elementAtPosition != null) {
                val referenceResult = resolveReferenceAtElement(elementAtPosition)
                if (referenceResult.isNotEmpty()) return referenceResult

                val uElement = findBestUElementAtPosition(elementAtPosition)
                if (uElement != null) {
                    val uastResult = findDefinitionForUElement(uElement)
                    if (uastResult.isNotEmpty()) return uastResult
                }
            }

            return findDefinitionWithFallback(psiFile, position)

        } catch (e: Exception) {
            logger.warn("Error finding definition at position $position in ${psiFile.name}: ${e.message}")
            return emptyList()
        }
    }

    private fun resolveReferenceAtElement(element: PsiElement): List<DefinitionLocation> {
        try {
            if (element.parent is PsiReferenceExpression) {
                val referenceExpression = element.parent as PsiReferenceExpression
                val qualifier = referenceExpression.qualifierExpression

                if (qualifier != null && qualifier is PsiReferenceExpression) {
                    val qualifierResolved = qualifier.resolve()
                    if (qualifierResolved is PsiClass) {
                        val memberName = referenceExpression.referenceName
                        if (memberName != null) {
                            val definitions = mutableListOf<DefinitionLocation>()
                            findMemberInClass(qualifierResolved, memberName, definitions, true)
                            if (definitions.isNotEmpty()) {
                                return definitions
                            }
                        }
                    }
                }
            }

            val parentReference = element.parent?.reference
            if (parentReference != null) {
                val resolved = parentReference.resolve()
                if (resolved != null) {
                    val location = createLocationFromPsiElement(resolved)
                    if (location != null) {
                        return listOf(location.copy(confidence = 1.0f))
                    }
                }
            }

            val directReference = element.reference
            if (directReference != null) {
                val resolved = directReference.resolve()
                if (resolved != null) {
                    val location = createLocationFromPsiElement(resolved)
                    if (location != null) {
                        return listOf(location.copy(confidence = 1.0f))
                    }
                }
            }

        } catch (e: Exception) {
            logger.debug("Error resolving reference: ${e.message}")
        }
        return emptyList()
    }

    private fun createLocationFromPsiElement(element: PsiElement): DefinitionLocation? {
        val uElement = element.toUElement()
        return if (uElement != null) {
            createLocationSafely(uElement)
        } else {
            createPsiLocationSafely(element)
        }
    }

    private fun findElementAtPositionWithContext(psiFile: PsiFile, position: Int): PsiElement? {
        try {
            var element = psiFile.findElementAt(position)
            if (element != null && element.text.isNotBlank()) {
                return element
            }

            for (offset in 1..3) {
                if (position - offset >= 0) {
                    element = psiFile.findElementAt(position - offset)
                    if (element != null && element.text.isNotBlank()) {
                        return element
                    }
                }

                if (position + offset < psiFile.textLength) {
                    element = psiFile.findElementAt(position + offset)
                    if (element != null && element.text.isNotBlank()) {
                        return element
                    }
                }
            }

            return null
        } catch (e: Exception) {
            logger.debug("Error finding element at position: ${e.message}")
            return null
        }
    }

    private fun findBestUElementAtPosition(element: PsiElement): UElement? {
        try {
            var uElement = element.toUElement()
            if (uElement != null && isValidUElement(uElement)) {
                return uElement
            }

            var parent = element.parent
            var attempts = 0
            while (parent != null && attempts < 10) {
                uElement = parent.toUElement()
                if (uElement != null && isValidUElement(uElement)) {
                    return uElement
                }
                parent = parent.parent
                attempts++
            }

            return null
        } catch (e: Exception) {
            logger.debug("Error finding UAST element: ${e.message}")
            return null
        }
    }

    private fun findDefinitionForUElement(uElement: UElement): List<DefinitionLocation> {
        try {
            if (isValidUElement(uElement)) {
                val location = createLocationSafely(uElement)
                if (location != null) {
                    return listOf(
                        location.copy(
                            confidence = 0.9f,
                            disambiguationHint = generateDisambiguationHint(uElement),
                            accessibilityWarning = generateAccessibilityWarning(uElement)
                        )
                    )
                }
            }

            var parent = uElement.uastParent
            while (parent != null) {
                if (isValidUElement(parent)) {
                    val location = createLocationSafely(parent)
                    if (location != null) {
                        return listOf(
                            location.copy(
                                confidence = 0.8f,
                                disambiguationHint = generateDisambiguationHint(parent),
                                accessibilityWarning = generateAccessibilityWarning(parent)
                            )
                        )
                    }
                }
                parent = parent.uastParent
            }
        } catch (e: Exception) {
            logger.debug("Error finding definition for UElement: ${e.message}")
        }
        return emptyList()
    }

    private fun findDefinitionWithFallback(psiFile: PsiFile, position: Int): List<DefinitionLocation> {
        try {
            for (offset in -5..5) {
                val adjustedPosition = position + offset
                if (adjustedPosition >= 0 && adjustedPosition < psiFile.textLength) {
                    val element = psiFile.findElementAt(adjustedPosition)
                    if (element != null) {
                        val result = resolveReferenceAtElement(element)
                        if (result.isNotEmpty()) {
                            return result.map {
                                it.copy(
                                    confidence = it.confidence * 0.8f,
                                    disambiguationHint = "Near search: ${it.disambiguationHint ?: "Found nearby"}"
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug("Error in fallback search: ${e.message}")
        }
        return emptyList()
    }

    private fun isValidUElement(uElement: UElement): Boolean {
        return uElement is UClass || uElement is UMethod || uElement is UField ||
               uElement is UVariable || uElement is UEnumConstant
    }

    override fun createLocation(element: PsiElement, searchTerm: String?): DefinitionLocation {
        val uElement = element.toUElement()
        return if (uElement != null) {
            createLocationSafely(uElement, searchTerm)
                ?: createFallbackLocation(element, searchTerm)
        } else {
            createPsiLocationSafely(element, searchTerm)
                ?: createFallbackLocation(element, searchTerm)
        }
    }

    private fun createLocationSafely(uElement: UElement, searchTerm: String? = null): DefinitionLocation? {
        try {
            val sourcePsi = uElement.sourcePsi ?: uElement.javaPsi ?: return null
            val containingFile = sourcePsi.containingFile ?: return null
            val virtualFile = containingFile.virtualFile ?: return null
            val project = sourcePsi.project
            val textRange = sourcePsi.textRange ?: return null

            val basePath = project.basePath ?: ""
            val relativePath = try {
                virtualFile.path.removePrefix(basePath).removePrefix("/")
            } catch (e: Exception) {
                virtualFile.name
            }

            val lineNumber = try {
                val document = PsiDocumentManager.getInstance(project).getDocument(containingFile)
                document?.getLineNumber(textRange.startOffset)?.plus(1) ?: 1
            } catch (e: Exception) {
                1
            }

            val isTestCode = try {
                isInTestCode(virtualFile)
            } catch (e: Exception) {
                false
            }

            val isLibraryCode = try {
                isInLibraryCode(virtualFile, project)
            } catch (e: Exception) {
                false
            }

            return when (uElement) {
                is UClass -> createClassLocation(
                    uElement, relativePath, textRange, lineNumber, isTestCode, isLibraryCode
                )
                is UMethod -> createMethodLocation(
                    uElement, relativePath, textRange, lineNumber, isTestCode, isLibraryCode
                )
                is UField -> createFieldLocation(
                    uElement, relativePath, textRange, lineNumber, isTestCode, isLibraryCode
                )
                is UVariable -> createVariableLocation(
                    uElement, relativePath, textRange, lineNumber, isTestCode, isLibraryCode
                )
                is UEnumConstant -> createEnumConstantLocation(
                    uElement, relativePath, textRange, lineNumber, isTestCode, isLibraryCode
                )
                else -> createGenericLocation(
                    uElement, relativePath, textRange, lineNumber, isTestCode, isLibraryCode
                )
            }

        } catch (e: Exception) {
            logger.warn("Error creating location for UAST element: ${e.message}")
            return null
        }
    }

    private fun createFallbackLocation(element: PsiElement, searchTerm: String?): DefinitionLocation {
        val containingFile = element.containingFile
        val virtualFile = containingFile?.virtualFile
        val textRange = element.textRange

        return DefinitionLocation(
            name = (element as? PsiNamedElement)?.name ?: element.text?.take(50) ?: "unknown",
            filePath = virtualFile?.name ?: "unknown",
            startOffset = textRange?.startOffset ?: 0,
            endOffset = textRange?.endOffset ?: 0,
            lineNumber = 1,
            type = "unknown",
            signature = element.text?.lines()?.firstOrNull()?.take(100),
            isTestCode = false,
            isLibraryCode = false,
            confidence = 0.1f,
            disambiguationHint = "Fallback location"
        )
    }

    private fun createClassLocation(
        uClass: UClass,
        filePath: String,
        textRange: com.intellij.openapi.util.TextRange,
        lineNumber: Int,
        isTestCode: Boolean,
        isLibraryCode: Boolean
    ): DefinitionLocation {
        return DefinitionLocation(
            name = uClass.name ?: "anonymous",
            filePath = filePath,
            startOffset = textRange.startOffset,
            endOffset = textRange.endOffset,
            lineNumber = lineNumber,
            type = when {
                uClass.isInterface -> "interface"
                uClass.isEnum -> "enum"
                uClass.isAnnotationType -> "annotation"
                else -> "class"
            },
            signature = buildUClassSignature(uClass),
            containingClass = (uClass.uastParent as? UClass)?.qualifiedName,
            modifiers = extractUElementModifiers(uClass),
            isAbstract = uClass.hasModifierPropertySafe(PsiModifier.ABSTRACT),
            isTestCode = isTestCode,
            isLibraryCode = isLibraryCode
        )
    }

    private fun createMethodLocation(
        uMethod: UMethod,
        filePath: String,
        textRange: com.intellij.openapi.util.TextRange,
        lineNumber: Int,
        isTestCode: Boolean,
        isLibraryCode: Boolean
    ): DefinitionLocation {
        return DefinitionLocation(
            name = uMethod.name,
            filePath = filePath,
            startOffset = textRange.startOffset,
            endOffset = textRange.endOffset,
            lineNumber = lineNumber,
            type = if (uMethod.isConstructor) "constructor" else "method",
            signature = buildUMethodSignature(uMethod),
            containingClass = (uMethod.uastParent as? UClass)?.qualifiedName,
            modifiers = extractUElementModifiers(uMethod),
            isAbstract = uMethod.hasModifierPropertySafe(PsiModifier.ABSTRACT),
            isTestCode = isTestCode,
            isLibraryCode = isLibraryCode
        )
    }

    private fun createFieldLocation(
        uField: UField,
        filePath: String,
        textRange: com.intellij.openapi.util.TextRange,
        lineNumber: Int,
        isTestCode: Boolean,
        isLibraryCode: Boolean
    ): DefinitionLocation {
        return DefinitionLocation(
            name = uField.name ?: "anonymous",
            filePath = filePath,
            startOffset = textRange.startOffset,
            endOffset = textRange.endOffset,
            lineNumber = lineNumber,
            type = "field",
            signature = buildUFieldSignature(uField),
            containingClass = (uField.uastParent as? UClass)?.qualifiedName,
            modifiers = extractUElementModifiers(uField),
            isTestCode = isTestCode,
            isLibraryCode = isLibraryCode
        )
    }

    private fun createVariableLocation(
        uVariable: UVariable,
        filePath: String,
        textRange: com.intellij.openapi.util.TextRange,
        lineNumber: Int,
        isTestCode: Boolean,
        isLibraryCode: Boolean
    ): DefinitionLocation {
        return DefinitionLocation(
            name = uVariable.name ?: "anonymous",
            filePath = filePath,
            startOffset = textRange.startOffset,
            endOffset = textRange.endOffset,
            lineNumber = lineNumber,
            type = "variable",
            signature = buildUVariableSignature(uVariable),
            containingClass = findContainingClass(uVariable)?.qualifiedName,
            modifiers = extractUElementModifiers(uVariable),
            isTestCode = isTestCode,
            isLibraryCode = isLibraryCode
        )
    }

    private fun createEnumConstantLocation(
        uEnumConstant: UEnumConstant,
        filePath: String,
        textRange: com.intellij.openapi.util.TextRange,
        lineNumber: Int,
        isTestCode: Boolean,
        isLibraryCode: Boolean
    ): DefinitionLocation {
        return DefinitionLocation(
            name = uEnumConstant.name ?: "anonymous",
            filePath = filePath,
            startOffset = textRange.startOffset,
            endOffset = textRange.endOffset,
            lineNumber = lineNumber,
            type = "enum_constant",
            signature = uEnumConstant.name,
            containingClass = (uEnumConstant.uastParent as? UClass)?.qualifiedName,
            modifiers = listOf("public", "static", "final"),
            isTestCode = isTestCode,
            isLibraryCode = isLibraryCode
        )
    }

    private fun createGenericLocation(
        uElement: UElement,
        filePath: String,
        textRange: com.intellij.openapi.util.TextRange,
        lineNumber: Int,
        isTestCode: Boolean,
        isLibraryCode: Boolean
    ): DefinitionLocation {
        val elementName = (uElement.sourcePsi ?: uElement.javaPsi)?.text ?: "unknown"

        return DefinitionLocation(
            name = elementName,
            filePath = filePath,
            startOffset = textRange.startOffset,
            endOffset = textRange.endOffset,
            lineNumber = lineNumber,
            type = "unknown",
            signature = uElement.asRenderString().lines().firstOrNull()?.take(100),
            isTestCode = isTestCode,
            isLibraryCode = isLibraryCode
        )
    }

    private fun createPsiLocationSafely(element: PsiElement, searchTerm: String? = null): DefinitionLocation? {
        try {
            val containingFile = element.containingFile ?: return null
            val virtualFile = containingFile.virtualFile ?: return null
            val project = element.project
            val textRange = element.textRange ?: return null

            val basePath = project.basePath ?: ""
            val relativePath = try {
                virtualFile.path.removePrefix(basePath).removePrefix("/")
            } catch (e: Exception) {
                virtualFile.name
            }

            val lineNumber = try {
                val document = PsiDocumentManager.getInstance(project).getDocument(containingFile)
                document?.getLineNumber(textRange.startOffset)?.plus(1) ?: 1
            } catch (e: Exception) {
                1
            }

            return DefinitionLocation(
                name = (element as? PsiNamedElement)?.name ?: "unknown",
                filePath = relativePath,
                startOffset = textRange.startOffset,
                endOffset = textRange.endOffset,
                lineNumber = lineNumber,
                type = when (element) {
                    is PsiClass -> when {
                        element.isInterface -> "interface"
                        element.isEnum -> "enum"
                        element.isAnnotationType -> "annotation"
                        else -> "class"
                    }
                    is PsiMethod -> if (element.isConstructor) "constructor" else "method"
                    is PsiField -> "field"
                    is PsiEnumConstant -> "enum_constant"
                    is PsiVariable -> "variable"
                    else -> "unknown"
                },
                signature = element.text?.lines()?.firstOrNull()?.take(100),
                isTestCode = false,
                isLibraryCode = false
            )

        } catch (e: Exception) {
            logger.warn("Error creating PSI location: ${e.message}")
            return null
        }
    }

    private fun generateAccessibilityWarning(uElement: UElement): String? {
        return try {
            val javaPsi = uElement.javaPsi
            if (javaPsi !is PsiModifierListOwner) return null

            val modifierList = javaPsi.modifierList ?: return null

            when {
                modifierList.hasModifierProperty(PsiModifier.PRIVATE) ->
                    "Private member - not accessible from outside the declaring class"

                modifierList.hasModifierProperty(PsiModifier.PROTECTED) ->
                    "Protected member - only accessible from subclasses or same package"

                !modifierList.hasModifierProperty(PsiModifier.PUBLIC) &&
                        !modifierList.hasModifierProperty(PsiModifier.PROTECTED) &&
                        !modifierList.hasModifierProperty(PsiModifier.PRIVATE) ->
                    "Package-private member - only accessible from the same package"

                uElement.sourcePsi?.text?.contains("internal") == true ->
                    "Internal member - only accessible from the same module"

                else -> null
            }
        } catch (e: Exception) {
            logger.debug("Error generating accessibility warning: ${e.message}")
            null
        }
    }

    private fun generateDisambiguationHint(uElement: UElement): String? {
        return try {
            when (uElement) {
                is UMethod -> {
                    val containingClass = uElement.uastParent as? UClass
                    val containingClassName = containingClass?.qualifiedName ?: containingClass?.name ?: "Unknown"
                    when {
                        uElement.isConstructor -> "Constructor in $containingClassName"
                        uElement.hasModifierPropertySafe(PsiModifier.STATIC) -> "Static method in $containingClassName"
                        uElement.hasModifierPropertySafe(PsiModifier.ABSTRACT) -> "Abstract method in $containingClassName"
                        uElement.sourcePsi?.text?.contains("suspend") == true -> "Suspend function in $containingClassName"
                        uElement.sourcePsi?.text?.contains("inline") == true -> "Inline function in $containingClassName"
                        else -> "Method in $containingClassName"
                    }
                }

                is UField -> {
                    val containingClass = uElement.uastParent as? UClass
                    val containingClassName = containingClass?.qualifiedName ?: containingClass?.name ?: "Unknown"
                    when {
                        uElement.hasModifierPropertySafe(PsiModifier.STATIC) &&
                                uElement.hasModifierPropertySafe(PsiModifier.FINAL) -> "Constant in $containingClassName"
                        uElement.hasModifierPropertySafe(PsiModifier.STATIC) -> "Static field in $containingClassName"
                        uElement.sourcePsi?.text?.contains("val") == true -> "Immutable property in $containingClassName"
                        uElement.sourcePsi?.text?.contains("var") == true -> "Mutable property in $containingClassName"
                        else -> "Field in $containingClassName"
                    }
                }

                is UClass -> {
                    val containingFile = uElement.sourcePsi?.containingFile
                    val packageName = (containingFile as? PsiJavaFile)?.packageName
                    val locationDesc = packageName ?: "default package"

                    val languageId = uElement.sourcePsi?.language?.id?.lowercase()
                    val languagePrefix = when (languageId) {
                        "kotlin" -> "Kotlin "
                        "scala" -> "Scala "
                        "groovy" -> "Groovy "
                        else -> ""
                    }

                    when {
                        uElement.isInterface -> "${languagePrefix}Interface in $locationDesc"
                        uElement.isEnum -> "${languagePrefix}Enum in $locationDesc"
                        uElement.isAnnotationType -> "${languagePrefix}Annotation in $locationDesc"
                        uElement.hasModifierPropertySafe(PsiModifier.ABSTRACT) -> "${languagePrefix}Abstract class in $locationDesc"
                        uElement.sourcePsi?.text?.contains("data class") == true -> "Kotlin Data class in $locationDesc"
                        uElement.sourcePsi?.text?.contains("sealed class") == true -> "Kotlin Sealed class in $locationDesc"
                        uElement.sourcePsi?.text?.contains("object ") == true -> "Kotlin Object in $locationDesc"
                        else -> "${languagePrefix}Class in $locationDesc"
                    }
                }

                is UVariable -> {
                    val javaPsi = uElement.javaPsi
                    when (javaPsi) {
                        is PsiParameter -> "Parameter"
                        is PsiLocalVariable -> "Local variable"
                        else -> "Variable"
                    }
                }

                else -> null
            }
        } catch (e: Exception) {
            logger.debug("Error generating disambiguation hint: ${e.message}")
            null
        }
    }

    private fun calculateConfidence(uElement: UElement, searchTerm: String, isClass: Boolean): Float {
        return try {
            val elementName = (uElement.sourcePsi ?: uElement.javaPsi)?.text ?: return 0.1f

            val sourcePsi = uElement.sourcePsi ?: uElement.javaPsi ?: return 0.1f
            val containingFile = sourcePsi.containingFile ?: return 0.1f
            val virtualFile = containingFile.virtualFile ?: return 0.1f
            val project = sourcePsi.project

            val isInLibrary = try {
                isInLibraryCode(virtualFile, project)
            } catch (e: Exception) {
                false
            }

            when {
                elementName == searchTerm && !isInLibrary -> {
                    when {
                        isClass && uElement is UClass -> 1.0f
                        !isClass && (uElement is UMethod || uElement is UField) -> 0.95f
                        else -> 0.9f
                    }
                }
                elementName == searchTerm && isInLibrary -> 0.5f
                elementName.equals(searchTerm, ignoreCase = true) -> 0.7f
                elementName.contains(searchTerm, ignoreCase = true) -> 0.3f
                else -> 0.1f
            }
        } catch (e: Exception) {
            0.1f
        }
    }

    private fun UDeclaration.hasModifierPropertySafe(modifier: String): Boolean {
        return try {
            val javaPsi = this.javaPsi
            (javaPsi as? PsiModifierListOwner)?.hasModifierProperty(modifier) ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun extractUElementModifiers(uElement: UElement): List<String> {
        return try {
            val javaPsi = uElement.javaPsi
            if (javaPsi is PsiModifierListOwner) {
                super.extractModifiers(javaPsi.modifierList).toList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun buildUClassSignature(uClass: UClass): String? {
        return try {
            val name = uClass.name ?: "anonymous"
            val type = when {
                uClass.isInterface -> "interface"
                uClass.isEnum -> "enum"
                uClass.isAnnotationType -> "@interface"
                else -> "class"
            }
            "$type $name"
        } catch (e: Exception) {
            null
        }
    }

    private fun buildUMethodSignature(uMethod: UMethod): String? {
        return try {
            val name = uMethod.name
            val params = uMethod.uastParameters.joinToString(", ") { param ->
                "${param.type?.presentableText ?: "?"} ${param.name ?: "?"}"
            }
            val returnType = if (uMethod.isConstructor) "" else "${uMethod.returnType?.presentableText ?: "void"} "
            "$returnType$name($params)"
        } catch (e: Exception) {
            uMethod.name
        }
    }

    private fun buildUFieldSignature(uField: UField): String? {
        return try {
            val type = uField.type?.presentableText ?: "?"
            val name = uField.name ?: "?"
            "$type $name"
        } catch (e: Exception) {
            uField.name
        }
    }

    private fun buildUVariableSignature(uVariable: UVariable): String? {
        return try {
            val type = uVariable.type?.presentableText ?: "?"
            val name = uVariable.name ?: "?"
            "$type $name"
        } catch (e: Exception) {
            uVariable.name
        }
    }

    private fun findContainingClass(uElement: UElement): UClass? {
        var parent = uElement.uastParent
        while (parent != null) {
            if (parent is UClass) return parent
            parent = parent.uastParent
        }
        return null
    }

    override fun supportsElement(element: PsiElement): Boolean {
        return LanguageUtils.isJvmLanguage(element.language)
    }

    override fun supportsFile(psiFile: PsiFile): Boolean {
        return LanguageUtils.isJvmLanguage(psiFile.language)
    }

    override fun getSupportedLanguage(): String {
        return "Java/Kotlin"
    }
}
