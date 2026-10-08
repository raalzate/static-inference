package com.extractor.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Root data model for the dependency graph JSON output.
 */
public class DependencyGraph {

    @JsonProperty("components")
    private List<Component> components;

    @JsonProperty("edges")
    private List<Edge> edges;

    @JsonProperty("api_contracts")
    private ApiContracts apiContracts = new ApiContracts();

    @JsonProperty("meta")
    private Meta meta;

    public DependencyGraph() {
        this.meta = new Meta();
    }

    public DependencyGraph(List<Component> components, List<Edge> edges) {
        this.components = components;
        this.edges = edges;
        this.meta = new Meta();
    }

    // Getters and setters
    public List<Component> getComponents() {
        return components;
    }

    public void setComponents(List<Component> components) {
        this.components = components;
    }

    public List<Edge> getEdges() {
        return edges;
    }

    public void setEdges(List<Edge> edges) {
        this.edges = edges;
    }

    public ApiContracts getApiContracts() {
        return apiContracts;
    }

    public void setApiContracts(ApiContracts apiContracts) {
        this.apiContracts = apiContracts;
    }

    public Meta getMeta() {
        return meta;
    }

    public void setMeta(Meta meta) {
        this.meta = meta;
    }

    /**
     * Endpoints and schemas ({@code output_entrypoints.json}, embedded
     * as {@code api_contracts}). {@code meta} (005, FR-008) goes first.
     */
    @JsonPropertyOrder({"meta", "endpoints", "schemas"})
    public static class ApiContracts {
        /** Contract metadata (005, FR-008). */
        @JsonProperty("meta")
        private DocumentMeta meta = new DocumentMeta();

        @JsonProperty("endpoints")
        private List<ApiEndpoint> endpoints = new ArrayList<>();

        @JsonProperty("schemas")
        private Map<String, ApiSchema> schemas = new HashMap<>();

        /**
         * Returns the contract metadata.
         *
         * @return the contract metadata
         */
        public DocumentMeta getMeta() {
            return meta;
        }

        /**
         * Sets the contract metadata.
         *
         * @param documentMeta the contract metadata
         */
        public void setMeta(final DocumentMeta documentMeta) {
            this.meta = documentMeta;
        }

        public List<ApiEndpoint> getEndpoints() {
            return endpoints;
        }

        public void setEndpoints(List<ApiEndpoint> endpoints) {
            this.endpoints = endpoints;
        }

        public Map<String, ApiSchema> getSchemas() {
            return schemas;
        }

        public void setSchemas(Map<String, ApiSchema> schemas) {
            this.schemas = schemas;
        }
    }

    /**
     * Metadata for the dependency graph. {@code schema_version}
     * (005, FR-008) is serialized right after {@code source}; the rest
     * of the order is unchanged (FR-011).
     */
    @JsonPropertyOrder({"source", "schema_version", "collected_at",
            "microservice_candidates", "dependency_accuracy",
            "decomposition_accuracy"})
    public static class Meta {
        /** System property / environment variable enabling {@code collected_at}. */
        public static final String COLLECTED_AT_OPT_IN = "STATIC_INFERENCE_COLLECTED_AT";

        @JsonProperty("source")
        private String source = "spoon";

        /** Contract version (005, FR-008). */
        @JsonProperty("schema_version")
        private String schemaVersion = SchemaVersion.CURRENT;

        @JsonProperty("collected_at")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String collectedAt;

        @JsonProperty("microservice_candidates")
        private Map<String, String> microserviceCandidates;

        @JsonProperty("dependency_accuracy")
        private Map<String, Object> dependencyAccuracy;

        @JsonProperty("decomposition_accuracy")
        private Map<String, Object> decompositionAccuracy;

        public Meta() {
            // Opt-in only: wall-clock timestamps break deterministic output (principle IV, BUG-001)
            this.collectedAt = collectedAtOptIn() ? Instant.now().toString() : null;
            this.microserviceCandidates = new HashMap<>();
        }

        static boolean collectedAtOptIn() {
            String value = System.getProperty(COLLECTED_AT_OPT_IN);
            if (value == null) {
                value = System.getenv(COLLECTED_AT_OPT_IN);
            }
            return Boolean.parseBoolean(value);
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        /**
         * Returns the contract version.
         *
         * @return the contract version
         */
        public String getSchemaVersion() {
            return schemaVersion;
        }

        /**
         * Sets the contract version.
         *
         * @param version the contract version
         */
        public void setSchemaVersion(final String version) {
            this.schemaVersion = version;
        }

        public String getCollectedAt() {
            return collectedAt;
        }

        public void setCollectedAt(String collectedAt) {
            this.collectedAt = collectedAt;
        }

        public Map<String, String> getMicroserviceCandidates() {
            return microserviceCandidates;
        }

        public void setMicroserviceCandidates(Map<String, String> microserviceCandidates) {
            this.microserviceCandidates = microserviceCandidates;
        }

        public Map<String, Object> getDependencyAccuracy() {
            return dependencyAccuracy;
        }

        public void setDependencyAccuracy(Map<String, Object> dependencyAccuracy) {
            this.dependencyAccuracy = dependencyAccuracy;
        }

        public Map<String, Object> getDecompositionAccuracy() {
            return decompositionAccuracy;
        }

        public void setDecompositionAccuracy(Map<String, Object> decompositionAccuracy) {
            this.decompositionAccuracy = decompositionAccuracy;
        }
    }
}