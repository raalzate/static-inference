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
 * MCP tool handler for get_dependency_graph (FR-004).
 */
public final class GetDependencyGraphTool {

    private GetDependencyGraphTool() {}

    public static McpServerFeatures.SyncToolSpecification spec(AnalysisService service) {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("get_dependency_graph")
                .description("Returns the dependency graph of a Java or .NET/C# project (auto-detected), including all components and their relationships. Does not generate files on disk.")
                .inputSchema(ToolSchemas.getDependencyGraph())
                .build();

        return new McpServerFeatures.SyncToolSpecification(tool,
                (exchange, request) -> handle(service, request));
    }

    public static McpSchema.CallToolResult handle(AnalysisService service, McpSchema.CallToolRequest request) {
        try {
            String projectPath = (String) request.arguments().get("projectPath");
            Path projectRoot = Paths.get(projectPath);

            DependencyGraph graph = service.getDependencyGraph(projectRoot);

            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
            String json = mapper.writeValueAsString(graph);

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
