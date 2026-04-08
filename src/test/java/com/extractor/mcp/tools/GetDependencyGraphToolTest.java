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

/** [TS-010] get_dependency_graph returns full graph structure */
class GetDependencyGraphToolTest {

    private AnalysisService service;
    @TempDir Path tempDir;

    @BeforeEach
    void setUp() { service = new AnalysisService(); }

    @Test
    void handle_withValidProject_returnsGraphWithComponentsAndEdges() throws IOException {
        Path project = createProject();
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "get_dependency_graph", Map.of("projectPath", project.toString()));

        McpSchema.CallToolResult result = GetDependencyGraphTool.handle(service, request);

        assertFalse(result.isError());
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(text.contains("components"), "Response must contain components");
        assertTrue(text.contains("edges"), "Response must contain edges");
        assertTrue(text.contains("apiContracts") || text.contains("api_contracts"), "Response must contain api_contracts");
        assertTrue(text.contains("meta"), "Response must contain meta");
    }

    @Test
    void handle_withInvalidPath_returnsError() {
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "get_dependency_graph", Map.of("projectPath", "/no/such/path"));

        McpSchema.CallToolResult result = GetDependencyGraphTool.handle(service, request);
        assertTrue(result.isError());
    }

    private Path createProject() throws IOException {
        Path p = tempDir.resolve("proj");
        Path src = p.resolve("src/main/java/com/test");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Svc.java"), "package com.test;\npublic class Svc {}");
        return p;
    }
}
