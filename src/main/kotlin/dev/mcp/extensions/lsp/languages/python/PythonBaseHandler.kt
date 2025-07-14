package dev.mcp.extensions.lsp.languages.python

import dev.mcp.extensions.lsp.languages.base.BaseLanguageHandler

/**
 * Base class for Python language handlers with Python-specific utilities.
 * Do not add any utilities that depend on python modules or libraries here.
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
}
