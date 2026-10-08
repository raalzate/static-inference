package com.extractor.schema;

import com.extractor.model.DependencyGraph;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract strictness on mutated real outputs (TS-004..TS-012, FR-004..FR-007). */
@Tag("schema-contract")
class SchemaNegativeContractTest {

    private static final String ISSUE = "{\"type\":\"CODE_STYLE\",\"severity\":\"INFO\",\"message\":\"m\","
            + "\"line\":1,\"pattern\":\"p\"}";

    private static JsonNode doc(String project, String kind) {
        JsonNode node = JsonDocs.parse(DemoOutputs.of(project).byKind(kind));
        if (SchemaRegistry.DEPENDENCY_GRAPH.equals(kind) && node.at("/components/0/code_issues/0").isMissingNode()) {
            ((ArrayNode) node.at("/components/0/code_issues")).add(JsonDocs.parse(ISSUE));
        }
        return node;
    }

    private static List<String> validate(String kind, JsonNode node) {
        return SchemaRegistry.get().validate(kind, JsonDocs.write(node));
    }

    private static void assertRejectedAt(List<String> errors, String pointer, String token) {
        assertTrue(errors.stream().anyMatch(e -> e.startsWith(pointer) && e.contains(token)),
                "expected error at " + pointer + " naming " + token + ": " + errors);
    }

    @ParameterizedTest(name = "TS-004: removing {1} from {0}[0] is rejected")
    @CsvSource({"components, id", "components, layer", "edges, from", "edges, weight"})
    void removingRequiredFieldIsRejected(String collection, String field) {
        JsonNode node = doc("java-standar", SchemaRegistry.DEPENDENCY_GRAPH);
        ((ObjectNode) node.path(collection).path(0)).remove(field);
        assertRejectedAt(validate(SchemaRegistry.DEPENDENCY_GRAPH, node), "/" + collection + "/0", field);
    }

    @ParameterizedTest(name = "TS-005: {1} = {2} is outside the closed catalogue")
    @CsvSource({
        "dependency-graph, /components/0/layer, Servicio",
        "dependency-graph, /components/0/code_issues/0/severity, FATAL",
        "dependency-graph, /components/0/code_issues/0/type, SMELL",
        "dependency-graph, /meta/source, dotnet",
        "architecture, /proposals/0/viability, Alta-Media",
        "architecture, /proposals/0/legacy_entrypoint/type, grpc"
    })
    void closedCatalogueRejectsUnknownValues(String kind, String pointer, String value) {
        JsonNode node = doc("java-standar", kind);
        JsonDocs.setAt(node, pointer, TextNode.valueOf(value));
        assertRejectedAt(validate(kind, node), pointer, value);
    }

    @ParameterizedTest(name = "TS-006: undeclared property at {1} is rejected")
    @CsvSource({
        "dependency-graph, /components/0",
        "dependency-graph, /edges/0",
        "dependency-graph, /meta",
        "architecture, /proposals/0/metrics",
        "entrypoints, /endpoints/0"
    })
    void undeclaredPropertyIsRejected(String kind, String pointer) {
        JsonNode node = doc("java-standar", kind);
        ((ObjectNode) node.at(pointer)).put("unexpected_field", 1);
        assertRejectedAt(validate(kind, node), pointer, "unexpected_field");
    }

    @ParameterizedTest(name = "TS-007: dynamic map {1} accepts arbitrary keys")
    @CsvSource(delimiter = '|', value = {
        "dependency-graph|/meta/microservice_candidates|\"x\"",
        "dependency-graph|/api_contracts/schemas|{\"type\":\"object\",\"properties\":{}}",
        "architecture|/project_metadata/external_dependencies|\"g:a\"",
        "architecture|/project_metadata/package_dependencies|"
                + "{\"components_count\":0,\"total_dependencies_out\":0,\"depends_on_packages\":[]}",
        "entrypoints|/schemas|{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"String\"}}}"
    })
    void dynamicMapsAcceptArbitraryKeys(String kind, String pointer, String value) {
        JsonNode node = doc("java-standar", kind);
        ((ObjectNode) node.at(pointer)).set("any.Key-123", JsonDocs.parse(value));
        assertEquals(List.of(), validate(kind, node));
    }

    @ParameterizedTest(name = "TS-008/TS-009: open value {2} at {1} is accepted")
    @CsvSource({
        "dependency-graph, /edges/0/type, 'call,use,injection-constructor'",
        "dependency-graph, /components/0/code_issues/0/pattern, BRAND_NEW_PATTERN",
        "entrypoints, /endpoints/0/method, GRPC",
        "architecture, /proposals/0/metrics/tables_source, orm",
        "dependency-graph, /meta/source, roslyn-fallback"
    })
    void openValuesAreAccepted(String kind, String pointer, String value) {
        JsonNode node = doc("legacy-erp", kind);
        JsonDocs.setAt(node, pointer, TextNode.valueOf(value));
        assertEquals(List.of(), validate(kind, node));
    }

    @Test
    @DisplayName("TS-010: Component.interface is deprecated but optional")
    void interfaceIsDeprecated() throws Exception {
        JsonNode common = JsonDocs.parse(Files.readString(SchemaRegistry.get().schemaFile(SchemaRegistry.COMPONENT)));
        assertTrue(common.at("/$defs/Component/properties/interface/deprecated").asBoolean(false));
        ObjectNode component = (ObjectNode) doc("java-standar", SchemaRegistry.DEPENDENCY_GRAPH).at("/components/0");
        assertEquals(List.of(), SchemaRegistry.get().validate(SchemaRegistry.COMPONENT, JsonDocs.write(component)));
        component.remove("interface");
        assertEquals(List.of(), SchemaRegistry.get().validate(SchemaRegistry.COMPONENT, JsonDocs.write(component)));
    }

    @Test
    @DisplayName("TS-011: an empty graph declaring the version is valid")
    void emptyGraphIsValid() {
        JsonNode empty = JsonDocs.mapper().valueToTree(new DependencyGraph(new ArrayList<>(), new ArrayList<>()));
        assertEquals(List.of(), validate(SchemaRegistry.DEPENDENCY_GRAPH, empty));
    }

    @ParameterizedTest(name = "TS-012: legacy_entrypoint {0} is valid")
    @CsvSource({"null", "absent", "internal"})
    void legacyEntrypointVariantsAreValid(String variant) {
        JsonNode node = doc("java-standar", SchemaRegistry.ARCHITECTURE);
        ObjectNode proposal = (ObjectNode) node.at("/proposals/0");
        switch (variant) {
            case "null" -> proposal.set("legacy_entrypoint", NullNode.getInstance());
            case "absent" -> proposal.remove("legacy_entrypoint");
            default -> proposal.set("legacy_entrypoint", JsonDocs.mapper().createObjectNode().put("type", variant));
        }
        assertEquals(List.of(), validate(SchemaRegistry.ARCHITECTURE, node));
    }

    @Test
    @DisplayName("FR-006: an unknown meta.source is rejected even on an otherwise valid graph")
    void unknownSourceRejected() {
        JsonNode node = doc("java-standar", SchemaRegistry.DEPENDENCY_GRAPH);
        JsonDocs.setAt(node, "/meta/source", TextNode.valueOf("javaparser"));
        assertFalse(validate(SchemaRegistry.DEPENDENCY_GRAPH, node).isEmpty());
    }
}
