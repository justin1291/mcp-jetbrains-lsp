package dev.mcp.extensions.lsp

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import kotlin.test.*

/**
 * Base class for integration tests that test LSP tools against real demo project modules
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseTest {
    
    protected lateinit var myFixture: CodeInsightTestFixture
    
    protected val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    
    @BeforeEach
    open fun setUp() {
        val factory = IdeaTestFixtureFactory.getFixtureFactory()
        
        // Create a heavy fixture that can include source roots  
        val projectBuilder = factory.createFixtureBuilder("integration-test")
        
        // Add the demo project source as a content root
        val projectFixture = projectBuilder.fixture
        myFixture = factory.createCodeInsightFixture(projectFixture)
        myFixture.setUp()
        
        // Copy demo files to test project for PSI indexing
        copyDemoFilesToProject()
    }
    
    @AfterEach
    fun tearDown() {
        try {
            myFixture.tearDown()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }
    
    protected val project get() = myFixture.project
    
    /**
     * Copies demo project files to the test project for PSI indexing
     */
    private fun copyDemoFilesToProject() {
        val demoPath = "./demo-project/src/main/java/com/example/demo/"
        val files = listOf(
            "UserService.java",
            "User.java", 
            "ApiController.java",
            "DataProcessor.java"
        )
        
        println("Copying demo files from: $demoPath")
        
        for (fileName in files) {
            val file = java.io.File(demoPath + fileName)
            if (file.exists()) {
                val content = file.readText()
                val createdFile = myFixture.addFileToProject("src/main/java/com/example/demo/$fileName", content)
                println("✓ Copied $fileName to test project (${content.length} chars)")
                // Force PSI to process the file
                myFixture.configureFromExistingVirtualFile(createdFile.virtualFile)
            } else {
                println("✗ Demo file not found: ${file.absolutePath}")
            }
        }
        
        println("Test project structure setup complete")
    }
    
    /**
     * Utility to parse JSON response content
     */
    protected inline fun <reified T> parseJsonResponse(responseContent: String?): T {
        assertNotNull(responseContent, "Response content should not be null")
        return json.decodeFromString(responseContent!!)
    }
}
