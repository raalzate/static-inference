package com.extractor.mcp;

import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;

/**
 * JSON schema definitions for all MCP tool inputs.
 * Matches the contracts defined in contracts/mcp-tools.md.
 */
public final class ToolSchemas {

    private ToolSchemas() {}

    private static Map<String, Object> stringProp(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> enumProp(String description, List<String> values) {
        return Map.of("type", "string", "description", description, "enum", values);
    }

    public static McpSchema.JsonSchema analyzeProject() {
        return new McpSchema.JsonSchema("object",
                Map.of(
                        "projectPath", stringProp("Absolute path to the project root directory. Java projects must contain pom.xml or build.gradle; .NET projects must contain a .sln or .csproj file. Type is auto-detected."),
                        "outputDir", stringProp("Directory to write output JSON files. Defaults to project root if omitted.")
                ),
                List.of("projectPath"),
                null, null, null);
    }

    public static McpSchema.JsonSchema getDependencyGraph() {
        return new McpSchema.JsonSchema("object",
                Map.of(
                        "projectPath", stringProp("Absolute path to the project root directory (Java or .NET/C#, auto-detected from marker files)")
                ),
                List.of("projectPath"),
                null, null, null);
    }

    public static McpSchema.JsonSchema getArchitectureProposal() {
        return new McpSchema.JsonSchema("object",
                Map.of(
                        "projectPath", stringProp("Absolute path to the project root directory (Java or .NET/C#, auto-detected from marker files)"),
                        "minViability", enumProp("Minimum viability threshold. Only proposals meeting or exceeding this level are returned.", List.of("Alta", "Media", "Baja"))
                ),
                List.of("projectPath"),
                null, null, null);
    }

    public static McpSchema.JsonSchema getApiContracts() {
        return new McpSchema.JsonSchema("object",
                Map.of(
                        "projectPath", stringProp("Absolute path to the project root directory. Java: detects @RestController, @Path. .NET: detects [ApiController] with [HttpGet/Post/Put/Delete].")
                ),
                List.of("projectPath"),
                null, null, null);
    }

    public static McpSchema.JsonSchema getComponentMetrics() {
        return new McpSchema.JsonSchema("object",
                Map.of(
                        "projectPath", stringProp("Absolute path to the project root directory (Java or .NET/C#, auto-detected from marker files)"),
                        "componentId", stringProp("Fully qualified type name. Java: 'com.example.service.UserService'. .NET: 'ECommerceApp.Services.ProductService'")
                ),
                List.of("projectPath", "componentId"),
                null, null, null);
    }
}
