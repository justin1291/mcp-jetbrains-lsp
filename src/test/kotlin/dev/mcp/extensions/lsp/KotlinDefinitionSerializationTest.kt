package dev.mcp.extensions.lsp.test

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import dev.mcp.extensions.lsp.languages.jvm.JvmDefinitionFinder

class KotlinDefinitionSerializationTest : LightJavaCodeInsightFixtureTestCase() {
    
    fun testKotlinDefinitionSerializationErrors() {
        val definitionFinder = JvmDefinitionFinder()
        
        val kotlinCode = """
            package com.example
            
            data class User(
                val id: Long,
                val name: String
            ) {
                fun greet() = "Hello, ${'$'}name"
                
                companion object {
                    fun create(name: String) = User(0, name)
                }
            }
            
            class UserService {
                fun processUser(user: User) {
                    println(user.greet())
                }
            }
        """.trimIndent()
        
        val psiFile = myFixture.configureByText("User.kt", kotlinCode)
        
        println("=== TESTING KOTLIN DEFINITION SERIALIZATION ===")
        
        try {
            // Test finding definition by position (e.g., click on 'User' in processUser method)
            val userPosition = kotlinCode.indexOf("user: User") + "user: ".length + 2 // Position on 'User'
            println("Testing position-based definition finding at position: $userPosition")
            
            val definitions = definitionFinder.findDefinitionByPosition(psiFile, userPosition)
            println("Found ${definitions.size} definitions")
            
            definitions.forEach { def ->
                println("Definition: ${def.name} (${def.type}) at ${def.filePath}:${def.lineNumber}")
                println("  Signature: ${def.signature}")
                println("  Containing class: ${def.containingClass}")
                println("  Modifiers: ${def.modifiers}")
            }
            
        } catch (e: Exception) {
            println("❌ SERIALIZATION ERROR in position-based finding:")
            println("  ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
        }
        
        try {
            // Test finding definition by name
            println("\nTesting name-based definition finding for 'User'")
            val project = psiFile.project
            val definitions = definitionFinder.findDefinitionByName(project, "User")
            println("Found ${definitions.size} definitions")
            
            definitions.forEach { def ->
                println("Definition: ${def.name} (${def.type}) at ${def.filePath}:${def.lineNumber}")
            }
            
        } catch (e: Exception) {
            println("❌ SERIALIZATION ERROR in name-based finding:")
            println("  ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
        }
        
        try {
            // Test finding Kotlin-specific elements
            val greetPosition = kotlinCode.indexOf("greet()") + 1  // Position on 'greet'
            println("\nTesting Kotlin method definition finding at position: $greetPosition")
            
            val definitions = definitionFinder.findDefinitionByPosition(psiFile, greetPosition)
            println("Found ${definitions.size} definitions for Kotlin method")
            
        } catch (e: Exception) {
            println("❌ SERIALIZATION ERROR in Kotlin method finding:")
            println("  ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
        }
        
        println("\n=== TEST COMPLETED ===")
    }
}
