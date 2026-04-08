package com.extractor.mcp.tools;

import com.extractor.mcp.ToolSchemas;
import com.extractor.service.AnalysisResult;
import com.extractor.service.AnalysisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool handler for analyze_project (FR-003).
 * Runs the full analysis pipeline and writes output files to disk.
 */
public final class AnalyzeProjectTool {

    private AnalyzeProjectTool() {}

    public static McpServerFeatures.SyncToolSpecification spec(AnalysisService service) {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("analyze_project")
                .description("Runs the full static analysis pipeline on a Java or .NET/C# project (auto-detected). Returns dependency graph, architecture proposals, and API contracts. Generates output files on disk.")
                .inputSchema(ToolSchemas.analyzeProject())
                .build();

        return new McpServerFeatures.SyncToolSpecification(tool,
                (exchange, request) -> handle(service, request));
    }

    public static McpSchema.CallToolResult handle(AnalysisService service, McpSchema.CallToolRequest request) {
        try {
            String projectPath = (String) request.arguments().get("projectPath");
            String outputDir = (String) request.arguments().get("outputDir");

            Path projectRoot = Paths.get(projectPath);
            if (!Files.exists(projectRoot)) {
                return errorResult("Project path '" + projectPath + "' does not exist");
            }
            if (!Files.isDirectory(projectRoot)) {
                return errorResult("Project path '" + projectPath + "' is not a directory");
            }

            AnalysisResult result = service.analyzeProject(projectRoot);

            // Determine output directory
            Path outDir = (outputDir != null && !outputDir.isEmpty())
                    ? Paths.get(outputDir) : projectRoot;
            if (!Files.exists(outDir)) {
                Files.createDirectories(outDir);
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

            // Write output files
            List<String> filesGenerated = new ArrayList<>();
            String outputJson = outDir.resolve("output.json").toString();
            writeFile(mapper.writeValueAsString(result.getDependencyGraph()), outputJson);
            filesGenerated.add(outputJson);

            String archJson = outDir.resolve("output_architecture.json").toString();
            writeFile(mapper.writeValueAsString(result.getArchitecture()), archJson);
            filesGenerated.add(archJson);

            String entryJson = outDir.resolve("output_entrypoints.json").toString();
            writeFile(mapper.writeValueAsString(result.getApiContracts()), entryJson);
            filesGenerated.add(entryJson);

            // Build response
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("componentCount", result.getComponentCount());
            response.put("edgeCount", result.getEdgeCount());
            response.put("proposalCount", result.getArchitecture().getProposals().size());
            response.put("endpointCount", result.getApiContracts() != null
                    ? result.getApiContracts().getEndpoints().size() : 0);
            response.put("filesGenerated", filesGenerated);
            response.put("summary", result.getArchitecture().getSummary());

            String json = mapper.writeValueAsString(response);
            return McpSchema.CallToolResult.builder()
                    .addTextContent(json)
                    .isError(false)
                    .build();

        } catch (IllegalArgumentException e) {
            return errorResult(e.getMessage());
        } catch (Exception e) {
            return errorResult("Analysis failed: " + e.getMessage());
        }
    }

    private static void writeFile(String content, String filePath) throws Exception {
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(content);
        }
    }

    public static McpSchema.CallToolResult errorResult(String message) {
        return McpSchema.CallToolResult.builder()
                .addTextContent(message)
                .isError(true)
                .build();
    }
}
