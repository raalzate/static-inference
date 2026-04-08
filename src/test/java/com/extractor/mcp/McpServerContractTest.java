package com.extractor.mcp;

import com.extractor.service.AnalysisService;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for MCP server tool registration.
 * [TS-015] Server starts via command-line argument
 * [TS-016] Agent discovers 5 tools with parameter schemas
 * [TS-018] Single artifact supports both modes
 * [TS-019] Tool listing includes complete schemas
 */
class McpServerContractTest {

    private List<McpServerFeatures.SyncToolSpecification> toolSpecs;

    @BeforeEach
    void setUp() {
        AnalysisService service = new AnalysisService();
        toolSpecs = McpServerMain.createToolSpecs(service);
    }

    @Test
    void serverRegisters5Tools() {
        assertEquals(5, toolSpecs.size(), "MCP server must register exactly 5 tools (FR-011)");
    }

    @Test
    void allToolNamesAreCorrect() {
        Set<String> names = toolSpecs.stream()
                .map(spec -> spec.tool().name())
                .collect(Collectors.toSet());

        assertTrue(names.contains("analyze_project"), "Missing tool: analyze_project (FR-003)");
        assertTrue(names.contains("get_dependency_graph"), "Missing tool: get_dependency_graph (FR-004)");
        assertTrue(names.contains("get_architecture_proposal"), "Missing tool: get_architecture_proposal (FR-005)");
        assertTrue(names.contains("get_api_contracts"), "Missing tool: get_api_contracts (FR-006)");
        assertTrue(names.contains("get_component_metrics"), "Missing tool: get_component_metrics (FR-007)");
    }

    @Test
    void allToolsHaveDescriptions() {
        for (McpServerFeatures.SyncToolSpecification spec : toolSpecs) {
            assertNotNull(spec.tool().description(),
                    "Tool " + spec.tool().name() + " must have a description");
            assertFalse(spec.tool().description().isEmpty(),
                    "Tool " + spec.tool().name() + " description must not be empty");
        }
    }

    @Test
    void allToolsHaveInputSchemas() {
        for (McpServerFeatures.SyncToolSpecification spec : toolSpecs) {
            McpSchema.JsonSchema schema = spec.tool().inputSchema();
            assertNotNull(schema, "Tool " + spec.tool().name() + " must have an input schema");
            assertEquals("object", schema.type(), "Schema type must be 'object'");
            assertNotNull(schema.properties(), "Schema must have properties");
            assertFalse(schema.properties().isEmpty(), "Schema properties must not be empty");
            assertNotNull(schema.required(), "Schema must have required fields");
            assertFalse(schema.required().isEmpty(), "Schema must have at least one required field");
        }
    }

    @Test
    void allToolsHaveCallHandlers() {
        for (McpServerFeatures.SyncToolSpecification spec : toolSpecs) {
            assertNotNull(spec.callHandler(),
                    "Tool " + spec.tool().name() + " must have a call handler");
        }
    }

    @Test
    void analyzeProjectSchemaHasProjectPathRequired() {
        McpServerFeatures.SyncToolSpecification spec = findTool("analyze_project");
        assertTrue(spec.tool().inputSchema().required().contains("projectPath"));
        assertTrue(spec.tool().inputSchema().properties().containsKey("projectPath"));
        assertTrue(spec.tool().inputSchema().properties().containsKey("outputDir"));
    }

    @Test
    void getArchitectureProposalSchemaHasMinViabilityOptional() {
        McpServerFeatures.SyncToolSpecification spec = findTool("get_architecture_proposal");
        assertTrue(spec.tool().inputSchema().required().contains("projectPath"));
        assertFalse(spec.tool().inputSchema().required().contains("minViability"),
                "minViability should be optional");
        assertTrue(spec.tool().inputSchema().properties().containsKey("minViability"));
    }

    @Test
    void getComponentMetricsSchemaRequiresBothParams() {
        McpServerFeatures.SyncToolSpecification spec = findTool("get_component_metrics");
        List<String> required = spec.tool().inputSchema().required();
        assertTrue(required.contains("projectPath"), "projectPath must be required");
        assertTrue(required.contains("componentId"), "componentId must be required");
    }

    private McpServerFeatures.SyncToolSpecification findTool(String name) {
        return toolSpecs.stream()
                .filter(s -> s.tool().name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not found: " + name));
    }
}
