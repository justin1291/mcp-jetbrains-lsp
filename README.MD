# JetBrains MCP Language Service Plugin

A JetBrains IDE plugin that extends the [MCP (Model Context Protocol) Server](https://github.com/JetBrains/mcp-server-plugin) with language service capabilities, enabling AI assistants to understand and navigate code with IDE-level intelligence.

## What is this?

This plugin provides MCP tools that expose IntelliJ's powerful code analysis features to AI assistants like Claude. It allows AI to:
- Extract symbols from files (classes, methods, fields)
- Navigate to symbol definitions (go-to-definition)
- Find all usages of symbols (find references)
- Get type information and documentation (hover info)

## Using with Claude Code

### Quick Setup
1. Install this plugin in IntelliJ IDEA (see Installation below)
2. Add to your Claude Code MCP configuration:

```json
{
  "mcpServers": {
    "intellij": {
      "command": "intellij-mcp-server",
      "args": ["--port", "9999"]
    }
  }
}
```

3. Restart Claude Code and open your IntelliJ project

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
├── src/main/kotlin/       # Plugin implementation
├── src/test/kotlin/       # Test suite
├── build.gradle.kts       # Build configuration
└── .github/workflows/     # CI/CD pipeline
```

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

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Write tests for your changes
4. Ensure all tests pass (`./gradlew test`)
5. Commit your changes using [Conventional Commits](https://www.conventionalcommits.org/):
   - `fix: correct typo in error message`
   - `feat: add Python language support`
   - `docs: update API examples`
6. Push to the branch (`git push origin feature/amazing-feature`)
7. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- Built on top of the [JetBrains MCP Server Plugin](https://github.com/JetBrains/mcp-server-plugin)
- Inspired by [Serena](https://github.com/oraios/serena) - Python MCP language service
- Uses IntelliJ Platform SDK for code analysis
