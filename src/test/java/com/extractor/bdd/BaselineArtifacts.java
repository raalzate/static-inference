package com.extractor.bdd;

import com.extractor.schema.DemoOutputs;
import com.extractor.schema.JsonDocs;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Access to the v1.1.0 baseline artifacts ({@code src/test/resources/baseline-v1.1.0/<project>/}) and to the
 * removal of the contract metadata added by 005 (TS-020, FR-011).
 */
public final class BaselineArtifacts {

    /** Placeholder for the repository root inside the stored baselines. */
    public static final String REPO_PLACEHOLDER = "${REPO}";

    private BaselineArtifacts() {
    }

    /** Loads the three baseline artifacts with the placeholder replaced by the hermetic demo root. */
    public static Map<String, String> load(String project) throws IOException {
        Path dir = DemoOutputs.repoRoot().resolve("src/test/resources/baseline-v1.1.0").resolve(project);
        String repo = DemoOutputs.demoRoot().toString();
        Map<String, String> out = new LinkedHashMap<>();
        for (String artifact : List.of(DemoOutputs.GRAPH, DemoOutputs.ARCHITECTURE, DemoOutputs.ENTRYPOINTS)) {
            out.put(artifact, Files.readString(dir.resolve(artifact), StandardCharsets.UTF_8)
                    .replace(REPO_PLACEHOLDER, repo));
        }
        return out;
    }

    /**
     * Removes {@code versionPath} (e.g. {@code meta.schema_version}) from the graph and every newly added
     * {@code metaName} object (architecture root, entrypoints root, graph {@code api_contracts}), then
     * re-serializes with the CLI mapper.
     */
    public static Map<String, String> stripContractMetadata(Map<String, String> artifacts, String versionPath,
                                                            String metaName) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : artifacts.entrySet()) {
            JsonNode root = JsonDocs.parse(e.getValue());
            switch (e.getKey()) {
                case DemoOutputs.GRAPH -> {
                    JsonDocs.removeAt(root, JsonDocs.pointer(versionPath));
                    JsonDocs.removeAt(root, "/api_contracts/" + metaName);
                }
                case DemoOutputs.ARCHITECTURE, DemoOutputs.ENTRYPOINTS -> JsonDocs.removeAt(root, "/" + metaName);
                default -> throw new IllegalArgumentException(e.getKey());
            }
            out.put(e.getKey(), JsonDocs.write(root));
        }
        return out;
    }
}
