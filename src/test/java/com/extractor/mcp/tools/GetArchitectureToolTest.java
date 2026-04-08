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
 * [TS-008] Filtered by viability
 * [TS-011] Returns ConsolidatedArchitecture
 */
class GetArchitectureToolTest {

    private AnalysisService service;
    @TempDir Path tempDir;

    @BeforeEach
    void setUp() { service = new AnalysisService(); }

    @Test
    void handle_withNoFilter_returnsFullArchitecture() throws IOException {
        Path project = createProject();
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "get_architecture_proposal", Map.of("projectPath", project.toString()));

        McpSchema.CallToolResult result = GetArchitectureTool.handle(service, request);

        assertFalse(result.isError());
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(text.contains("proposals"), "Response must contain proposals");
        assertTrue(text.contains("summary"), "Response must contain summary");
    }

    @Test
    void handle_withAltaFilter_returnsOnlyAltaProposals() throws IOException {
        Path project = createProject();
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "get_architecture_proposal",
                Map.of("projectPath", project.toString(), "minViability", "Alta"));

        McpSchema.CallToolResult result = GetArchitectureTool.handle(service, request);

        assertFalse(result.isError());
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        // If any proposals exist, they must all be Alta
        if (text.contains("\"viability\"")) {
            assertFalse(text.contains("\"viability\" : \"Baja\""), "Should not contain Baja viability");
            assertFalse(text.contains("\"viability\" : \"Media\""), "Should not contain Media viability");
        }
    }

    @Test
    void handle_withInvalidPath_returnsError() {
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "get_architecture_proposal", Map.of("projectPath", "/nope"));
        assertTrue(GetArchitectureTool.handle(service, request).isError());
    }

    private Path createProject() throws IOException {
        Path p = tempDir.resolve("proj");
        Path src = p.resolve("src/main/java/com/test");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Svc.java"), "package com.test;\npublic class Svc {}");
        return p;
    }
}
