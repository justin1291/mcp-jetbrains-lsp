# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a JetBrains IDE plugin that extends the MCP (Model Context Protocol) Server with language service capabilities. It provides MCP tools that expose IntelliJ's code analysis features to AI assistants, enabling them to understand and navigate code with IDE-level intelligence.

## Development Commands
### Jetbrain Tools (test, build)
Prefer using tools provided by JetBrains for development tasks.
get_run_configurations - Lists all run configurations in the project
run_configuration - Runs the specified run configuration


### Manually Building and Testing
```bash
# Build the plugin
./gradlew build

# Run all tests 
./gradlew test

# Run tests skipping Python tests (if Python plugin not available)
./gradlew test -Dskip.python.tests=true

# Run specific test
./gradlew test --tests "*.GetSymbolsInFileToolTest"

# Build plugin distribution
./gradlew buildPlugin

# Run in development (launches test IDE instance)
./gradlew runIde

# Verify plugin compatibility
./gradlew verifyPlugin
```

### Code Quality
```bash
# Check dependencies
./gradlew dependencies

# Clean build
./gradlew clean build
```

## Known Issues

### Python Test Environment
Python tests currently fail in standard IntelliJ IDEA Community test environment because the Python plugin is not loaded. This is expected behavior for optional plugin dependencies.

**Workarounds:**
1. Run tests in PyCharm (where Python support is built-in)
2. Skip Python tests: `./gradlew test -Dskip.python.tests=true`
3. Test Python functionality manually using "Run IDE with Plugin" configuration

See `src/test/kotlin/dev/mcp/extensions/lsp/languages/python/README.md` for details.

## Architecture

The plugin follows a modular, factory-based architecture that separates concerns:

### Core Components
1. **MCP Tools** (`src/main/kotlin/dev/mcp/extensions/lsp/tools/`)
   - `GetSymbolsInFileTool` - Extract symbols from files
   - `FindSymbolDefinitionTool` - Navigate to symbol definitions  
   - `FindSymbolReferencesTool` - Find all symbol usages
   - `GetHoverInfoTool` - Get type information and documentation

2. **Factories** (`src/main/kotlin/dev/mcp/extensions/lsp/core/factories/`)
   - Detect language from PSI elements
   - Cache instances for performance
   - Route to appropriate language implementations

3. **Language Implementations** (`src/main/kotlin/dev/mcp/extensions/lsp/languages/`)
   - `java/` - Java/Kotlin support
   - `base/BaseLanguageHandler.kt` - Common functionality

4. **Models** (`src/main/kotlin/dev/mcp/extensions/lsp/core/models/`)
   - Centralized data models for symbols, definitions, references, hover info

### Adding New Language Support
To add support for a new language (e.g., Python):

1. Create language-specific implementations in `src/main/kotlin/dev/mcp/extensions/lsp/languages/python/`
2. Update the four factories to recognize the new language
3. Add plugin dependency in `plugin.xml`: `<depends optional="true">com.intellij.modules.python</depends>`
4. Language-specific tools should implement the base interfaces defined in `core/interfaces/`. Language implementations should be annotated with `@Service` to be discoverable by the factories.

## Key Technical Details

### Dependencies
- Built on IntelliJ Platform 2024.3+
- Requires Java 21
- Depends on MCP Server plugin v1.0.30+
- Uses `kotlinx-serialization-json` (compileOnly to avoid class loader conflicts)

### Test Configuration
- Uses JUnit 5 with JUnit 4 compatibility layer (required by IntelliJ Platform)
- Tests require PSI document commits: `PsiDocumentManager.getInstance(project).commitAllDocuments()`
- Memory settings: 2048MB for tests

### Performance Considerations
- Factory caching prevents repeated instantiation
- All PSI operations wrapped in read actions
- Built-in timing measurements for operations

## Plugin Distribution

The built plugin ZIP is created in `build/distributions/` and can be installed via:
Settings � Plugins � � � Install Plugin from Disk

## Release Process

Uses Conventional Commits with Release Please:
- `fix:` � patch release (1.0.0 � 1.0.1)
- `feat:` � minor release (1.0.1 � 1.1.0)  
- `feat!:` or `BREAKING CHANGE:` � major release (1.1.0 � 2.0.0)
