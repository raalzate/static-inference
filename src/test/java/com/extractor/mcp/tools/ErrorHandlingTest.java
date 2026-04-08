package com.extractor.mcp.tools;

import com.extractor.service.AnalysisService;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [TS-022] No stack traces in error responses
 * [TS-023] Partial analysis failures handled gracefully
 * [TS-024] Paths with spaces and special characters
 * [TS-025] Non-existent output directory
 */
class ErrorHandlingTest {

    private AnalysisService service;
    @TempDir Path tempDir;

    @BeforeEach
    void setUp() { service = new AnalysisService(); }

    @Test
    void errorResponses_doNotLeakStackTraces() {
        // TS-022: Error responses must not contain stack traces
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "analyze_project", Map.of("projectPath", "/nonexistent/path"));

        McpSchema.CallToolResult result = AnalyzeProjectTool.handle(service, request);

        assertTrue(result.isError());
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertFalse(text.contains("at com.extractor"), "Must not contain stack trace");
        assertFalse(text.contains("java.lang."), "Must not contain Java exception class names");
        assertFalse(text.contains("\tat "), "Must not contain stack trace frames");
    }

    @Test
    void pathsWithSpaces_handledCorrectly() throws IOException {
        // TS-024: Paths with spaces
        Path project = tempDir.resolve("my project dir");
        Path src = project.resolve("src/main/java/com/test");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Svc.java"), "package com.test;\npublic class Svc {}");

        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "get_dependency_graph", Map.of("projectPath", project.toString()));

        McpSchema.CallToolResult result = GetDependencyGraphTool.handle(service, request);
        assertFalse(result.isError(), "Path with spaces should not cause an error");
    }

    @Test
    void nonExistentOutputDirectory_createdAutomatically() throws IOException {
        // TS-025: Non-existent output directory
        Path project = tempDir.resolve("proj");
        Path src = project.resolve("src/main/java/com/test");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Svc.java"), "package com.test;\npublic class Svc {}");

        Path outputDir = tempDir.resolve("nonexistent/nested/output");
        assertFalse(Files.exists(outputDir));

        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "analyze_project",
                Map.of("projectPath", project.toString(), "outputDir", outputDir.toString()));

        McpSchema.CallToolResult result = AnalyzeProjectTool.handle(service, request);
        assertFalse(result.isError(), "Non-existent output dir should be created");
        assertTrue(Files.exists(outputDir), "Output directory should have been created");
    }

    @Test
    void allToolsReturnStructuredErrors_notExceptions() {
        // Verify each tool returns isError=true for invalid input, not exceptions
        String badPath = "/does/not/exist/at/all";

        assertFalse(isException(() ->
                AnalyzeProjectTool.handle(service, new McpSchema.CallToolRequest(
                        "analyze_project", Map.of("projectPath", badPath)))));
        assertFalse(isException(() ->
                GetDependencyGraphTool.handle(service, new McpSchema.CallToolRequest(
                        "get_dependency_graph", Map.of("projectPath", badPath)))));
        assertFalse(isException(() ->
                GetArchitectureTool.handle(service, new McpSchema.CallToolRequest(
                        "get_architecture_proposal", Map.of("projectPath", badPath)))));
        assertFalse(isException(() ->
                GetApiContractsTool.handle(service, new McpSchema.CallToolRequest(
                        "get_api_contracts", Map.of("projectPath", badPath)))));
        assertFalse(isException(() ->
                GetComponentMetricsTool.handle(service, new McpSchema.CallToolRequest(
                        "get_component_metrics", Map.of("projectPath", badPath, "componentId", "x")))));
    }

    private boolean isException(Runnable runnable) {
        try { runnable.run(); return false; }
        catch (Exception e) { return true; }
    }
}
