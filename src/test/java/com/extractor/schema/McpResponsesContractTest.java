package com.extractor.schema;

import com.extractor.mcp.McpCalls;
import com.extractor.service.AnalysisService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TS-022, TS-023, TS-026, TS-027 (FR-002, FR-014, SC-002): MCP responses satisfy their contracts. */
@Tag("schema-contract")
class McpResponsesContractTest {

    private static McpCalls mcp;
    private static String project;

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void setUp() {
        mcp = new McpCalls(new AnalysisService());
        project = DemoOutputs.projectDir("java-standar").toString();
    }

    private static McpCalls.Response ok(String tool, Map<String, Object> args) {
        McpCalls.Response response = mcp.call(tool, args);
        assertFalse(response.error(), tool + ": " + response.text());
        return response;
    }

    private static void assertValid(String contract, String json) {
        assertEquals(List.of(), SchemaRegistry.get().validate(contract, json), contract);
    }

    @ParameterizedTest(name = "TS-022: {0} response satisfies its contract")
    @ValueSource(strings = {"get_dependency_graph", "get_architecture_proposal", "get_api_contracts"})
    void documentToolsSatisfyContract(String tool) {
        assertValid("mcp/" + tool + ".response", ok(tool, Map.of("projectPath", project)).text());
    }

    @Test
    @DisplayName("TS-022/TS-026: analyze_project keeps its shape and satisfies its contract")
    void analyzeProjectSatisfiesContract() {
        String text = ok("analyze_project", Map.of("projectPath", project, "outputDir", tempDir.toString())).text();
        assertValid("mcp/analyze_project.response", text);
        JsonNode root = JsonDocs.parse(text);
        assertEquals(java.util.Set.of("componentCount", "edgeCount", "proposalCount", "endpointCount",
                "filesGenerated", "summary"), java.util.Set.copyOf(JsonDocs.fieldNames(root)));
        assertEquals(6, JsonDocs.fieldNames(root).size());
        assertEquals(3, root.path("filesGenerated").size());
        assertFalse(root.has("meta"));
    }

    @Test
    @DisplayName("TS-022/TS-027: get_component_metrics returns a bare Component")
    void componentMetricsSatisfiesContract() {
        String id = JsonDocs.parse(DemoOutputs.of("java-standar").graph()).at("/components/0/id").asText();
        String text = ok("get_component_metrics", Map.of("projectPath", project, "componentId", id)).text();
        assertValid("mcp/get_component_metrics.response", text);
        assertValid(SchemaRegistry.COMPONENT, text);
        assertFalse(JsonDocs.parse(text).has("meta"));
    }

    @ParameterizedTest(name = "TS-023: get_architecture_proposal minViability={0} satisfies its contract")
    @ValueSource(strings = {"Alta", "Media", "Baja"})
    void filteredArchitectureSatisfiesContract(String level) {
        String text = ok("get_architecture_proposal", Map.of("projectPath", project, "minViability", level)).text();
        assertValid("mcp/get_architecture_proposal.response", text);
        assertTrue(JsonDocs.parse(text).has("meta"));
    }
}
