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

    /**
     * Root of the hermetic demo copies ({@code target/schema-demo}): it plays the role of the repository root
     * for the {@code ${REPO}} placeholder of the v1.1.0 baselines.
     */
    public static Path demoRoot() {
        return repoRoot().resolve("target").resolve("schema-demo");
    }

    /**
     * Absolute path of a hermetic copy of {@code test-for-demo/<project>} holding only the files tracked by git
     * (build outputs such as {@code target/}, {@code obj/} or previous {@code output*.json} left in the working
     * tree would otherwise change the analysis). Created once per JVM.
     */
    public static Path projectDir(String project) {
        return COPIES.computeIfAbsent(project, DemoOutputs::copyTracked);
    }

    private static final Map<String, Path> COPIES = new ConcurrentHashMap<>();

    private static Path copyTracked(String project) {
        Path source = repoRoot().resolve("test-for-demo").resolve(project);
        Path target = demoRoot().resolve("test-for-demo").resolve(project);
        try {
            deleteRecursively(target);
            List<String> files = trackedFiles("test-for-demo/" + project);
            if (files.isEmpty()) {
                throw new IllegalStateException("no git-tracked files under " + source);
            }
            for (String file : files) {
                Path from = repoRoot().resolve(file);
                if (Files.isRegularFile(from)) {
                    Path to = demoRoot().resolve(file);
                    Files.createDirectories(to.getParent());
                    Files.copy(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> trackedFiles(String relativeDir) throws IOException {
        Process process = new ProcessBuilder("git", "ls-files", "-z", "--", relativeDir)
                .directory(repoRoot().toFile()).redirectErrorStream(false).start();
        process.getOutputStream().close();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        process.getErrorStream().transferTo(java.io.OutputStream.nullOutputStream());
        try {
            if (process.waitFor() != 0) {
                return filesWithoutBuildOutputs(relativeDir); // not a git checkout (e.g. a plain copy)
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return out.isEmpty() ? List.of() : List.of(out.split("\u0000"));
    }

    /** Fallback outside git: every file except the build outputs ignored by .gitignore. */
    private static List<String> filesWithoutBuildOutputs(String relativeDir) throws IOException {
        java.util.Set<String> skippedDirs = java.util.Set.of("target", "obj", "bin", ".si-output");
        try (java.util.stream.Stream<Path> walk = Files.walk(repoRoot().resolve(relativeDir))) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        for (Path part : repoRoot().resolve(relativeDir).relativize(p)) {
                            if (skippedDirs.contains(part.toString())) {
                                return false;
                            }
                        }
                        String name = p.getFileName().toString();
                        return !name.equals(".DS_Store") && !name.matches("output(_architecture|_entrypoints|_layers|_web)?\\.json");
                    })
                    .map(p -> repoRoot().relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
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
