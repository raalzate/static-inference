package com.extractor.bdd;

import com.extractor.model.DependencyGraph;
import com.extractor.schema.DemoOutputs;
import com.extractor.schema.JsonDocs;
import com.extractor.schema.SchemaRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Step definitions of {@code contract-documents.feature} (US1): mutations of real outputs. */
public class ContractDocumentsSteps {

    /**
     * Well-formed values for the dynamic maps of TS-007, keyed by the map's last pointer segment.
     * Each value follows the value schema of that map.
     */
    private static final Map<String, String> MAP_VALUES = Map.of(
            "microservice_candidates", "\"any-cluster\"",
            "schemas", "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"Long\"}}}",
            "external_dependencies", "\"org.example:any-artifact\"",
            "package_dependencies",
            "{\"components_count\":1,\"total_dependencies_out\":0,\"depends_on_packages\":[]}");

    /** A contract-valid code issue used when the target component has none (TS-005/TS-008 setup). */
    private static final String SAMPLE_CODE_ISSUE = "{\"type\":\"CODE_STYLE\",\"severity\":\"INFO\","
            + "\"message\":\"sample issue\",\"line\":1,\"pattern\":\"sample_pattern\"}";

    private JsonNode schemaDocument;
    private JsonNode inspectedDefinition;

    // --- TS-004 ------------------------------------------------------------------------------------

    @When("the field {string} is removed from the first element of {string}")
    public void theFieldIsRemovedFromTheFirstElementOf(String field, String collection) {
        JsonNode first = World.doc.path(collection).path(0);
        assertTrue(first.isObject(), collection + " has no first element");
        assertTrue(first.has(field), "first element of " + collection + " has no " + field);
        ((ObjectNode) first).remove(field);
    }

    @Then("the validation error names the path {string} and the missing field {string}")
    public void theValidationErrorNamesThePathAndTheMissingField(String path, String field) {
        CommonSteps.assertErrorNames(path, field);
    }

    // --- TS-005 / TS-008 / TS-009 --------------------------------------------------------------------

    @When("the value at {string} is replaced with {string}")
    public void theValueAtIsReplacedWith(String pointer, String value) {
        ensureCodeIssueExists(pointer);
        assertFalse(World.doc.at(pointer).isMissingNode(), "nothing to replace at " + pointer);
        JsonDocs.setAt(World.doc, pointer, TextNode.valueOf(value));
    }

    @Then("the validation error names the path {string} and the disallowed value {string}")
    public void theValidationErrorNamesThePathAndTheDisallowedValue(String pointer, String value) {
        CommonSteps.assertErrorNames(pointer, value);
    }

    // --- TS-006 --------------------------------------------------------------------------------------

    @When("an undeclared property {string} is added at {string}")
    public void anUndeclaredPropertyIsAddedAt(String property, String pointer) {
        JsonNode target = World.doc.at(pointer);
        assertTrue(target.isObject(), pointer + " is not an object");
        ((ObjectNode) target).put(property, "unexpected");
    }

    @Then("the validation error names the path {string} and the unknown property {string}")
    public void theValidationErrorNamesThePathAndTheUnknownProperty(String pointer, String property) {
        CommonSteps.assertErrorNames(pointer, property);
    }

    // --- TS-007 --------------------------------------------------------------------------------------

    @When("a new entry with key {string} and a well-formed value is added to the map at {string}")
    public void aNewEntryIsAddedToTheMapAt(String key, String pointer) {
        JsonNode map = World.doc.at(pointer);
        assertTrue(map.isObject(), pointer + " is not a map");
        String name = pointer.substring(pointer.lastIndexOf('/') + 1);
        String value = MAP_VALUES.get(name);
        assertNotNull(value, "no well-formed value known for map " + name);
        ((ObjectNode) map).set(key, JsonDocs.parse(value));
    }

    // --- TS-010 --------------------------------------------------------------------------------------

    @Given("the dependency-graph contract")
    public void theDependencyGraphContract() throws IOException {
        schemaDocument = JsonDocs.parse(Files.readString(
                SchemaRegistry.get().schemaFile(SchemaRegistry.COMPONENT), StandardCharsets.UTF_8));
        JsonNode graph = JsonDocs.parse(Files.readString(
                SchemaRegistry.get().schemaFile(SchemaRegistry.DEPENDENCY_GRAPH), StandardCharsets.UTF_8));
        assertTrue(graph.toString().contains("common.schema.json#/$defs/Component"),
                "dependency-graph contract must reference the Component definition");
    }

    @When("the definition of the component property {string} is inspected")
    public void theDefinitionOfTheComponentPropertyIsInspected(String property) {
        inspectedDefinition = schemaDocument.at("/$defs/Component/properties/" + property);
        assertFalse(inspectedDefinition.isMissingNode(), "Component declares no property " + property);
    }

    @Then("it is marked as deprecated")
    public void itIsMarkedAsDeprecated() {
        assertTrue(inspectedDefinition.path("deprecated").asBoolean(false), "not marked deprecated");
    }

    @Then("a component that includes {string} validates")
    public void aComponentThatIncludesValidates(String property) {
        ObjectNode component = firstJavaComponent();
        component.put(property, component.path("is_interface").asBoolean());
        assertComponentValid(component);
    }

    @Then("a component that omits {string} validates")
    public void aComponentThatOmitsValidates(String property) {
        ObjectNode component = firstJavaComponent();
        component.remove(property);
        assertComponentValid(component);
    }

    // --- TS-011 --------------------------------------------------------------------------------------

    @Given("a dependency graph with no components, no edges and no endpoints that declares the current contract version")
    public void anEmptyDependencyGraph() {
        DependencyGraph graph = new DependencyGraph(new ArrayList<>(), new ArrayList<>());
        World.docKind = SchemaRegistry.DEPENDENCY_GRAPH;
        World.doc = JsonDocs.mapper().valueToTree(graph);
        assertEquals(SchemaRegistry.get().version(), World.doc.at("/meta/schema_version").asText());
    }

    @When("it is validated against the dependency-graph contract")
    public void itIsValidatedAgainstTheDependencyGraphContract() {
        World.errors = SchemaRegistry.get().validate(SchemaRegistry.DEPENDENCY_GRAPH, JsonDocs.write(World.doc));
    }

    @Then("validation succeeds")
    public void validationSucceeds() {
        assertTrue(World.errors.isEmpty(), "expected valid: " + World.errors);
    }

    // --- TS-012 --------------------------------------------------------------------------------------

    @When("the legacy entrypoint of the first proposal is set to null")
    public void theLegacyEntrypointIsSetToNull() {
        firstProposal().set("legacy_entrypoint", NullNode.getInstance());
    }

    @When("the legacy entrypoint of the first proposal is set to absent")
    public void theLegacyEntrypointIsSetToAbsent() {
        firstProposal().remove("legacy_entrypoint");
    }

    @When("the legacy entrypoint of the first proposal is set to an object with only type {string}")
    public void theLegacyEntrypointIsSetToAnObjectWithOnlyType(String type) {
        ObjectNode entry = JsonDocs.mapper().createObjectNode().put("type", type);
        firstProposal().set("legacy_entrypoint", entry);
    }

    // --- helpers -------------------------------------------------------------------------------------

    private static ObjectNode firstProposal() {
        JsonNode proposal = World.doc.path("proposals").path(0);
        assertTrue(proposal.isObject(), "architecture has no proposals");
        return (ObjectNode) proposal;
    }

    private static ObjectNode firstJavaComponent() {
        JsonNode graph = JsonDocs.parse(DemoOutputs.of("java-standar").graph());
        return (ObjectNode) graph.path("components").path(0).deepCopy();
    }

    private static void assertComponentValid(ObjectNode component) {
        List<String> errors = SchemaRegistry.get().validate(SchemaRegistry.COMPONENT, JsonDocs.write(component));
        assertTrue(errors.isEmpty(), "component must validate: " + errors);
    }

    /**
     * Scenario setup for pointers into {@code /components/0/code_issues/0}: when the first component of the
     * real output reports no code issue, a contract-valid one is inserted so the mutation has a target
     * (the document stays valid until the mutation is applied).
     */
    private static void ensureCodeIssueExists(String pointer) {
        String prefix = "/components/0/code_issues/0";
        if (!pointer.startsWith(prefix) || !World.doc.at(prefix).isMissingNode()) {
            return;
        }
        JsonNode issues = World.doc.at("/components/0/code_issues");
        assertTrue(issues.isArray(), "first component has no code_issues array");
        ((com.fasterxml.jackson.databind.node.ArrayNode) issues).add(JsonDocs.parse(SAMPLE_CODE_ISSUE));
        assertTrue(SchemaRegistry.get().validate(World.docKind, JsonDocs.write(World.doc)).isEmpty(),
                "document must remain valid after inserting the sample code issue");
    }
}
