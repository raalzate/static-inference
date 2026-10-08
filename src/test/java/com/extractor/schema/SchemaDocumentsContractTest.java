package com.extractor.schema;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TS-001 / TS-002 (FR-001, FR-003, FR-012, FR-013, SC-001): the three artifacts of every demo project
 * validate against the formal contract. Samples are left in {@code target/schema-samples/java/} for the
 * generated TypeScript validators (TS-031).
 */
@Tag("schema-contract")
class SchemaDocumentsContractTest {

    static Stream<Arguments> artifacts() {
        return Stream.concat(DemoOutputs.JAVA_PROJECTS.stream(), DemoOutputs.DOTNET_PROJECTS.stream())
                .flatMap(project -> Stream.of(
                        Arguments.of(project, DemoOutputs.GRAPH, SchemaRegistry.DEPENDENCY_GRAPH),
                        Arguments.of(project, DemoOutputs.ARCHITECTURE, SchemaRegistry.ARCHITECTURE),
                        Arguments.of(project, DemoOutputs.ENTRYPOINTS, SchemaRegistry.ENTRYPOINTS)));
    }

    @ParameterizedTest(name = "TS-001/TS-002: {0} {1} satisfies the {2} contract")
    @MethodSource("artifacts")
    void demoArtifactValidates(String project, String artifact, String kind) {
        SchemaVersionContractTest.assumeAnalyzable(project);
        String json = DemoOutputs.of(project).byFileName(artifact);
        List<String> errors = SchemaRegistry.get().validate(kind, json);
        assertEquals(List.of(), errors, project + " " + artifact);
    }
}
