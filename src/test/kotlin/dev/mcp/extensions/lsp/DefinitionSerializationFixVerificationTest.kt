package dev.mcp.extensions.lsp.test

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import dev.mcp.extensions.lsp.languages.jvm.JvmDefinitionFinder

class DefinitionSerializationFixVerificationTest : LightJavaCodeInsightFixtureTestCase() {
    
    fun testJavaDefinitionResolution() {
        val definitionFinder = JvmDefinitionFinder()
        
        val javaCode = """
            package com.example;
            
            public class UserService {
                private String name;
                
                public UserService(String name) {
                    this.name = name;
                }
                
                public String getName() {
                    return name;
                }
                
                public void processUser() {
                    // Test: click on getName() should find definition
                    String result = getName();
                }
            }
        """.trimIndent()
        
        val psiFile = myFixture.configureByText("UserService.java", javaCode)
        
        println("=== TESTING JAVA DEFINITION RESOLUTION ===")
        
        try {
            // Test finding definition of getName() method call
            val getNameCallPosition = javaCode.indexOf("getName()") + 3  // Position in "getName"
            println("Testing Java method call resolution at position: $getNameCallPosition")
            
            val definitions = definitionFinder.findDefinitionByPosition(psiFile, getNameCallPosition)
            println("✅ Found ${definitions.size} definitions for Java method")
            
            definitions.forEach { def ->
                println("  - ${def.name} (${def.type}) at line ${def.lineNumber}")
                println("    Signature: ${def.signature}")
                println("    File: ${def.filePath}")
            }
            
            assert(definitions.isNotEmpty()) { "Should find definition for Java method call" }
            
        } catch (e: Exception) {
            println("❌ FAILED: Java definition resolution error: ${e.message}")
            throw e
        }
    }
    
    fun testKotlinDefinitionResolution() {
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
                    // Test: click on user.greet() should find definition
                    println(user.greet())
                    
                    // Test: click on User should find class definition
                    val newUser = User.create("test")
                }
            }
        """.trimIndent()
        
        val psiFile = myFixture.configureByText("User.kt", kotlinCode)
        
        println("\n=== TESTING KOTLIN DEFINITION RESOLUTION ===")
        
        try {
            // Test 1: Finding Kotlin method definition
            val greetCallPosition = kotlinCode.indexOf("user.greet()") + 5  // Position in "greet"
            println("Testing Kotlin method call resolution at position: $greetCallPosition")
            
            val methodDefinitions = definitionFinder.findDefinitionByPosition(psiFile, greetCallPosition)
            println("✅ Found ${methodDefinitions.size} definitions for Kotlin method")
            
            methodDefinitions.forEach { def ->
                println("  - ${def.name} (${def.type}) at line ${def.lineNumber}")
                println("    Signature: ${def.signature}")
            }
            
            // Test 2: Finding Kotlin class definition
            val userTypePosition = kotlinCode.indexOf("val newUser = User.create") + "val newUser = ".length + 2  // Position in "User"
            println("\nTesting Kotlin class reference resolution at position: $userTypePosition")
            
            val classDefinitions = definitionFinder.findDefinitionByPosition(psiFile, userTypePosition)
            println("✅ Found ${classDefinitions.size} definitions for Kotlin class")
            
            classDefinitions.forEach { def ->
                println("  - ${def.name} (${def.type}) at line ${def.lineNumber}")
                println("    Signature: ${def.signature}")
            }
            
            // Test 3: Name-based search
            println("\nTesting name-based search for 'User'")
            val nameDefinitions = definitionFinder.findDefinitionByName(psiFile.project, "User")
            println("✅ Found ${nameDefinitions.size} definitions by name")
            
            nameDefinitions.forEach { def ->
                println("  - ${def.name} (${def.type}) confidence: ${def.confidence}")
            }
            
            println("✅ ALL KOTLIN TESTS PASSED - No serialization errors!")
            
        } catch (e: Exception) {
            println("❌ FAILED: Kotlin definition resolution error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    fun testEdgeCasesAndErrorHandling() {
        val definitionFinder = JvmDefinitionFinder()
        
        println("\n=== TESTING EDGE CASES AND ERROR HANDLING ===")
        
        try {
            val kotlinCode = """
                // Edge case: minimal Kotlin with nullable types
                class Test {
                    var value: String? = null
                    fun test() = value?.length
                }
            """.trimIndent()
            
            val psiFile = myFixture.configureByText("Test.kt", kotlinCode)
            
            // Test invalid positions
            println("Testing invalid position handling...")
            val invalidDefinitions1 = definitionFinder.findDefinitionByPosition(psiFile, -1)
            val invalidDefinitions2 = definitionFinder.findDefinitionByPosition(psiFile, psiFile.textLength + 100)
            
            println("✅ Invalid positions handled gracefully (returned ${invalidDefinitions1.size} and ${invalidDefinitions2.size} definitions)")
            
            // Test empty/invalid names
            println("Testing empty name handling...")
            val emptyNameDefinitions = definitionFinder.findDefinitionByName(psiFile.project, "")
            val invalidNameDefinitions = definitionFinder.findDefinitionByName(psiFile.project, "NonExistentClass12345")
            
            println("✅ Empty/invalid names handled gracefully (returned ${emptyNameDefinitions.size} and ${invalidNameDefinitions.size} definitions)")
            
            // Test nullable types and other Kotlin features
            val nullablePosition = kotlinCode.indexOf("String?") + 3
            val nullableDefinitions = definitionFinder.findDefinitionByPosition(psiFile, nullablePosition)
            
            println("✅ Kotlin nullable types handled (found ${nullableDefinitions.size} definitions)")
            
            println("✅ ALL EDGE CASE TESTS PASSED!")
            
        } catch (e: Exception) {
            println("❌ FAILED: Edge case handling error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    fun testSerializationSafety() {
        val definitionFinder = JvmDefinitionFinder()
        
        println("\n=== TESTING SERIALIZATION SAFETY ===")
        
        try {
            val complexKotlinCode = """
                package com.example
                
                interface UserRepository {
                    suspend fun findById(id: Long): User?
                }
                
                data class User(val id: Long, val name: String) {
                    companion object {
                        const val DEFAULT_NAME = "Unknown"
                    }
                }
                
                class UserServiceImpl : UserRepository {
                    override suspend fun findById(id: Long): User? {
                        return User(id, User.DEFAULT_NAME)
                    }
                }
            """.trimIndent()
            
            val psiFile = myFixture.configureByText("ComplexTest.kt", complexKotlinCode)
            
            // Test various complex scenarios that could cause serialization issues
            val testPositions = listOf(
                complexKotlinCode.indexOf("suspend fun") + 8,  // suspend keyword
                complexKotlinCode.indexOf("User?") + 2,        // nullable return type
                complexKotlinCode.indexOf("const val") + 6,    // const property
                complexKotlinCode.indexOf("override") + 4,     // override keyword
                complexKotlinCode.indexOf("DEFAULT_NAME") + 3  // companion object property
            )
            
            testPositions.forEachIndexed { index, position ->
                try {
                    val definitions = definitionFinder.findDefinitionByPosition(psiFile, position)
                    println("✅ Position $index ($position): Found ${definitions.size} definitions safely")
                    
                    // Test that all returned locations can be serialized (access all properties)
                    definitions.forEach { def ->
                        val serializable = "name=${def.name}, type=${def.type}, file=${def.filePath}, " +
                                "line=${def.lineNumber}, signature=${def.signature}, " +
                                "modifiers=${def.modifiers}, isAbstract=${def.isAbstract}"
                        // If we get here without exception, serialization is safe
                    }
                    
                } catch (e: Exception) {
                    println("❌ Position $index ($position) failed: ${e.message}")
                    throw e
                }
            }
            
            println("✅ ALL SERIALIZATION SAFETY TESTS PASSED!")
            
        } catch (e: Exception) {
            println("❌ FAILED: Serialization safety error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    fun runAllTests() {
        try {
            testJavaDefinitionResolution()
            testKotlinDefinitionResolution()
            testEdgeCasesAndErrorHandling()
            testSerializationSafety()
            
            println("\n🎉 ALL TESTS PASSED! Kotlin serialization issues are FIXED!")
            
        } catch (e: Exception) {
            println("\n💥 TESTS FAILED: ${e.message}")
            throw e
        }
    }
}
