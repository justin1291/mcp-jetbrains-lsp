package dev.mcp.extensions.lsp.languages.python

import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.*
import dev.mcp.extensions.lsp.languages.base.BaseLanguageHandler

/**
 * Base class for Python language handlers with Python-specific utilities.
 * Provides common functionality for working with Python PSI elements.
 */
abstract class PythonBaseHandler : BaseLanguageHandler() {
    
    /**
     * Get Python visibility based on naming conventions.
     * Python uses naming conventions rather than keywords:
     * - __name (double underscore prefix): private
     * - _name (single underscore prefix): protected/internal
     * - name (no prefix): public
     */
    protected fun getPythonVisibility(name: String?): String {
        return when {
            name == null -> "unknown"
            name.startsWith("__") && !name.endsWith("__") -> "private"
            name.startsWith("_") && !name.startsWith("__") -> "protected"
            else -> "public"
        }
    }
    
    /**
     * Check if a variable name suggests it's a constant (UPPER_CASE convention).
     */
    protected fun isConstant(name: String): Boolean {
        return name.matches(Regex("[A-Z][A-Z0-9_]*"))
    }
    
    /**
     * Extract decorators from a decoratable element.
     */
    protected fun extractDecorators(decorated: PyDecoratable): List<String> {
        return decorated.decoratorList?.decorators?.map { decorator ->
            "@${decorator.name ?: decorator.text}"
        } ?: emptyList()
    }
    
    /**
     * Check if an element is async.
     */
    protected fun isAsync(element: PsiElement): Boolean {
        return when (element) {
            is PyFunction -> element.isAsync
            else -> false
        }
    }
    
    /**
     * Extract type hint from a typed element.
     */
    protected fun extractTypeHint(typedElement: PyTypedElement): String? {
        // TODO: Implement when Python PSI API provides type annotation support
        return null
    }
    
    /**
     * Extract return type hint from a function.
     */
    protected fun extractReturnTypeHint(function: PyFunction): String? {
        return function.annotation?.value?.text
    }
    
    /**
     * Check if a function is a property (has @property decorator).
     */
    protected fun isProperty(function: PyFunction): Boolean {
        return extractDecorators(function).any { it == "@property" }
    }
    
    /**
     * Get the type of method (static, class, instance, property).
     */
    protected fun getMethodType(function: PyFunction): String {
        val decorators = extractDecorators(function)
        return when {
            decorators.contains("@staticmethod") -> "static"
            decorators.contains("@classmethod") -> "class"
            decorators.contains("@property") -> "property"
            else -> "instance"
        }
    }
    
    /**
     * Check if a class is a dataclass.
     */
    protected fun isDataclass(pyClass: PyClass): Boolean {
        return extractDecorators(pyClass).any { 
            it.contains("dataclass", ignoreCase = true) 
        }
    }
    
    /**
     * Extract docstring from an element.
     */
    protected fun extractDocstring(element: PyDocStringOwner): String? {
        return element.docStringValue
    }
    
    /**
     * Check if element has a docstring.
     */
    protected fun hasDocstring(element: PsiElement): Boolean {
        return when (element) {
            is PyDocStringOwner -> element.docStringValue != null
            else -> false
        }
    }
    
    /**
     * Check if a variable is relevant (not a loop variable, etc.).
     */
    protected fun isRelevantVariable(target: PyTargetExpression): Boolean {
        val parent = target.parent
        return parent !is PyForPart &&
               parent !is PyComprehensionElement &&
               parent !is PyWithItem
    }
    
    /**
     * Get Python-specific modifiers for an element.
     */
    protected fun getPythonModifiers(element: PsiElement): List<String> {
        val modifiers = mutableListOf<String>()
        
        when (element) {
            is PyFunction -> {
                if (element.isAsync) modifiers.add("async")
                if (element.isGenerator) modifiers.add("generator")
                // Check if function has @abstractmethod decorator
                val decorators = extractDecorators(element)
                if (decorators.contains("@abstractmethod")) modifiers.add("abstract")
                if (decorators.contains("@staticmethod")) modifiers.add("static")
                if (decorators.contains("@classmethod")) modifiers.add("class")
                if (decorators.contains("@property")) modifiers.add("property")
            }
            is PyClass -> {
                // Check if class has ABC base or abstractmethod decorators
                if (hasAbstractMethods(element)) modifiers.add("abstract")
                if (isDataclass(element)) modifiers.add("dataclass")
            }
        }
        
        return modifiers
    }
    
    /**
     * Check if a class has abstract methods (making it abstract).
     */
    protected fun hasAbstractMethods(pyClass: PyClass): Boolean {
        // Check if inherits from ABC
        val inheritsFromABC = pyClass.superClassExpressions.any { expr ->
            expr.text.contains("ABC") || expr.text.contains("ABCMeta")
        }
        
        // Check if has any methods with @abstractmethod
        val hasAbstractMethod = pyClass.methods.any { method ->
            extractDecorators(method).contains("@abstractmethod")
        }
        
        return inheritsFromABC || hasAbstractMethod
    }
}
