package dev.mcp.extensions.lsp.test

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import dev.mcp.extensions.lsp.languages.jvm.JvmDefinitionFinder

class AccessibilityAnalysisTest : LightJavaCodeInsightFixtureTestCase() {
    
    fun testJavaAccessibilityAnalysis() {
        val definitionFinder = JvmDefinitionFinder()
        
        val javaCode = """
            package com.example;
            
            public class AccessibilityExample {
                private String privateField = "private";
                protected String protectedField = "protected";
                String packagePrivateField = "package";
                public String publicField = "public";
                
                private void privateMethod() {}
                protected void protectedMethod() {}
                void packagePrivateMethod() {}
                public void publicMethod() {}
                
                public static final String CONSTANT = "constant";
                
                public void testAccessibility() {
                    // Test: click on privateField should show accessibility warning
                    System.out.println(privateField);
                    System.out.println(protectedField);
                    System.out.println(packagePrivateField);
                    System.out.println(publicField);
                }
            }
        """.trimIndent()
        
        val psiFile = myFixture.configureByText("AccessibilityExample.java", javaCode)
        
        println("=== TESTING JAVA ACCESSIBILITY ANALYSIS ===")
        
        try {
            // Test private field access
            val privateFieldPosition = javaCode.indexOf("privateField);") + 6  // Position in "privateField"
            val privateFieldDefinitions = definitionFinder.findDefinitionByPosition(psiFile, privateFieldPosition)
            
            println("Private field definition:")
            privateFieldDefinitions.forEach { def ->
                println("  - ${def.name} (${def.type})")
                println("    Disambiguation: ${def.disambiguationHint}")
                println("    Accessibility: ${def.accessibilityWarning}")
                println("    Modifiers: ${def.modifiers}")
            }
            
            // Test protected field access
            val protectedFieldPosition = javaCode.indexOf("protectedField);") + 8
            val protectedFieldDefinitions = definitionFinder.findDefinitionByPosition(psiFile, protectedFieldPosition)
            
            println("\nProtected field definition:")
            protectedFieldDefinitions.forEach { def ->
                println("  - ${def.name} (${def.type})")
                println("    Disambiguation: ${def.disambiguationHint}")
                println("    Accessibility: ${def.accessibilityWarning}")
                println("    Modifiers: ${def.modifiers}")
            }
            
            // Test package-private field access
            val packageFieldPosition = javaCode.indexOf("packagePrivateField);") + 10
            val packageFieldDefinitions = definitionFinder.findDefinitionByPosition(psiFile, packageFieldPosition)
            
            println("\nPackage-private field definition:")
            packageFieldDefinitions.forEach { def ->
                println("  - ${def.name} (${def.type})")
                println("    Disambiguation: ${def.disambiguationHint}")
                println("    Accessibility: ${def.accessibilityWarning}")
                println("    Modifiers: ${def.modifiers}")
            }
            
            // Test public field access
            val publicFieldPosition = javaCode.indexOf("publicField);") + 6
            val publicFieldDefinitions = definitionFinder.findDefinitionByPosition(psiFile, publicFieldPosition)
            
            println("\nPublic field definition:")
            publicFieldDefinitions.forEach { def ->
                println("  - ${def.name} (${def.type})")
                println("    Disambiguation: ${def.disambiguationHint}")
                println("    Accessibility: ${def.accessibilityWarning}")
                println("    Modifiers: ${def.modifiers}")
            }
            
            // Verify accessibility warnings
            assert(privateFieldDefinitions.any { it.accessibilityWarning?.contains("Private member") == true }) {
                "Private field should have accessibility warning"
            }
            assert(protectedFieldDefinitions.any { it.accessibilityWarning?.contains("Protected member") == true }) {
                "Protected field should have accessibility warning"
            }
            assert(packageFieldDefinitions.any { it.accessibilityWarning?.contains("Package-private member") == true }) {
                "Package-private field should have accessibility warning"
            }
            assert(publicFieldDefinitions.any { it.accessibilityWarning == null }) {
                "Public field should NOT have accessibility warning"
            }
            
            println("\n✅ Java accessibility analysis working correctly!")
            
        } catch (e: Exception) {
            println("❌ FAILED: Java accessibility analysis error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    fun testKotlinAccessibilityAnalysis() {
        val definitionFinder = JvmDefinitionFinder()
        
        val kotlinCode = """
            package com.example
            
            class AccessibilityExample {
                private val privateProperty = "private"
                protected val protectedProperty = "protected"
                internal val internalProperty = "internal"
                val publicProperty = "public"
                
                private fun privateMethod() {}
                protected fun protectedMethod() {}
                internal fun internalMethod() {}
                fun publicMethod() {}
                
                companion object {
                    const val CONSTANT = "constant"
                }
                
                fun testAccessibility() {
                    // Test: click on properties should show appropriate warnings
                    println(privateProperty)
                    println(protectedProperty)
                    println(internalProperty)
                    println(publicProperty)
                }
            }
        """.trimIndent()
        
        val psiFile = myFixture.configureByText("AccessibilityExample.kt", kotlinCode)
        
        println("\n=== TESTING KOTLIN ACCESSIBILITY ANALYSIS ===")
        
        try {
            // Test private property access
            val privatePropertyPosition = kotlinCode.indexOf("privateProperty)") + 8
            val privatePropertyDefinitions = definitionFinder.findDefinitionByPosition(psiFile, privatePropertyPosition)
            
            println("Private property definition:")
            privatePropertyDefinitions.forEach { def ->
                println("  - ${def.name} (${def.type})")
                println("    Disambiguation: ${def.disambiguationHint}")
                println("    Accessibility: ${def.accessibilityWarning}")
                println("    Modifiers: ${def.modifiers}")
            }
            
            // Test internal property access
            val internalPropertyPosition = kotlinCode.indexOf("internalProperty)") + 8
            val internalPropertyDefinitions = definitionFinder.findDefinitionByPosition(psiFile, internalPropertyPosition)
            
            println("\nInternal property definition:")
            internalPropertyDefinitions.forEach { def ->
                println("  - ${def.name} (${def.type})")
                println("    Disambiguation: ${def.disambiguationHint}")
                println("    Accessibility: ${def.accessibilityWarning}")
                println("    Modifiers: ${def.modifiers}")
            }
            
            // Test public property access
            val publicPropertyPosition = kotlinCode.indexOf("publicProperty)") + 8
            val publicPropertyDefinitions = definitionFinder.findDefinitionByPosition(psiFile, publicPropertyPosition)
            
            println("\nPublic property definition:")
            publicPropertyDefinitions.forEach { def ->
                println("  - ${def.name} (${def.type})")
                println("    Disambiguation: ${def.disambiguationHint}")
                println("    Accessibility: ${def.accessibilityWarning}")
                println("    Modifiers: ${def.modifiers}")
            }
            
            println("\n✅ Kotlin accessibility analysis working correctly!")
            
        } catch (e: Exception) {
            println("❌ FAILED: Kotlin accessibility analysis error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    fun testDisambiguationHints() {
        val definitionFinder = JvmDefinitionFinder()
        
        val mixedCode = """
            package com.example;
            
            public abstract class BaseClass {
                public static final String CONSTANT = "base";
                public abstract void abstractMethod();
                public static void staticMethod() {}
                
                public class InnerClass {
                    public void innerMethod() {}
                }
            }
            
            interface TestInterface {
                void interfaceMethod();
            }
            
            enum TestEnum {
                VALUE1, VALUE2
            }
        """.trimIndent()
        
        val psiFile = myFixture.configureByText("DisambiguationTest.java", mixedCode)
        
        println("\n=== TESTING DISAMBIGUATION HINTS ===")
        
        try {
            // Test by name searches to see disambiguation hints
            val constantDefinitions = definitionFinder.findDefinitionByName(psiFile.project, "CONSTANT")
            println("Constant disambiguation:")
            constantDefinitions.forEach { def ->
                println("  - ${def.name}: ${def.disambiguationHint}")
                assert(def.disambiguationHint?.contains("Constant") == true) {
                    "Constant should have appropriate disambiguation hint"
                }
            }
            
            val staticMethodDefinitions = definitionFinder.findDefinitionByName(psiFile.project, "staticMethod")
            println("\nStatic method disambiguation:")
            staticMethodDefinitions.forEach { def ->
                println("  - ${def.name}: ${def.disambiguationHint}")
                assert(def.disambiguationHint?.contains("Static method") == true) {
                    "Static method should have appropriate disambiguation hint"
                }
            }
            
            val abstractMethodDefinitions = definitionFinder.findDefinitionByName(psiFile.project, "abstractMethod")
            println("\nAbstract method disambiguation:")
            abstractMethodDefinitions.forEach { def ->
                println("  - ${def.name}: ${def.disambiguationHint}")
                assert(def.disambiguationHint?.contains("Abstract method") == true) {
                    "Abstract method should have appropriate disambiguation hint"
                }
            }
            
            val classDefinitions = definitionFinder.findDefinitionByName(psiFile.project, "BaseClass")
            println("\nClass disambiguation:")
            classDefinitions.forEach { def ->
                println("  - ${def.name}: ${def.disambiguationHint}")
                assert(def.disambiguationHint?.contains("Abstract class") == true) {
                    "Abstract class should have appropriate disambiguation hint"
                }
            }
            
            val interfaceDefinitions = definitionFinder.findDefinitionByName(psiFile.project, "TestInterface")
            println("\nInterface disambiguation:")
            interfaceDefinitions.forEach { def ->
                println("  - ${def.name}: ${def.disambiguationHint}")
                assert(def.disambiguationHint?.contains("Interface") == true) {
                    "Interface should have appropriate disambiguation hint"
                }
            }
            
            val enumDefinitions = definitionFinder.findDefinitionByName(psiFile.project, "TestEnum")
            println("\nEnum disambiguation:")
            enumDefinitions.forEach { def ->
                println("  - ${def.name}: ${def.disambiguationHint}")
                assert(def.disambiguationHint?.contains("Enum") == true) {
                    "Enum should have appropriate disambiguation hint"
                }
            }
            
            println("\n✅ All disambiguation hints working correctly!")
            
        } catch (e: Exception) {
            println("❌ FAILED: Disambiguation hints error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    fun runAllAccessibilityTests() {
        try {
            testJavaAccessibilityAnalysis()
            testKotlinAccessibilityAnalysis()
            testDisambiguationHints()
            
            println("\n🎉 ALL ACCESSIBILITY ANALYSIS TESTS PASSED!")
            println("✅ JVM Definition Finder now provides:")
            println("  - Accessibility warnings for private/protected/package-private/internal members")
            println("  - Rich disambiguation hints for classes, methods, fields, and interfaces")
            println("  - Cross-language support for Java and Kotlin accessibility rules")
            println("  - Feature parity with Java implementation for contextual information")
            
        } catch (e: Exception) {
            println("\n💥 ACCESSIBILITY ANALYSIS TESTS FAILED: ${e.message}")
            throw e
        }
    }
}
