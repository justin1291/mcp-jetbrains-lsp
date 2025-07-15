# JetBrains MCP Language Service Plugin

A JetBrains IDE plugin that extends the [MCP (Model Context Protocol) Server](https://github.com/JetBrains/mcp-server-plugin) with language service capabilities, enabling AI assistants to understand and navigate code with IDE-level intelligence.

Link to jetbrains marketplace https://plugins.jetbrains.com/plugin/27888-mcp-language-service-tools?noRedirect=true

## What is this?

This plugin provides MCP tools that expose IntelliJ's powerful code analysis features to AI assistants like Claude. It allows AI to:
- Extract symbols from files (classes, methods, fields)
- Navigate to symbol definitions (go-to-definition)
- Find all usages of symbols (find references)
- Get type information and documentation (hover info)

## Architecture Overview

The plugin follows a modular, extensible architecture that separates concerns and makes adding new language support trivial:

```
┌─────────────────────┐     ┌────────────────────┐     ┌─────────────────────┐
│    MCP Tools        │     │    Factories       │     │  Language Impls     │
│  (Protocol Layer)   │────▶│ (Language Router)  │────▶│ (Analysis Logic)    │
└─────────────────────┘     └────────────────────┘     └─────────────────────┘
         │                           │                            │
         │                           │                            │
    Handles MCP              Detects language            Language-specific
    protocol and             and returns                code analysis
    delegates to             appropriate               implementation
    factories                implementation
```

### Key Issues
Unable to make python optional at the moment.
Tests that rely on indexing are not currently working. Will investigate different strategies for testing in the future.


### Key Components

1. **MCP Tools** (`src/main/kotlin/dev/mcp/extensions/lsp/tools/`)
   - Handle MCP protocol communication
   - Parse arguments and format responses
   - Delegate to factories for language-specific logic

2. **Factories** (`src/main/kotlin/dev/mcp/extensions/lsp/core/factories/`)
   - Detect language from PSI elements
   - Cache instances for performance
   - Provide clear error messages for unsupported languages

3. **Interfaces** (`src/main/kotlin/dev/mcp/extensions/lsp/core/interfaces/`)
   - Define contracts for language implementations
   - Enable loose coupling and extensibility

4. **Language Implementations** (`src/main/kotlin/dev/mcp/extensions/lsp/languages/`)
   - Implement language-specific analysis logic
   - Extend BaseLanguageHandler for common functionality
   - Currently supports Java/Kotlin


### Example: Refactoring a Method Safely

**Before:** Claude Code can see your files but can't understand code relationships:
```
❌ "Can I rename this method safely?" → Claude guesses based on text search
❌ Risk missing usages in other files or breaking code
```

**After:** Claude Code has IDE-level intelligence:
```bash
# Claude can now systematically analyze your code:
$ claude "Can I safely rename the processUserData method?"

> Let me analyze this method and its usages...
> 
> Using get_symbols_in_file on UserService.java...
> Found processUserData at line 45
> 
> Using find_symbol_references for processUserData...
> Found 8 usages across 4 files:
> - UserController.java:23 (method_call)
> - DataProcessor.java:67 (method_call) 
> - UserServiceTest.java:15,28 (method_calls in tests)
> - ApiDocumentation.md:102 (documentation reference)
>
> ✅ Safe to rename! All usages are in your project.
> ⚠️  Don't forget to update the documentation reference.
```

**The difference:** Claude Code now understands your codebase structure and can make informed decisions about refactoring, debugging, and code changes using the same analysis tools your IDE provides.

## Installation

### From Source
```bash
# Clone the repository
git clone https://github.com/yourusername/mcp-idea-lsp
cd mcp-idea-lsp

# Build the plugin
./gradlew buildPlugin

# The plugin ZIP will be in build/distributions/
```

### Install in IntelliJ
1. Open IntelliJ IDEA
2. Go to Settings → Plugins → ⚙️ → Install Plugin from Disk
3. Select the ZIP file from `build/distributions/`
4. Restart the IDE

## Prerequisites
- IntelliJ IDEA 2024.3 or later
- [MCP Server Plugin](https://plugins.jetbrains.com/plugin/12345-mcp-server) v1.0.30+
- Java 21 or later

## Documentation

### Language Feature Documentation
Detailed documentation about language-specific features and implementation considerations can be found in the `docs/language-features/` directory:

- **[Java Features](docs/language-features/java-features.md)** - Java and Kotlin language specifics
- **[Python Features](docs/language-features/python-features.md)** - Python language specifics (for ongoing implementation)
- **[JavaScript Features](docs/language-features/javascript-features.md)** - JavaScript/TypeScript specifics (planned)
- **[API Design Considerations](docs/language-features/api-design-considerations.md)** - Cross-language insights and future API improvements

This documentation helps guide implementation decisions and ensures we properly handle each language's unique features.

## MCP Tools API Reference

### `get_symbols_in_file`
Extract all symbols from a file with optional filtering and hierarchy.

**Arguments:**
- `filePath`: Path to the file relative to project root
- `hierarchical`: If true, returns nested structure; if false, returns flat list (default: false)
- `symbolTypes`: Optional filter for symbol types (e.g., ["class", "method", "field", "import"])
- `includeImports`: Whether to include import statements (default: false)

**Example Request:**
```json
{
  "filePath": "src/main/java/MyClass.java",
  "hierarchical": true,
  "symbolTypes": ["class", "method"],
  "includeImports": false
}
```

**Response Structure:**
```json
{
  "name": "methodName",
  "type": "com.example.MyClass.methodName",
  "kind": "method",
  "startOffset": 100,
  "endOffset": 200,
  "lineNumber": 10,
  "modifiers": ["public", "static"],
  "parameters": ["String arg1", "int arg2"],
  "returnType": "void",
  "children": []  // Only in hierarchical mode
}
```

### `find_symbol_definition`
Navigate to where a symbol is defined (like Ctrl+Click in the IDE).

**Arguments:**
- `symbolName`: Name of the symbol to find (optional if using position)
- `filePath`: Optional file path for position-based search
- `position`: Optional character offset in the file

**Example Request:**
```json
{
  "symbolName": "MyClass",
  "filePath": null,
  "position": null
}
```

**Response Structure:**
```json
{
  "name": "MyClass",
  "filePath": "src/main/java/com/example/MyClass.java",
  "startOffset": 100,
  "endOffset": 200,
  "lineNumber": 5,
  "type": "class",
  "signature": "public class MyClass",
  "containingClass": "com.example.ParentClass",
  "modifiers": ["public"],
  "isAbstract": false
}
```

### `find_symbol_references`
Find all places where a symbol is used (like Find Usages in the IDE).

**Arguments:**
- `symbolName`: Name of the symbol (optional if using position)
- `filePath`: Optional file path for position-based search
- `position`: Optional character offset
- `includeDeclaration`: Whether to include the declaration itself (default: false)

**Example Request:**
```json
{
  "symbolName": "processData",
  "includeDeclaration": true
}
```

**Response Structure:**
```json
{
  "filePath": "src/main/java/Usage.java",
  "startOffset": 500,
  "endOffset": 510,
  "lineNumber": 25,
  "usageType": "method_call",
  "elementText": "myMethod()",
  "preview": "result = myMethod();",
  "containingMethod": "processData",
  "containingClass": "com.example.Usage"
}
```

### `get_hover_info`
Get type information and documentation for a symbol at a position.

**Arguments:**
- `filePath`: Path to the file
- `position`: Character offset in the file

**Example Request:**
```json
{
  "filePath": "src/main/java/MyClass.java",
  "position": 150
}
```

**Response Structure:**
```json
{
  "elementName": "myVariable",
  "elementType": "variable",
  "type": "List<String>",
  "presentableText": "myVariable: List<String>",
  "javaDoc": "/** Documentation */",
  "signature": "List<String> myVariable",
  "modifiers": ["private", "final"]
}
```

## Development

### Building
```bash
./gradlew build
```

### Testing
```bash
# Run all tests
./gradlew test

# Run specific test
./gradlew test --tests "*.GetSymbolsInFileToolTest"
```

### Running in Development
```bash
./gradlew runIde
```

### Creating a Release

This project uses [Release Please](https://github.com/googleapis/release-please) for automated releases.

**How it works:**
1. Use [Conventional Commits](https://www.conventionalcommits.org/) in your commit messages:
   - `fix:` for bug fixes (creates a patch release)
   - `feat:` for new features (creates a minor release)
   - `feat!:` or `BREAKING CHANGE:` for breaking changes (creates a major release)
   - `chore:`, `docs:`, `style:`, `refactor:`, `test:` don't trigger releases

2. Release Please automatically:
   - Creates/updates a PR with version bumps and changelog
   - When you merge that PR, it creates a GitHub release
   - Uploads the plugin ZIP to the release

**Example commits:**
```bash
# Bug fix (1.0.0 → 1.0.1)
git commit -m "fix: handle null PSI elements in symbol extraction"

# New feature (1.0.1 → 1.1.0)
git commit -m "feat: add support for Kotlin coroutines"

# Breaking change (1.1.0 → 2.0.0)
git commit -m "feat!: change API response format"
```

## Project Structure
```
├── src/
│   ├── main/kotlin/dev/mcp/extensions/lsp/
│   │   ├── core/
│   │   │   ├── interfaces/      # Language implementation contracts
│   │   │   ├── models/          # Data models (centralized)
│   │   │   ├── factories/       # Language detection and caching
│   │   │   └── utils/           # Shared utilities
│   │   ├── languages/
│   │   │   ├── base/            # Base implementation
│   │   │   └── java/            # Java/Kotlin implementations
│   │   └── tools/               # MCP protocol handlers
│   └── test/kotlin/             # Test suite
├── docs/
│   └── language-features/       # Language-specific documentation
│       ├── java-features.md     # Java/Kotlin language specifics
│       ├── python-features.md   # Python language specifics
│       ├── javascript-features.md # JS/TS language specifics
│       └── api-design-considerations.md # Cross-language insights
├── build.gradle.kts             # Build configuration
└── .github/workflows/           # CI/CD pipeline
```

## Performance Considerations

- **Factory Caching**: Language implementations are cached per language to avoid repeated instantiation
- **Read Actions**: All PSI operations are wrapped in read actions for thread safety
- **Performance Logging**: Built-in timing measurements for all operations

## Troubleshooting

### Plugin Not Found
Ensure the MCP Server plugin is installed and enabled. Check version compatibility (requires v1.0.30+).

### Tools Not Available
Verify all tools are registered in `plugin.xml`:
```xml
<extensions defaultExtensionNs="com.intellij.mcpServer">
    <mcpTool implementation="dev.mcp.extensions.lsp.tools.GetSymbolsInFileTool"/>
    <mcpTool implementation="dev.mcp.extensions.lsp.tools.FindSymbolDefinitionTool"/>
    <mcpTool implementation="dev.mcp.extensions.lsp.tools.FindSymbolReferencesTool"/>
    <mcpTool implementation="dev.mcp.extensions.lsp.tools.GetHoverInfoTool"/>
</extensions>
```

### Performance Issues
- **Invalidate Caches**: File → Invalidate Caches and Restart
- **Memory Settings**: Help → Change Memory Settings (increase to 2048MB+)
- **Review Usage**: Avoid excessive searches on large codebases

### Build Issues
- **Clean Build**: `./gradlew clean build`
- **Check Java Version**: Ensure Java 21 is configured
- **Verify Dependencies**: Run `./gradlew dependencies` to check resolution

### Test Dependencies Not Resolved
Ensure your `build.gradle.kts` includes all required dependencies:
```kotlin
dependencies {
    intellijPlatform {
        create("IC", "2024.3")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        plugin("com.intellij.mcpServer", "1.0.30")
        bundledPlugin("com.intellij.java")
        instrumentationTools()
        testImplementation()
    }
    
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}
```

### PSI Elements Not Found in Tests
Ensure documents are committed in test setup:
```kotlin
@BeforeEach
override fun setUp() {
    super.setUp()
    PsiDocumentManager.getInstance(project).commitAllDocuments()
}
```

### Memory Issues During Tests
If tests fail with OutOfMemoryError:
```kotlin
tasks {
    test {
        useJUnitPlatform()
        jvmArgs("-Xmx2048m")
    }
}
```

## Roadmap

### Phase 3: JavaScript/TypeScript Support
- **JS/TS Symbol Extraction**: Classes, functions, React components, exports
- **JS/TS Definition Finding**: Navigate through modules and node_modules
- **JS/TS Reference Finding**: Track usage across files and imports
- **JS/TS Hover Info**: JSDoc, type information, component props

### Future Enhancements
- **Go Support**: Structs, interfaces, functions, methods
- **Rust Support**: Structs, traits, functions, macros
- **Multi-language Projects**: Handle mixed codebases seamlessly
- **Performance Optimizations**: Caching, incremental updates
- **Advanced Features**: Call hierarchy, type hierarchy, rename refactoring

## Adding Language Support

Thanks to our modular architecture, adding a new language is straightforward:

### Quick Overview
1. **Implement 4 interfaces** for your language
2. **Update 4 factories** to recognize your language
3. **Add plugin dependency** in plugin.xml
4. That's it! No changes to MCP tools needed

### Language-Specific Considerations
- **Dynamic Languages**: Handle missing type information gracefully
- **Language Features**: Map to common concepts (decorators → annotations)
- **Performance**: Use language-specific indices for fast lookups

### Testing New Languages
```kotlin
class PythonSymbolExtractorTest : BaseTest() {
    @Test
    fun testExtractPythonClass() {
        val file = myFixture.addFileToProject("test.py", """
            class MyClass:
                def method(self):
                    pass
        """.trimIndent())
        
        val symbols = extractor.extractSymbolsFlat(file, args)
        assertTrue(symbols.any { it.name == "MyClass" && it.kind == "class" })
    }
}
```

## Contributing

### Development Setup
1. Clone the repository
2. Open in IntelliJ IDEA (2024.3+)
3. Import as Gradle project
4. Run `./gradlew build`

### Code Style
- Use Kotlin idioms (null-safe operators, scope functions)
- Follow existing patterns for consistency
- Add KDoc for public APIs
- No inline comments - use descriptive names

### Testing
```bash
# Run all tests
./gradlew test

# Run with coverage
./gradlew test jacocoTestReport

# Run specific test
./gradlew test --tests "*.PythonSymbolExtractorTest"
```

### Submitting Changes
1. Fork and create feature branch
2. Write tests for your changes
3. Use [Conventional Commits](https://www.conventionalcommits.org/):
   - `fix:` for bug fixes
   - `feat:` for new features
   - `docs:` for documentation
   - `test:` for test additions
   - `refactor:` for code structure changes
4. Ensure all tests pass
5. Submit PR with clear description

### Pull Request Checklist
- [ ] Tests pass locally
- [ ] Code follows Kotlin conventions
- [ ] Public APIs have KDoc comments
- [ ] No hardcoded strings
- [ ] Error handling implemented
- [ ] Performance considered
- [ ] Documentation updated

### Getting Help
- **Issues**: Report bugs or request features
- **Discussions**: Ask questions or share ideas
- **Wiki**: Architecture details and guides

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- Built on top of the [JetBrains MCP Server Plugin](https://github.com/JetBrains/mcp-server-plugin)
- Inspired by [Serena](https://github.com/oraios/serena) - Python MCP language service
- Uses IntelliJ Platform SDK for code analysis
