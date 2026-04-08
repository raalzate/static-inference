package com.extractor.mcp.tools;

import com.extractor.mcp.ToolSchemas;
import com.extractor.model.Component;
import com.extractor.service.AnalysisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * MCP tool handler for get_component_metrics (FR-007).
 */
public final class GetComponentMetricsTool {

    private GetComponentMetricsTool() {}

    public static McpServerFeatures.SyncToolSpecification spec(AnalysisService service) {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("get_component_metrics")
                .description("Returns detailed metrics for a specific component (Java class or .NET type) including coupling (CBO), cohesion (LCOM), architectural layer, database tables, messaging role, and call graph.")
                .inputSchema(ToolSchemas.getComponentMetrics())
                .build();

        return new McpServerFeatures.SyncToolSpecification(tool,
                (exchange, request) -> handle(service, request));
    }

    public static McpSchema.CallToolResult handle(AnalysisService service, McpSchema.CallToolRequest request) {
        try {
            String projectPath = (String) request.arguments().get("projectPath");
            String componentId = (String) request.arguments().get("componentId");
            Path projectRoot = Paths.get(projectPath);

            Component component = service.getComponentMetrics(projectRoot, componentId);
            if (component == null) {
                List<String> available = service.getComponentIds(projectRoot);
                return AnalyzeProjectTool.errorResult(
                        "Component '" + componentId + "' not found in project. Available components: " + available);
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
            String json = mapper.writeValueAsString(component);

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
