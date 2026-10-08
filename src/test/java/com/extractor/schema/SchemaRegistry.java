package com.extractor.schema;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.PathType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test utility that loads the formal contract from {@code schema/<VERSION>/} and validates JSON documents
 * against it (005-json-schema-contract, plan Design Note 4).
 * <p>
 * The {@code $id} prefix {@code https://static-inference.dev/schema/<VERSION>/} is mapped to the local
 * directory, so validation never touches the network. Errors are rendered as
 * {@code <JSON Pointer>: <message> [keyword=..., property=..., value=...]} so callers can assert on the
 * path and on the offending field or value.
 * <p>
 * Uses {@code com.networknt:json-schema-validator} 3.x, already present transitively through the MCP SDK
 * (research.md D2 addendum); it works on Jackson 3, so documents are passed as JSON text.
 */
public final class SchemaRegistry {

    /** Schema kinds addressable by {@link #validate(String, String)}. */
    public static final String DEPENDENCY_GRAPH = "dependency-graph";
    public static final String ARCHITECTURE = "architecture";
    public static final String ENTRYPOINTS = "entrypoints";
    /** The {@code Component} definition of the common contract. */
    public static final String COMPONENT = "common#Component";

    private static volatile SchemaRegistry instance;

    private final Path schemaRoot;
    private final String version;
    private final String baseIri;
    private final com.networknt.schema.SchemaRegistry registry;
    private final Map<String, Schema> cache = new ConcurrentHashMap<>();

    private SchemaRegistry(Path schemaRoot) {
        this.schemaRoot = schemaRoot;
        this.version = readVersion(schemaRoot);
        this.baseIri = "https://static-inference.dev/schema/" + version + "/";
        Path versionDir = schemaRoot.resolve(version);
        this.registry = com.networknt.schema.SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder
                        .schemaRegistryConfig(com.networknt.schema.SchemaRegistryConfig.builder()
                                .formatAssertionsEnabled(true)
                                .pathType(PathType.JSON_POINTER)
                                .locale(Locale.ENGLISH)
                                .build())
                        .schemas(iri -> loadMapped(iri, versionDir)));
    }

    /** Returns the shared registry rooted at the {@code schema.dir} system property (or the discovered repo). */
    public static SchemaRegistry get() {
        SchemaRegistry local = instance;
        if (local == null) {
            synchronized (SchemaRegistry.class) {
                local = instance;
                if (local == null) {
                    local = new SchemaRegistry(locateSchemaRoot());
                    instance = local;
                }
            }
        }
        return local;
    }

    /** Root directory of the schema package ({@code <repo>/schema}). */
    public Path schemaRoot() {
        return schemaRoot;
    }

    /** Repository root (parent of the schema package). */
    public Path repoRoot() {
        return schemaRoot.toAbsolutePath().getParent();
    }

    /** Contract version declared in {@code schema/VERSION}. */
    public String version() {
        return version;
    }

    /** Absolute {@code $id} base of the current version. */
    public String baseIri() {
        return baseIri;
    }

    /** Local path of a schema file, e.g. {@code dependency-graph} or {@code mcp/analyze_project.response}. */
    public Path schemaFile(String kind) {
        String file = kind.startsWith("common#") ? "common" : kind;
        return schemaRoot.resolve(version).resolve(file + ".schema.json");
    }

    /**
     * Validates a JSON document against a contract kind.
     *
     * @param kind {@code dependency-graph}, {@code architecture}, {@code entrypoints},
     *             {@code mcp/<tool>.response} or {@code common#<Definition>}
     * @param json the document as JSON text
     * @return formatted validation errors (empty when valid)
     */
    public List<String> validate(String kind, String json) {
        Schema schema = cache.computeIfAbsent(kind, this::loadSchema);
        List<Error> errors = schema.validate(json, InputFormat.JSON);
        List<String> out = new ArrayList<>(errors.size());
        for (Error error : errors) {
            out.add(format(error));
        }
        return out;
    }

    private Schema loadSchema(String kind) {
        String iri;
        if (kind.startsWith("common#")) {
            iri = baseIri + "common.schema.json#/$defs/" + kind.substring("common#".length());
        } else {
            iri = baseIri + kind + ".schema.json";
        }
        return registry.getSchema(SchemaLocation.of(iri));
    }

    private String loadMapped(String iri, Path versionDir) {
        if (iri == null || !iri.startsWith(baseIri)) {
            return null;
        }
        String relative = iri.substring(baseIri.length());
        int hash = relative.indexOf('#');
        if (hash >= 0) {
            relative = relative.substring(0, hash);
        }
        Path file = versionDir.resolve(relative).normalize();
        if (!file.startsWith(versionDir) || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String format(Error error) {
        String pointer = error.getInstanceLocation() == null ? "" : error.getInstanceLocation().toString();
        StringBuilder sb = new StringBuilder();
        sb.append(pointer.isEmpty() ? "/" : pointer).append(": ").append(error.getMessage())
                .append(" [keyword=").append(error.getKeyword());
        if (error.getProperty() != null) {
            sb.append(", property=").append(error.getProperty());
        }
        if (error.getInstanceNode() != null && error.getInstanceNode().isValueNode()) {
            sb.append(", value=").append(error.getInstanceNode().toString());
        }
        return sb.append(']').toString();
    }

    private static String readVersion(Path schemaRoot) {
        try {
            return Files.readString(schemaRoot.resolve("VERSION"), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read contract version from " + schemaRoot, e);
        }
    }

    private static Path locateSchemaRoot() {
        String configured = System.getProperty("schema.dir");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath();
        }
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("schema").resolve("VERSION");
            if (Files.isRegularFile(candidate)) {
                return candidate.getParent();
            }
            current = current.getParent();
        }
        throw new IllegalStateException("schema/VERSION not found; set -Dschema.dir");
    }
}
