package com.extractor.mcp.tools;

import com.extractor.service.AnalysisService;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [TS-009] Returns metrics for specific component
 * [TS-013] Component not found returns actionable error
 * [TS-014] Error includes available component list
 */
class GetComponentMetricsToolTest {

    private AnalysisService service;
    @TempDir Path tempDir;

    @BeforeEach
    void setUp() { service = new AnalysisService(); }

    @Test
    void handle_withKnownComponent_returnsMetrics() throws Exception {
        // Use the actual project which has real Java classes Spoon can analyze
        Path project = Path.of(System.getProperty("user.dir"));
        List<String> ids = service.getComponentIds(project);
        // Skip if the project has no analyzable components (e.g., CI minimal env)
        if (ids.isEmpty()) return;

        String componentId = ids.get(0);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "get_component_metrics",
                Map.of("projectPath", project.toString(), "componentId", componentId));

        McpSchema.CallToolResult result = GetComponentMetricsTool.handle(service, request);

        assertFalse(result.isError());
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(text.contains(componentId), "Response must contain the component ID");
        assertTrue(text.contains("loc"), "Response must contain LOC metric");
    }

    @Test
    void handle_withUnknownComponent_returnsErrorWithAvailableList() throws Exception {
        Path project = Path.of(System.getProperty("user.dir"));
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "get_component_metrics",
                Map.of("projectPath", project.toString(), "componentId", "com.nonexistent.Foo"));

        McpSchema.CallToolResult result = GetComponentMetricsTool.handle(service, request);

        assertTrue(result.isError(), "Unknown component should return error");
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(text.contains("not found"), "Error should say 'not found'");
        // Available components list should be present
        assertTrue(text.contains("Available components"), "Error should list available components");
    }

    @Test
    void handle_withInvalidPath_returnsError() {
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "get_component_metrics",
                Map.of("projectPath", "/nope", "componentId", "com.Foo"));
        assertTrue(GetComponentMetricsTool.handle(service, request).isError());
    }
}
