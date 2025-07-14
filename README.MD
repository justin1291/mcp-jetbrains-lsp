<idea-plugin>
    <!-- Java/Kotlin language support configuration -->
    <!-- This file is only loaded when com.intellij.modules.java is available -->
    
    <!-- Register Java services explicitly when Java plugin is available -->
    <extensions defaultExtensionNs="com.intellij">
        <applicationService serviceImplementation="dev.mcp.extensions.lsp.languages.java.JavaSymbolExtractor"/>
        <applicationService serviceImplementation="dev.mcp.extensions.lsp.languages.java.JavaDefinitionFinder"/>
        <applicationService serviceImplementation="dev.mcp.extensions.lsp.languages.java.JavaReferenceFinder"/>
        <applicationService serviceImplementation="dev.mcp.extensions.lsp.languages.java.JavaHoverInfoProvider"/>
    </extensions>
</idea-plugin>
