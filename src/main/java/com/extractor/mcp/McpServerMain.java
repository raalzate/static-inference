package com.extractor.mcp;

import com.extractor.mcp.tools.*;
import com.extractor.service.AnalysisService;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;

/**
 * MCP server entry point. Starts a synchronous MCP server over stdio transport
 * and registers all analysis tools. Delegates to AnalysisService (Constitution II).
 */
public class McpServerMain {

    private static final String SERVER_NAME = "static-inference";
    private static final String SERVER_VERSION = "1.0.0";

    /**
     * Creates tool specifications for all 5 MCP tools.
     * Exposed as package-private for testing.
     */
    static List<McpServerFeatures.SyncToolSpecification> createToolSpecs(AnalysisService service) {
        return List.of(
                AnalyzeProjectTool.spec(service),
                GetDependencyGraphTool.spec(service),
                GetArchitectureTool.spec(service),
                GetApiContractsTool.spec(service),
                GetComponentMetricsTool.spec(service)
        );
    }

    /**
     * Starts the MCP server with stdio transport. Blocks until stdin is closed.
     * Redirects SLF4J to stderr so logs don't corrupt the MCP protocol on stdout.
     */
    public static void start() {
        // Redirect SLF4J Simple to stderr — stdout is reserved for MCP protocol (research.md Decision 7)
        System.setProperty("org.slf4j.simpleLogger.logFile", "System.err");

        AnalysisService service = new AnalysisService();
        List<McpServerFeatures.SyncToolSpecification> tools = createToolSpecs(service);

        StdioServerTransportProvider transport = new StdioServerTransportProvider(
                McpJsonDefaults.getMapper());

        McpSyncServer server = McpServer.sync(transport)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(new McpSchema.ServerCapabilities.Builder()
                        .tools(true)
                        .build())
                .tools(tools)
                .build();

        // Block until the process is terminated; stdio transport reads stdin in background
        Runtime.getRuntime().addShutdownHook(new Thread(server::closeGracefully));
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            server.closeGracefully();
        }
    }
}
