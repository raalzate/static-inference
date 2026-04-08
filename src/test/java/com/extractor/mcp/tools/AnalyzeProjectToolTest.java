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
 * Tests for AnalyzeProjectTool MCP handler.
 * [TS-001] Successful full analysis
 * [TS-002] Invalid project path
 * [TS-003] No Java sources
 * [TS-005] Correct response structure
 * [TS-006] Output files generated
 */
class AnalyzeProjectToolTest {

    private AnalysisService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new AnalysisService();
    }

    @Test
    void handle_withValidProject_returnsSuccessWithStructuredResult() throws IOException {
        Path project = createMinimalJavaProject();
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("analyze_project",
                Map.of("projectPath", project.toString(), "outputDir", tempDir.resolve("output").toString()));

        McpSchema.CallToolResult result = AnalyzeProjectTool.handle(service, request);

        assertFalse(result.isError(), "Should not be an error");
        assertNotNull(result.content());
        assertFalse(result.content().isEmpty());

        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(text.contains("componentCount"), "Response must contain componentCount");
        assertTrue(text.contains("edgeCount"), "Response must contain edgeCount");
        assertTrue(text.contains("proposalCount"), "Response must contain proposalCount");
        assertTrue(text.contains("endpointCount"), "Response must contain endpointCount");
        assertTrue(text.contains("filesGenerated"), "Response must contain filesGenerated");
        assertTrue(text.contains("summary"), "Response must contain summary");
    }

    @Test
    void handle_withValidProject_generatesOutputFiles() throws IOException {
        Path project = createMinimalJavaProject();
        Path outputDir = tempDir.resolve("out");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("analyze_project",
                Map.of("projectPath", project.toString(), "outputDir", outputDir.toString()));

        AnalyzeProjectTool.handle(service, request);

        assertTrue(Files.exists(outputDir.resolve("output.json")), "output.json must be generated");
        assertTrue(Files.exists(outputDir.resolve("output_architecture.json")), "output_architecture.json must be generated");
        assertTrue(Files.exists(outputDir.resolve("output_entrypoints.json")), "output_entrypoints.json must be generated");
    }

    @Test
    void handle_withNonExistentPath_returnsError() {
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("analyze_project",
                Map.of("projectPath", "/nonexistent/path"));

        McpSchema.CallToolResult result = AnalyzeProjectTool.handle(service, request);

        assertTrue(result.isError(), "Should be an error");
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(text.contains("does not exist"), "Error should indicate path doesn't exist");
    }

    @Test
    void handle_withEmptyDirectory_noJavaSources_returnsResult() throws IOException {
        Path emptyDir = tempDir.resolve("empty");
        Files.createDirectories(emptyDir);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("analyze_project",
                Map.of("projectPath", emptyDir.toString(), "outputDir", tempDir.resolve("out2").toString()));

        // Empty dir should still succeed (0 components) — graceful degradation
        McpSchema.CallToolResult result = AnalyzeProjectTool.handle(service, request);
        assertNotNull(result);
    }

    private Path createMinimalJavaProject() throws IOException {
        Path project = tempDir.resolve("test-project");
        Path srcDir = project.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("HelloService.java"),
                """
                package com.example;
                public class HelloService {
                    public String greet(String name) {
                        return "Hello, " + name;
                    }
                }
                """);
        return project;
    }
}
