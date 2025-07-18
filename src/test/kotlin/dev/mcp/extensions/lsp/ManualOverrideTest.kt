package dev.mcp.extensions.lsp.test

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import dev.mcp.extensions.lsp.core.models.GetSymbolsArgs
import dev.mcp.extensions.lsp.languages.jvm.JvmSymbolExtractor

class ManualOverrideTest : LightJavaCodeInsightFixtureTestCase() {
    
    fun testOverrideDetectionManual() {
        val symbolExtractor = JvmSymbolExtractor()
        
        val javaCode = """
            public class TestOverride {
                @Override
                public String toString() {
                    return "test";
                }
                
                @Override
                public boolean equals(Object obj) {
                    return super.equals(obj);
                }
                
                @Override
                public int hashCode() {
                    return super.hashCode();
                }
                
                public void customMethod() {
                    // This should NOT have override info
                }
            }
        """.trimIndent()
        
        val psiFile = myFixture.configureByText("TestOverride.java", javaCode)
        val args = GetSymbolsArgs(
            filePath = "TestOverride.java",
            hierarchical = false
        )
        
        val symbols = symbolExtractor.extractSymbolsFlat(psiFile, args)
        
        // Find methods
        val toStringMethod = symbols.find { it.name == "toString" && it.kind.value == "method" }
        val equalsMethod = symbols.find { it.name == "equals" && it.kind.value == "method" }
        val hashCodeMethod = symbols.find { it.name == "hashCode" && it.kind.value == "method" }
        val customMethod = symbols.find { it.name == "customMethod" && it.kind.value == "method" }
        
        println("=== OVERRIDE DETECTION TEST RESULTS ===")
        
        toStringMethod?.let {
            println("toString: overrides = ${it.overrides?.parentClass}.${it.overrides?.methodName} (explicit: ${it.overrides?.isExplicit})")
        } ?: println("toString: NOT FOUND")
        
        equalsMethod?.let {
            println("equals: overrides = ${it.overrides?.parentClass}.${it.overrides?.methodName} (explicit: ${it.overrides?.isExplicit})")
        } ?: println("equals: NOT FOUND")
        
        hashCodeMethod?.let {
            println("hashCode: overrides = ${it.overrides?.parentClass}.${it.overrides?.methodName} (explicit: ${it.overrides?.isExplicit})")
        } ?: println("hashCode: NOT FOUND")
        
        customMethod?.let {
            println("customMethod: overrides = ${it.overrides?.parentClass}.${it.overrides?.methodName} (should be null)")
        } ?: println("customMethod: NOT FOUND")
        
        // Verify the fix
        val expectedResults = mapOf(
            "toString" to "toString",
            "equals" to "equals", 
            "hashCode" to "hashCode"
        )
        
        var allCorrect = true
        expectedResults.forEach { (methodName, expectedOverride) ->
            val method = symbols.find { it.name == methodName && it.kind.value == "method" }
            if (method?.overrides?.methodName != expectedOverride) {
                println("❌ FAILED: $methodName should override $expectedOverride but got ${method?.overrides?.methodName}")
                allCorrect = false
            } else {
                println("✅ PASSED: $methodName correctly overrides $expectedOverride")
            }
        }
        
        if (customMethod?.overrides != null) {
            println("❌ FAILED: customMethod should NOT have override info but got ${customMethod.overrides}")
            allCorrect = false
        } else {
            println("✅ PASSED: customMethod correctly has no override info")
        }
        
        if (allCorrect) {
            println("\n🎉 ALL TESTS PASSED! Override detection is working correctly.")
        } else {
            println("\n💥 SOME TESTS FAILED! Override detection needs more work.")
        }
    }
}
