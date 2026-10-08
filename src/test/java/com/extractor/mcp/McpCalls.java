package com.extractor.mcp;

import com.extractor.service.AnalysisService;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;

/**
 * Invokes the registered MCP tool handlers in-process, exactly as the MCP server dispatches them
 * ({@link McpServerMain#createToolSpecs(AnalysisService)}), and returns the text response.
 */
public final class McpCalls {

    private final List<McpServerFeatures.SyncToolSpecification> specs;

    public McpCalls(AnalysisService service) {
        this.specs = McpServerMain.createToolSpecs(service);
    }

    /** Result of a tool call. */
    public record Response(boolean error, String text) {
    }

    /** Calls a tool with the given arguments. */
    public Response call(String tool, Map<String, Object> arguments) {
        McpServerFeatures.SyncToolSpecification spec = specs.stream()
                .filter(s -> s.tool().name().equals(tool))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tool not registered: " + tool));
        McpSchema.CallToolResult result = spec.callHandler().apply(null, new McpSchema.CallToolRequest(tool, arguments));
        String text = result.content().isEmpty() ? "" : ((McpSchema.TextContent) result.content().get(0)).text();
        return new Response(Boolean.TRUE.equals(result.isError()), text);
    }

    /** Number of registered tools. */
    public int toolCount() {
        return specs.size();
    }
}
