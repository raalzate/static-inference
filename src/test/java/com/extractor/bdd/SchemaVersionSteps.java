package com.extractor.bdd;

import com.extractor.analyzer.DotNetBridgeAnalyzer;
import com.extractor.analyzer.DotNetToolLocator;
import com.extractor.model.DependencyGraph;
import com.extractor.schema.DemoOutputs;
import com.extractor.schema.JsonDocs;
import com.extractor.schema.SchemaRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Step definitions of {@code schema-version.feature} (US2). */
public class SchemaVersionSteps {

    private static final List<String> ARTIFACTS =
            List.of(DemoOutputs.GRAPH, DemoOutputs.ARCHITECTURE, DemoOutputs.ENTRYPOINTS);

    private Path secondRun;
    private Map<String, String> baseline;
    private Map<String, String> current;
    private String fakeDotnetVersion;
    private boolean fakeVersionAbsent;
    private DependencyGraph processedGraph;
    private List<String> processedWarnings;

    // --- TS-013 / TS-014 -------------------------------------------------------------------------

    @Then("the artifact {string} contains {string} equal to {string}")
    public void theArtifactContainsEqualTo(String artifact, String path, String expected) throws IOException {
        JsonNode root = JsonDocs.parse(Files.readString(World.outDir.resolve(artifact), StandardCharsets.UTF_8));
        JsonNode value = JsonDocs.get(root, path);
        assertNotNull(value, artifact + " has no " + path);
        assertEquals(expected, value.asText(), artifact + " " + path);
    }

    // --- TS-015 (and TS-003, shared raw .NET steps live in ContractDocumentsSteps) -----------------

    @Then("the emitted graph contains {string} equal to {string}")
    public void theEmittedGraphContainsEqualTo(String path, String expected) {
        JsonNode value = JsonDocs.get(JsonDocs.parse(World.rawDotnetGraph), path);
        assertNotNull(value, "raw .NET graph has no " + path);
        assertEquals(expected, value.asText());
    }

    @Then("{string} is serialized immediately after {string}")
    public void isSerializedImmediatelyAfter(String path, String previous) {
        String parent = path.substring(0, path.lastIndexOf('.'));
        assertEquals(parent, previous.substring(0, previous.lastIndexOf('.')), "different parents");
        JsonNode object = JsonDocs.get(JsonDocs.parse(World.rawDotnetGraph), parent);
        assertNotNull(object, "raw .NET graph has no " + parent);
        List<String> names = JsonDocs.fieldNames(object);
        int prevIndex = names.indexOf(previous.substring(previous.lastIndexOf('.') + 1));
        int index = names.indexOf(path.substring(path.lastIndexOf('.') + 1));
        assertTrue(prevIndex >= 0 && index == prevIndex + 1, "order of " + parent + " is " + names);
    }

    // --- TS-016 / TS-018: dotted-path mutations of the scenario document -------------------------

    @When("{string} is removed")
    public void isRemoved(String path) {
        JsonDocs.removeAt(World.doc, JsonDocs.pointer(path));
    }

    @When("{string} is absent")
    public void isAbsent(String path) {
        JsonDocs.removeAt(World.doc, JsonDocs.pointer(path));
        assertNull(JsonDocs.get(World.doc, path));
    }

    @When("{string} is set to {string}")
    public void isSetTo(String path, String value) {
        JsonDocs.setAt(World.doc, JsonDocs.pointer(path), TextNode.valueOf(value));
    }

    @When("{string} is set to the number {int}")
    public void isSetToTheNumber(String path, int value) {
        JsonDocs.setAt(World.doc, JsonDocs.pointer(path), IntNode.valueOf(value));
    }

    @Then("the validation error names the path {string} or {string}")
    public void theValidationErrorNamesThePathOr(String first, String second) {
        boolean found = World.errors.stream()
                .map(CommonSteps::errorPointer)
                .anyMatch(p -> p.equals(first) || p.equals(second));
        assertTrue(found, "no error located at " + first + " or " + second + ": " + World.errors);
    }

    // --- TS-017 / TS-019: timestamp opt-in and determinism -----------------------------------------

    @Given("the timestamp opt-in {string} is not enabled")
    public void theTimestampOptInIsNotEnabled(String name) {
        assertEquals(DependencyGraph.Meta.COLLECTED_AT_OPT_IN, name);
        System.clearProperty(name);
        Assumptions.assumeFalse(Boolean.parseBoolean(System.getenv(name)),
                name + " is enabled in the environment; determinism scenario not applicable");
        World.collectedAtOptIn = false;
    }

    @Given("the timestamp opt-in {string} is enabled")
    public void theTimestampOptInIsEnabled(String name) {
        assertEquals(DependencyGraph.Meta.COLLECTED_AT_OPT_IN, name);
        System.setProperty(name, "true");
        World.collectedAtOptIn = true;
    }

    @When("the engine analyzes the project twice into two different output directories")
    public void theEngineAnalyzesTheProjectTwice() throws IOException {
        Path first = Files.createTempDirectory("si-005-run1-");
        secondRun = Files.createTempDirectory("si-005-run2-");
        DemoOutputs.analyzeInto(DemoOutputs.projectDir(World.project), first);
        DemoOutputs.analyzeInto(DemoOutputs.projectDir(World.project), secondRun);
        World.outDir = first;
    }

    @Then("the three artifacts of both runs are byte-identical")
    public void theThreeArtifactsOfBothRunsAreByteIdentical() throws IOException {
        for (String artifact : ARTIFACTS) {
            assertEquals(-1L, Files.mismatch(World.outDir.resolve(artifact), secondRun.resolve(artifact)),
                    artifact + " differs between two runs of " + World.project);
        }
    }

    @Then("no artifact contains {string}")
    public void noArtifactContains(String path) throws IOException {
        for (Path dir : List.of(World.outDir, secondRun)) {
            for (String artifact : ARTIFACTS) {
                JsonNode root = JsonDocs.parse(Files.readString(dir.resolve(artifact), StandardCharsets.UTF_8));
                assertNull(JsonDocs.get(root, path), artifact + " contains " + path);
            }
        }
    }

    @Then("the dependency graph contains {string} as an ISO 8601 date-time")
    public void theDependencyGraphContainsAsAnIsoDateTime(String path) throws IOException {
        JsonNode root = JsonDocs.parse(Files.readString(World.outDir.resolve(DemoOutputs.GRAPH),
                StandardCharsets.UTF_8));
        JsonNode value = JsonDocs.get(root, path);
        assertNotNull(value, "graph has no " + path);
        assertNotNull(OffsetDateTime.parse(value.asText()), path + " is not ISO 8601");
    }

    // --- TS-020: no regression against v1.1.0 ------------------------------------------------------

    @Given("the baseline artifacts produced by release v1.1.0 for {string}")
    public void theBaselineArtifactsProducedByRelease(String project) throws IOException {
        if (DemoOutputs.isDotNet(project)) {
            ToolAvailability.requireDotnetAnalyzer();
        }
        World.project = project;
        baseline = BaselineArtifacts.load(project);
    }

    @When("the current engine produces the same artifacts")
    public void theCurrentEngineProducesTheSameArtifacts() {
        DemoOutputs.Artifacts artifacts = DemoOutputs.of(World.project);
        current = new java.util.LinkedHashMap<>();
        for (String artifact : ARTIFACTS) {
            current.put(artifact, artifacts.byFileName(artifact));
        }
    }

    @When("{string} and the newly added {string} objects are removed from the current artifacts")
    public void andTheNewlyAddedObjectsAreRemoved(String versionPath, String metaName) {
        current = BaselineArtifacts.stripContractMetadata(current, versionPath, metaName);
    }

    @Then("the current artifacts are byte-identical to the baseline artifacts")
    public void theCurrentArtifactsAreByteIdenticalToTheBaseline() {
        for (String artifact : ARTIFACTS) {
            assertEquals(baseline.get(artifact), current.get(artifact),
                    artifact + " of " + World.project + " differs from v1.1.0");
        }
    }

    // --- TS-021: bridge warns on a different .NET contract version --------------------------------

    @Given("the .NET analyzer returns a graph whose {string} is absent")
    public void theDotNetAnalyzerReturnsAGraphWhoseIsAbsent(String path) {
        assertEquals("meta.schema_version", path);
        fakeVersionAbsent = true;
    }

    @Given("the .NET analyzer returns a graph whose {string} is {string}")
    public void theDotNetAnalyzerReturnsAGraphWhoseIs(String path, String version) {
        assertEquals("meta.schema_version", path);
        fakeDotnetVersion = version;
    }

    @When("the engine processes that graph")
    public void theEngineProcessesThatGraph() throws Exception {
        Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().startsWith("windows"),
                "fake dotnet-analyzer is a POSIX shell script");
        Path dir = Files.createTempDirectory("si-005-fake-dotnet-");
        ObjectNode meta = JsonDocs.mapper().createObjectNode().put("source", "roslyn");
        if (!fakeVersionAbsent) {
            meta.put("schema_version", fakeDotnetVersion);
        }
        meta.set("microservice_candidates", JsonDocs.mapper().createObjectNode());
        ObjectNode graph = JsonDocs.mapper().createObjectNode();
        graph.set("components", JsonDocs.mapper().createArrayNode());
        graph.set("edges", JsonDocs.mapper().createArrayNode());
        graph.set("api_contracts", JsonDocs.parse("{\"endpoints\":[],\"schemas\":{}}"));
        graph.set("meta", meta);
        Path payload = dir.resolve("graph.json");
        Files.writeString(payload, JsonDocs.write(graph), StandardCharsets.UTF_8);
        Path script = dir.resolve("dotnet-analyzer");
        Files.writeString(script, "#!/bin/sh\ncat '" + payload + "'\n", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));

        DotNetBridgeAnalyzer bridge = new DotNetBridgeAnalyzer(new DotNetToolLocator(dir));
        processedGraph = bridge.analyzeProject(dir);
        processedWarnings = new ArrayList<>(bridge.getWarnings());
    }

    @Then("the analysis warnings contain a message naming absent and {string}")
    public void theAnalysisWarningsContainAMessageNamingAbsentAnd(String engineVersion) {
        assertWarning("absent", engineVersion);
    }

    @Then("the analysis warnings contain a message naming {string} and {string}")
    public void theAnalysisWarningsContainAMessageNaming(String dotnetVersion, String engineVersion) {
        assertWarning(dotnetVersion, engineVersion);
    }

    @Then("the engine artifacts declare {string} equal to {string}")
    public void theEngineArtifactsDeclareEqualTo(String path, String expected) {
        JsonNode graph = JsonDocs.parse(JsonDocs.write(JsonDocs.mapper().valueToTree(processedGraph)));
        JsonNode version = JsonDocs.get(graph, path);
        assertNotNull(version, "graph has no " + path);
        assertEquals(expected, version.asText());
        JsonNode entrypoints = JsonDocs.mapper().valueToTree(processedGraph.getApiContracts());
        assertEquals(expected, JsonDocs.get(entrypoints, path).asText(), "entrypoints " + path);
        assertTrue(SchemaRegistry.get().validate(SchemaRegistry.DEPENDENCY_GRAPH, JsonDocs.write(graph)).isEmpty(),
                "processed graph must satisfy the contract");
    }

    private void assertWarning(String dotnetVersion, String engineVersion) {
        assertFalse(processedWarnings.isEmpty(), "no analysis warnings");
        assertTrue(processedWarnings.stream().anyMatch(w -> w.contains(dotnetVersion) && w.contains(engineVersion)),
                "no warning naming " + dotnetVersion + " and " + engineVersion + ": " + processedWarnings);
    }
}
