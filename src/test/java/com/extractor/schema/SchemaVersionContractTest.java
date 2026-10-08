package com.extractor.schema;

import com.extractor.model.DependencyGraph;
import com.extractor.model.SchemaVersion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract version declared in every output (US2, FR-008/FR-010). */
@Tag("schema-contract")
class SchemaVersionContractTest {

    private static final List<String> ARTIFACTS =
            List.of(DemoOutputs.GRAPH, DemoOutputs.ARCHITECTURE, DemoOutputs.ENTRYPOINTS);

    @TempDir
    Path tempDir;

    @ParameterizedTest(name = "TS-013/TS-014: {0} artifacts declare meta.schema_version 1.0.0")
    @ValueSource(strings = {"java-standar", "dotnet-ecommerce"})
    void everyArtifactDeclaresSchemaVersion(String project) {
        assumeAnalyzable(project);
        DemoOutputs.Artifacts artifacts = DemoOutputs.of(project);
        for (String artifact : ARTIFACTS) {
            JsonNode version = JsonDocs.get(JsonDocs.parse(artifacts.byFileName(artifact)), "meta.schema_version");
            assertNotNull(version, artifact + " lacks meta.schema_version");
            assertEquals("1.0.0", version.asText(), artifact);
        }
        assertEquals("1.0.0", SchemaVersion.CURRENT);
        assertEquals(SchemaRegistry.get().version(), SchemaVersion.CURRENT, "schema/VERSION vs SchemaVersion");
    }

    @ParameterizedTest(name = "TS-016: {0} with meta.schema_version {1} is rejected")
    @CsvSource({
        "dependency-graph, removed",
        "dependency-graph, '1.0'",
        "dependency-graph, latest",
        "dependency-graph, number",
        "architecture, removed",
        "entrypoints, removed"
    })
    void missingOrMalformedSchemaVersionIsRejected(String kind, String mutation) {
        JsonNode doc = JsonDocs.parse(DemoOutputs.of("java-standar").byKind(kind));
        switch (mutation) {
            case "removed" -> JsonDocs.removeAt(doc, "/meta/schema_version");
            case "number" -> JsonDocs.setAt(doc, "/meta/schema_version", IntNode.valueOf(1));
            default -> JsonDocs.setAt(doc, "/meta/schema_version", TextNode.valueOf(mutation));
        }
        List<String> errors = SchemaRegistry.get().validate(kind, JsonDocs.write(doc));
        assertFalse(errors.isEmpty(), "contract must reject " + mutation);
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("/meta: ") || e.startsWith("/meta/schema_version: ")),
                "error must point at /meta or /meta/schema_version: " + errors);
    }

    @ParameterizedTest(name = "TS-017: two runs over {0} are byte-identical and carry no collected_at")
    @ValueSource(strings = {"java-standar", "dotnet-ecommerce"})
    void twoRunsAreByteIdentical(String project) throws Exception {
        assumeAnalyzable(project);
        Assumptions.assumeFalse(Boolean.parseBoolean(System.getenv(DependencyGraph.Meta.COLLECTED_AT_OPT_IN)));
        System.clearProperty(DependencyGraph.Meta.COLLECTED_AT_OPT_IN);
        Path a = tempDir.resolve("a");
        Path b = tempDir.resolve("b");
        DemoOutputs.analyzeInto(DemoOutputs.projectDir(project), a);
        DemoOutputs.analyzeInto(DemoOutputs.projectDir(project), b);
        for (String artifact : ARTIFACTS) {
            assertEquals(-1L, Files.mismatch(a.resolve(artifact), b.resolve(artifact)), artifact);
            assertNull(JsonDocs.get(JsonDocs.parse(Files.readString(a.resolve(artifact))), "meta.collected_at"));
        }
    }

    @ParameterizedTest(name = "TS-018: meta.collected_at {0} -> valid={1}")
    @CsvSource({"absent, true", "2026-10-07T12:34:56.789Z, true", "'07/10/2026 12:34', false"})
    void collectedAtIsValidatedOnlyWhenPresent(String state, boolean valid) {
        JsonNode doc = JsonDocs.parse(DemoOutputs.of("java-standar").graph());
        if ("absent".equals(state)) {
            JsonDocs.removeAt(doc, "/meta/collected_at");
        } else {
            JsonDocs.setAt(doc, "/meta/collected_at", TextNode.valueOf(state));
        }
        List<String> errors = SchemaRegistry.get().validate(SchemaRegistry.DEPENDENCY_GRAPH, JsonDocs.write(doc));
        assertEquals(valid, errors.isEmpty(), "errors: " + errors);
    }

    @Test
    @DisplayName("TS-019: opt-in emits a valid ISO 8601 meta.collected_at")
    void optInEmitsIsoTimestamp() throws Exception {
        System.setProperty(DependencyGraph.Meta.COLLECTED_AT_OPT_IN, "true");
        try {
            DemoOutputs.Artifacts artifacts = DemoOutputs.analyzeInto(DemoOutputs.projectDir("java-standar"),
                    tempDir.resolve("optin"));
            JsonNode collectedAt = JsonDocs.get(JsonDocs.parse(artifacts.graph()), "meta.collected_at");
            assertNotNull(collectedAt);
            assertNotNull(OffsetDateTime.parse(collectedAt.asText()));
            assertEquals(List.of(), SchemaRegistry.get().validate(SchemaRegistry.DEPENDENCY_GRAPH,
                    Files.readString(tempDir.resolve("optin").resolve(DemoOutputs.GRAPH), StandardCharsets.UTF_8)));
        } finally {
            System.clearProperty(DependencyGraph.Meta.COLLECTED_AT_OPT_IN);
        }
    }

    static void assumeAnalyzable(String project) {
        if (DemoOutputs.isDotNet(project)) {
            Assumptions.assumeTrue(DemoOutputs.dotNetAnalyzerAvailable(), "dotnet-analyzer not available");
        }
    }
}
