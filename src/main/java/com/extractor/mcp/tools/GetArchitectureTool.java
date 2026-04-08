package com.extractor.mcp.tools;

import com.extractor.mcp.ToolSchemas;
import com.extractor.inference.ConsolidatedArchitecture;
import com.extractor.service.AnalysisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * MCP tool handler for get_architecture_proposal (FR-005).
 */
public final class GetArchitectureTool {

    private GetArchitectureTool() {}

    public static McpServerFeatures.SyncToolSpecification spec(AnalysisService service) {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("get_architecture_proposal")
                .description("Returns microservice decomposition proposals with viability scores, metrics, and recommended actions. Optionally filtered by minimum viability level.")
                .inputSchema(ToolSchemas.getArchitectureProposal())
                .build();

        return new McpServerFeatures.SyncToolSpecification(tool,
                (exchange, request) -> handle(service, request));
    }

    public static McpSchema.CallToolResult handle(AnalysisService service, McpSchema.CallToolRequest request) {
        try {
            String projectPath = (String) request.arguments().get("projectPath");
            String minViability = (String) request.arguments().get("minViability");
            Path projectRoot = Paths.get(projectPath);

            ConsolidatedArchitecture architecture = service.getArchitectureProposal(projectRoot, minViability);

            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
            String json = mapper.writeValueAsString(architecture);

            return McpSchema.CallToolResult.builder()
                    .addTextContent(json)
                    .isError(false)
                    .build();

        } catch (IllegalArgumentException e) {
            return AnalyzeProjectTool.errorResult(e.getMessage());
        } catch (Exception e) {
            return AnalyzeProjectTool.errorResult("Analysis failed: " + e.getMessage());
        }
    }
}
