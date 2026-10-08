package com.extractor.bdd;

import com.extractor.schema.DemoOutputs;
import com.extractor.schema.JsonDocs;
import com.extractor.schema.SchemaRegistry;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Steps shared by several features: choosing a demo project, running the engine, loading a valid document
 * for mutation and asserting validation outcomes against the contract.
 */
public class CommonSteps {

    @Given("the Java demo project {string}")
    public void theJavaDemoProject(String project) {
        assertFalse(DemoOutputs.isDotNet(project), project + " is not a Java demo project");
        World.project = project;
    }

    @Given("the .NET demo project {string}")
    public void theDotNetDemoProject(String project) {
        assertTrue(DemoOutputs.isDotNet(project), project + " is not a .NET demo project");
        ToolAvailability.requireDotnetAnalyzer();
        World.project = project;
    }

    @Given("the demo project {string}")
    public void theDemoProject(String project) {
        if (DemoOutputs.isDotNet(project)) {
            ToolAvailability.requireDotnetAnalyzer();
        }
        World.project = project;
    }

    @When("the engine analyzes it and writes the three artifacts")
    public void theEngineAnalyzesItAndWritesTheThreeArtifacts() throws IOException {
        Path out = Files.createTempDirectory("si-005-artifacts-");
        if (World.collectedAtOptIn) {
            World.artifacts = DemoOutputs.analyzeInto(DemoOutputs.projectDir(World.project), out);
        } else {
            World.artifacts = DemoOutputs.of(World.project);
            DemoOutputs.writeArtifacts(World.artifacts, out);
        }
        World.outDir = out;
    }

    @Then("the dependency graph validates against the dependency-graph contract with no errors")
    public void theDependencyGraphValidates() throws IOException {
        assertArtifactValid(DemoOutputs.GRAPH, SchemaRegistry.DEPENDENCY_GRAPH);
    }

    @Then("the consolidated architecture validates against the architecture contract with no errors")
    public void theConsolidatedArchitectureValidates() throws IOException {
        assertArtifactValid(DemoOutputs.ARCHITECTURE, SchemaRegistry.ARCHITECTURE);
    }

    @Then("the entrypoints validate against the entrypoints contract with no errors")
    public void theEntrypointsValidate() throws IOException {
        assertArtifactValid(DemoOutputs.ENTRYPOINTS, SchemaRegistry.ENTRYPOINTS);
    }

    @Given("a valid dependency graph produced for {string}")
    public void aValidDependencyGraphProducedFor(String project) {
        loadValid(SchemaRegistry.DEPENDENCY_GRAPH, project);
    }

    @Given("a valid consolidated architecture produced for {string}")
    public void aValidConsolidatedArchitectureProducedFor(String project) {
        loadValid(SchemaRegistry.ARCHITECTURE, project);
    }

    @Given("a valid {string} produced for {string}")
    public void aValidDocumentProducedFor(String kind, String project) {
        loadValid(kind, project);
    }

    @Then("^validation against the \"?([a-z-]+)\"? contract (fails|succeeds)$")
    public void validationAgainstTheContract(String kind, String outcome) {
        assertEquals(World.docKind, kind, "scenario validates a different document kind");
        World.errors = SchemaRegistry.get().validate(kind, JsonDocs.write(World.doc));
        if ("fails".equals(outcome)) {
            assertFalse(World.errors.isEmpty(), "expected the " + kind + " contract to reject the document");
        } else {
            assertTrue(World.errors.isEmpty(), "expected the " + kind + " contract to accept: " + World.errors);
        }
    }

    /** Loads a real engine output as the scenario document and checks the precondition "valid". */
    static void loadValid(String kind, String project) {
        if (DemoOutputs.isDotNet(project)) {
            ToolAvailability.requireDotnetAnalyzer();
        }
        World.project = project;
        World.docKind = kind;
        World.doc = JsonDocs.parse(DemoOutputs.of(project).byKind(kind));
        List<String> errors = SchemaRegistry.get().validate(kind, JsonDocs.write(World.doc));
        assertTrue(errors.isEmpty(), "precondition: " + kind + " of " + project + " must be valid: " + errors);
    }

    /** Asserts that some error is located at (or below) {@code pointer} and mentions {@code token}. */
    static void assertErrorNames(String pointer, String token) {
        boolean found = World.errors.stream().anyMatch(e -> errorPointer(e).startsWith(pointer) && e.contains(token));
        assertTrue(found, "no error at " + pointer + " naming '" + token + "': " + World.errors);
    }

    /** JSON Pointer prefix of a formatted {@link SchemaRegistry} error. */
    static String errorPointer(String error) {
        int colon = error.indexOf(": ");
        return colon < 0 ? error : error.substring(0, colon);
    }

    private static void assertArtifactValid(String fileName, String kind) throws IOException {
        String json = Files.readString(World.outDir.resolve(fileName), StandardCharsets.UTF_8);
        List<String> errors = SchemaRegistry.get().validate(kind, json);
        assertTrue(errors.isEmpty(), fileName + " of " + World.project + " violates the " + kind
                + " contract: " + errors);
    }
}
