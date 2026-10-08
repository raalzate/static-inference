package com.extractor.bdd;

import com.extractor.MicroserviceInferenceMain;
import com.extractor.schema.DemoOutputs;
import com.extractor.schema.JsonDocs;
import com.extractor.mcp.McpCalls;
import com.extractor.schema.SchemaRegistry;
import com.extractor.service.AnalysisService;
import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Step definitions of {@code mcp-responses.feature} (US3). Handlers are invoked as the MCP server does. */
public class McpResponsesSteps {

    /** Response contract per tool (FR-002). */
    private static final Map<String, String> CONTRACTS = Map.of(
            "analyze_project", "mcp/analyze_project.response",
            "get_dependency_graph", "mcp/get_dependency_graph.response",
            "get_architecture_proposal", "mcp/get_architecture_proposal.response",
            "get_api_contracts", "mcp/get_api_contracts.response",
            "get_component_metrics", "mcp/get_component_metrics.response");

    /** Root keywords a pure-reference response contract may carry besides {@code $ref}. */
    private static final Set<String> ANNOTATION_KEYWORDS = Set.of("$schema", "$id", "$ref", "title", "description");

    private McpCalls mcp;
    private Path projectDir;
    private McpCalls.Response response;
    private Path cliOut;
    private final Map<String, String> mcpDocuments = new LinkedHashMap<>();
    private JsonNode responseContract;
    private String responseContractName;

    @Given("the MCP server is running over the Java demo project {string}")
    public void theMcpServerIsRunningOver(String project) {
        mcp = new McpCalls(new AnalysisService());
        assertEquals(5, mcp.toolCount(), "MCP server must register the 5 tools");
        projectDir = DemoOutputs.projectDir(project);
        World.project = project;
    }

    @When("the tool {string} is invoked successfully with a temporary output directory")
    public void invokedWithATemporaryOutputDirectory(String tool) throws IOException {
        Path out = Files.createTempDirectory("si-005-mcp-out-");
        invoke(tool, Map.of("projectPath", projectDir.toString(), "outputDir", out.toString()));
    }

    @When("the tool {string} is invoked successfully with the project path")
    public void invokedWithTheProjectPath(String tool) {
        invoke(tool, Map.of("projectPath", projectDir.toString()));
    }

    @When("the tool {string} is invoked successfully with the id of the first component of the graph")
    public void invokedSuccessfullyWithTheFirstComponentId(String tool) {
        invokedWithTheFirstComponentId(tool);
    }

    @When("the tool {string} is invoked with the id of the first component of the graph")
    public void invokedWithTheFirstComponentId(String tool) {
        String id = JsonDocs.parse(DemoOutputs.of(World.project).graph()).at("/components/0/id").asText();
        assertFalse(id.isEmpty(), "graph has no components");
        invoke(tool, Map.of("projectPath", projectDir.toString(), "componentId", id));
    }

    @When("the tool {string} is invoked with minimum viability {string}")
    public void invokedWithMinimumViability(String tool, String level) {
        invoke(tool, Map.of("projectPath", projectDir.toString(), "minViability", level));
        assertFalse(response.error(), "tool returned an error: " + response.text());
    }

    @Then("the response is not marked as error")
    public void theResponseIsNotMarkedAsError() {
        assertFalse(response.error(), "tool returned an error: " + response.text());
    }

    @Then("the response text validates against the {string} contract with no errors")
    public void theResponseTextValidates(String contract) {
        assertValid(contract, response.text());
    }

    @Then("the response validates against the {string} contract with no errors")
    public void theResponseValidates(String contract) {
        assertValid(contract, response.text());
    }

    @Then("the response validates against the Component definition of the common contract")
    public void theResponseValidatesAgainstTheComponentDefinition() {
        assertFalse(response.error(), response.text());
        assertValid(SchemaRegistry.COMPONENT, response.text());
    }

    // --- TS-024 --------------------------------------------------------------------------------------

    @Given("the CLI has produced the three artifacts for the same project")
    public void theCliHasProducedTheThreeArtifacts() throws IOException {
        cliOut = Files.createTempDirectory("si-005-cli-");
        MicroserviceInferenceMain.main(new String[] {projectDir.toString(), cliOut.resolve("output.json").toString()});
        for (String artifact : List.of(DemoOutputs.GRAPH, DemoOutputs.ARCHITECTURE, DemoOutputs.ENTRYPOINTS)) {
            assertTrue(Files.isRegularFile(cliOut.resolve(artifact)), "CLI did not write " + artifact);
        }
    }

    @When("the MCP tools return the dependency graph, the architecture proposal and the API contracts")
    public void theMcpToolsReturnTheThreeDocuments() {
        for (String tool : List.of("get_dependency_graph", "get_architecture_proposal", "get_api_contracts")) {
            invoke(tool, Map.of("projectPath", projectDir.toString()));
            assertFalse(response.error(), tool + ": " + response.text());
            mcpDocuments.put(tool, response.text());
        }
    }

    @Then("each CLI artifact validates against its document contract")
    public void eachCliArtifactValidates() throws IOException {
        assertValid(SchemaRegistry.DEPENDENCY_GRAPH, cli(DemoOutputs.GRAPH));
        assertValid(SchemaRegistry.ARCHITECTURE, cli(DemoOutputs.ARCHITECTURE));
        assertValid(SchemaRegistry.ENTRYPOINTS, cli(DemoOutputs.ENTRYPOINTS));
    }

    @Then("each MCP response validates against its response contract")
    public void eachMcpResponseValidates() {
        mcpDocuments.forEach((tool, text) -> assertValid(CONTRACTS.get(tool), text));
    }

    @Then("each CLI artifact is equivalent to the corresponding MCP response")
    public void eachCliArtifactIsEquivalent() throws IOException {
        assertEquals(JsonDocs.parse(cli(DemoOutputs.GRAPH)), JsonDocs.parse(mcpDocuments.get("get_dependency_graph")));
        assertEquals(JsonDocs.parse(cli(DemoOutputs.ARCHITECTURE)),
                JsonDocs.parse(mcpDocuments.get("get_architecture_proposal")));
        assertEquals(JsonDocs.parse(cli(DemoOutputs.ENTRYPOINTS)), JsonDocs.parse(mcpDocuments.get("get_api_contracts")));
    }

    // --- TS-025 --------------------------------------------------------------------------------------

    @Given("the response contract {string}")
    public void theResponseContract(String contract) throws IOException {
        responseContractName = contract;
        responseContract = JsonDocs.parse(Files.readString(SchemaRegistry.get().schemaFile(contract),
                StandardCharsets.UTF_8));
    }

    @When("its root definition is resolved")
    public void itsRootDefinitionIsResolved() {
        assertTrue(responseContract.has("$ref"), responseContractName + " has no root $ref");
    }

    @Then("it is a reference to {string}")
    public void itIsAReferenceTo(String target) {
        String expected = switch (target) {
            case "dependency-graph contract" -> SchemaRegistry.get().baseIri() + "dependency-graph.schema.json";
            case "architecture contract" -> SchemaRegistry.get().baseIri() + "architecture.schema.json";
            case "entrypoints contract" -> SchemaRegistry.get().baseIri() + "entrypoints.schema.json";
            case "Component definition of the common contract" ->
                    SchemaRegistry.get().baseIri() + "common.schema.json#/$defs/Component";
            default -> throw new IllegalArgumentException("Unknown target " + target);
        };
        String id = responseContract.path("$id").asText();
        String resolved = java.net.URI.create(id).resolve(responseContract.path("$ref").asText()).toString();
        assertEquals(expected, resolved, responseContractName + " $ref");
    }

    @Then("it declares no properties of its own")
    public void itDeclaresNoPropertiesOfItsOwn() {
        List<String> keywords = JsonDocs.fieldNames(responseContract);
        assertTrue(ANNOTATION_KEYWORDS.containsAll(keywords), responseContractName + " declares " + keywords);
    }

    // --- TS-026 / TS-027 -----------------------------------------------------------------------------

    @Then("the response contains exactly the properties {string}, {string}, {string}, {string}, {string} and {string}")
    public void theResponseContainsExactlyTheProperties(String a, String b, String c, String d, String e, String f) {
        List<String> actual = JsonDocs.fieldNames(JsonDocs.parse(response.text()));
        assertEquals(6, actual.size(), "properties: " + actual);
        assertEquals(Set.of(a, b, c, d, e, f), Set.copyOf(actual), "properties: " + actual);
    }

    @Then("{string} lists exactly three files ending in {string}, {string} and {string}")
    public void listsExactlyThreeFilesEndingIn(String field, String first, String second, String third) {
        JsonNode files = JsonDocs.parse(response.text()).path(field);
        assertEquals(3, files.size(), field + " = " + files);
        assertTrue(files.get(0).asText().endsWith(first), files.toString());
        assertTrue(files.get(1).asText().endsWith(second), files.toString());
        assertTrue(files.get(2).asText().endsWith(third), files.toString());
    }

    @Then("the response does not contain {string}")
    public void theResponseDoesNotContain(String property) {
        JsonNode root = JsonDocs.parse(response.text());
        assertFalse(root.has(property), "response contains " + property);
    }

    private void invoke(String tool, Map<String, Object> arguments) {
        response = mcp.call(tool, arguments);
        assertNotNull(response.text());
    }

    private String cli(String artifact) throws IOException {
        return Files.readString(cliOut.resolve(artifact), StandardCharsets.UTF_8);
    }

    private static void assertValid(String contract, String json) {
        List<String> errors = SchemaRegistry.get().validate(contract, json);
        assertTrue(errors.isEmpty(), "response violates " + contract + ": " + errors);
    }
}
