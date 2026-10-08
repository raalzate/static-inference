package com.extractor.schema;

import com.extractor.bdd.BaselineArtifacts;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** TS-020 / FR-011: adding the contract version changes nothing else in the output. */
@Tag("schema-contract")
class BaselineParityTest {

    @ParameterizedTest(name = "TS-020: {0} equals v1.1.0 once the contract metadata is removed")
    @ValueSource(strings = {"java-standar", "dotnet-ecommerce"})
    void outputMatchesBaselineExceptContractMetadata(String project) throws Exception {
        SchemaVersionContractTest.assumeAnalyzable(project);
        Map<String, String> baseline = BaselineArtifacts.load(project);
        DemoOutputs.Artifacts artifacts = DemoOutputs.of(project);
        Map<String, String> current = new LinkedHashMap<>();
        for (String artifact : List.of(DemoOutputs.GRAPH, DemoOutputs.ARCHITECTURE, DemoOutputs.ENTRYPOINTS)) {
            current.put(artifact, artifacts.byFileName(artifact));
        }
        Map<String, String> stripped = BaselineArtifacts.stripContractMetadata(current, "meta.schema_version", "meta");
        for (String artifact : baseline.keySet()) {
            assertEquals(baseline.get(artifact), stripped.get(artifact), artifact + " of " + project);
        }
    }
}
