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
 * [TS-007] Returns only contract data
 * [TS-012] Returns ApiContracts structure
 */
class GetApiContractsToolTest {

    private AnalysisService service;
    @TempDir Path tempDir;

    @BeforeEach
    void setUp() { service = new AnalysisService(); }

    @Test
    void handle_withValidProject_returnsApiContractsOnly() throws IOException {
        Path project = createProject();
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "get_api_contracts", Map.of("projectPath", project.toString()));

        McpSchema.CallToolResult result = GetApiContractsTool.handle(service, request);

        assertFalse(result.isError());
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(text.contains("endpoints"), "Response must contain endpoints");
        assertTrue(text.contains("schemas"), "Response must contain schemas");
        // Must NOT contain graph/architecture data
        assertFalse(text.contains("proposals"), "Should not contain architecture proposals");
    }

    @Test
    void handle_withInvalidPath_returnsError() {
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "get_api_contracts", Map.of("projectPath", "/nope"));
        assertTrue(GetApiContractsTool.handle(service, request).isError());
    }

    private Path createProject() throws IOException {
        Path p = tempDir.resolve("proj");
        Path src = p.resolve("src/main/java/com/test");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Svc.java"), "package com.test;\npublic class Svc {}");
        return p;
    }
}
