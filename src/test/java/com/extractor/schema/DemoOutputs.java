package com.extractor.schema;

import com.extractor.analyzer.DotNetToolLocator;
import com.extractor.service.AnalysisResult;
import com.extractor.service.AnalysisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test utility that runs the engine ({@link AnalysisService}) over a project of {@code test-for-demo/}
 * and serializes the three artifacts exactly as the CLI does (same {@link ObjectMapper} configuration
 * as {@code MicroserviceInferenceMain}). Every produced artifact is also written to
 * {@code target/schema-samples/java/<project>/} so the generated TypeScript validators can check the
 * producer → consumer chain (plan Design Note 5).
 * <p>
 * Results are cached per project for the lifetime of the JVM (the engine is deterministic).
 */
public final class DemoOutputs {

    /** Java demo projects (SC-001). */
    public static final List<String> JAVA_PROJECTS = List.of("java-standar", "jee-architecture-master", "legacy-erp");
    /** .NET demo projects analyzed through the engine (SC-001). */
    public static final List<String> DOTNET_PROJECTS =
            List.of("dotnet-ecommerce", "dotnet-legacy-clinic", "dotnet-unstructured");

    /** Artifact file names in CLI order. */
    public static final String GRAPH = "output.json";
    public static final String ARCHITECTURE = "output_architecture.json";
    public static final String ENTRYPOINTS = "output_entrypoints.json";

    private static final Map<String, Artifacts> CACHE = new ConcurrentHashMap<>();

    private DemoOutputs() {
    }

    /** Serialized artifacts of one analysis. */
    public record Artifacts(String project, String graph, String architecture, String entrypoints,
                            List<String> warnings) {

        /** Returns the artifact text by CLI file name. */
        public String byFileName(String fileName) {
            return switch (fileName) {
                case GRAPH -> graph;
                case ARCHITECTURE -> architecture;
                case ENTRYPOINTS -> entrypoints;
                default -> throw new IllegalArgumentException("Unknown artifact " + fileName);
            };
        }

        /** Returns the artifact text by contract kind. */
        public String byKind(String kind) {
            return switch (kind) {
                case SchemaRegistry.DEPENDENCY_GRAPH -> graph;
                case SchemaRegistry.ARCHITECTURE -> architecture;
                case SchemaRegistry.ENTRYPOINTS -> entrypoints;
                default -> throw new IllegalArgumentException("Unknown kind " + kind);
            };
        }
    }

    /** ObjectMapper configured exactly like the CLI and MCP adapters. */
    public static ObjectMapper cliMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        return mapper;
    }

    /** Repository root. */
    public static Path repoRoot() {
        return SchemaRegistry.get().repoRoot();
    }

    /** Absolute path of {@code test-for-demo/<project>}. */
    public static Path projectDir(String project) {
        return repoRoot().resolve("test-for-demo").resolve(project);
    }

    /** True when the project is a .NET demo project. */
    public static boolean isDotNet(String project) {
        return DOTNET_PROJECTS.contains(project);
    }

    /**
     * True when the {@code dotnet-analyzer} executable can be located the same way the engine does
     * (sibling of {@code target/classes}, {@code DOTNET_ANALYZER_PATH} or {@code PATH}).
     */
    public static boolean dotNetAnalyzerAvailable() {
        try {
            new DotNetToolLocator(repoRoot().resolve("target")).locate();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** Analyzes the demo project once (cached) and returns the serialized artifacts. */
    public static Artifacts of(String project) {
        return CACHE.computeIfAbsent(project, p -> analyze(projectDir(p), p, true));
    }

    /**
     * Analyzes a project without caching and writes the CLI artifacts into {@code outDir}.
     *
     * @return the serialized artifacts
     */
    public static Artifacts analyzeInto(Path projectDir, Path outDir) {
        Artifacts artifacts = analyze(projectDir, projectDir.getFileName().toString(), false);
        writeArtifacts(artifacts, outDir);
        return artifacts;
    }

    /** Writes the three artifacts with their CLI file names. */
    public static void writeArtifacts(Artifacts artifacts, Path outDir) {
        try {
            Files.createDirectories(outDir);
            Files.writeString(outDir.resolve(GRAPH), artifacts.graph(), StandardCharsets.UTF_8);
            Files.writeString(outDir.resolve(ARCHITECTURE), artifacts.architecture(), StandardCharsets.UTF_8);
            Files.writeString(outDir.resolve(ENTRYPOINTS), artifacts.entrypoints(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Writes a validated sample under {@code target/schema-samples/<front>/<project>/<file>}. */
    public static void writeSample(String front, String project, String fileName, String json) {
        Path out = repoRoot().resolve("target").resolve("schema-samples").resolve(front).resolve(project);
        try {
            Files.createDirectories(out);
            Files.writeString(out.resolve(fileName), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Artifacts analyze(Path projectDir, String project, boolean writeSamples) {
        try {
            AnalysisResult result = new AnalysisService().analyzeProject(projectDir);
            ObjectMapper mapper = cliMapper();
            Artifacts artifacts = new Artifacts(project,
                    mapper.writeValueAsString(result.getDependencyGraph()),
                    mapper.writeValueAsString(result.getArchitecture()),
                    mapper.writeValueAsString(result.getApiContracts()),
                    result.getWarnings());
            if (writeSamples) {
                writeSample("java", project, GRAPH, artifacts.graph());
                writeSample("java", project, ARCHITECTURE, artifacts.architecture());
                writeSample("java", project, ENTRYPOINTS, artifacts.entrypoints());
            }
            return artifacts;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Analysis of " + projectDir + " failed: " + e.getMessage(), e);
        }
    }
}
