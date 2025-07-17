package dev.mcp.extensions.lsp

import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File


/**
 * Base class for integration tests that test LSP tools against real demo project modules
 * Uses JavaTestFixtureFactory for proper multi-module setup
 */
@RunWith(JUnit4::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class JavaBaseTest : HeavyPlatformTestCase() {

    protected lateinit var myFixture: CodeInsightTestFixture

    @BeforeAll
    override fun setUp() {
        val projectBuilder =
            IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder("java-test")

        myFixture = JavaTestFixtureFactory.getFixtureFactory()
            .createCodeInsightFixture(projectBuilder.getFixture())

        val demoProjectPath = File("./java-demo-project").absolutePath
        val demoModule =
            projectBuilder.addModule<JavaModuleFixtureBuilder<*>?>(JavaModuleFixtureBuilder::class.java)
        demoModule.addContentRoot(demoProjectPath)
        demoModule.addSourceRoot("$demoProjectPath/src/main/java")

        myFixture.setUp()
    }

    @AfterAll
    override fun tearDown() {
        try {
            myFixture.tearDown()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    protected val fixtureProject get() = myFixture.project
}
