package com.extractor.mcp.tools;

import com.extractor.mcp.ToolSchemas;
import com.extractor.model.DependencyGraph;
import com.extractor.service.AnalysisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * MCP tool handler for get_api_contracts (FR-006).
 */
public final class GetApiContractsTool {

    private GetApiContractsTool() {}

    public static McpServerFeatures.SyncToolSpecification spec(AnalysisService service) {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("get_api_contracts")
                .description("Returns REST endpoints and messaging contracts discovered in the project (Java or .NET/C#, auto-detected). For .NET, detects ASP.NET Core [HttpGet/Post/Put/Delete] attributes. Does not include dependency graph or architecture data.")
                .inputSchema(ToolSchemas.getApiContracts())
                .build();

        return new McpServerFeatures.SyncToolSpecification(tool,
                (exchange, request) -> handle(service, request));
    }

    public static McpSchema.CallToolResult handle(AnalysisService service, McpSchema.CallToolRequest request) {
        try {
            String projectPath = (String) request.arguments().get("projectPath");
            Path projectRoot = Paths.get(projectPath);

            DependencyGraph.ApiContracts contracts = service.getApiContracts(projectRoot);

            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
            String json = mapper.writeValueAsString(contracts);

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
