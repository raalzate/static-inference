package com.extractor.analyzer;

import com.extractor.model.DependencyGraph;
import com.extractor.model.SchemaVersion;
import com.extractor.utils.DependencyResolver;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Bridges Java and the .NET Roslyn analyzer by invoking the dotnet-analyzer
 * executable as a subprocess and deserializing its JSON output into a
 * {@link DependencyGraph}.
 * <p>
 * Implements {@link LanguageAnalyzer} so it can be used interchangeably with
 * {@link JavaSpoonAnalyzer}.
 */
public class DotNetBridgeAnalyzer implements LanguageAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(DotNetBridgeAnalyzer.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

    private final DotNetToolLocator locator;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final List<String> warnings = new ArrayList<>();

    /**
     * Creates a new analyzer that uses the given locator to find the dotnet-analyzer executable.
     *
     * @param locator the tool locator for finding the dotnet-analyzer executable
     */
    public DotNetBridgeAnalyzer(DotNetToolLocator locator) {
        this(locator, DEFAULT_TIMEOUT);
    }

    /**
     * Creates a new analyzer with an explicit subprocess timeout.
     *
     * @param locator the tool locator for finding the dotnet-analyzer executable
     * @param timeout maximum wall-clock time the subprocess may run
     */
    public DotNetBridgeAnalyzer(DotNetToolLocator locator, Duration timeout) {
        this.locator = locator;
        this.timeout = timeout;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Analyze a .NET project by invoking the dotnet-analyzer subprocess.
     *
     * @param projectRoot root directory of the .NET project
     * @return the computed dependency graph
     * @throws Exception if the subprocess fails or output cannot be parsed
     */
    @Override
    public DependencyGraph analyzeProject(Path projectRoot) throws Exception {
        Path executablePath = locator.locate();
        logger.info("Running dotnet-analyzer: {} {}", executablePath, projectRoot);

        ProcessBuilder pb = new ProcessBuilder(
                executablePath.toString(),
                projectRoot.toString()
        );
        pb.redirectErrorStream(false);

        Process process = pb.start();
        process.getOutputStream().close();

        // Drain both streams concurrently so a full stderr pipe cannot block stdout (BUG-002)
        CompletableFuture<String> stdoutFuture = drain(process.getInputStream());
        CompletableFuture<String> stderrFuture = drain(process.getErrorStream());

        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            throw new RuntimeException(
                    "dotnet-analyzer timed out after " + timeout.toSeconds() + " seconds");
        }

        String stdout = stdoutFuture.join();
        String stderr = stderrFuture.join();

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException(
                    "dotnet-analyzer exited with code " + exitCode + ": " + stderr);
        }

        logger.info("dotnet-analyzer completed successfully, parsing output ({} chars)", stdout.length());
        if (!stderr.isEmpty()) {
            logger.debug("dotnet-analyzer stderr: {}", stderr);
        }

        return parseOutput(stdout);
    }

    private static CompletableFuture<String> drain(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                in.transferTo(out);
                return out.toString(StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /**
     * Deserialize the JSON output from the dotnet-analyzer into a {@link DependencyGraph}.
     * <p>
     * Package-private for testability: unit tests can call this directly with sample JSON
     * without needing to spawn a subprocess.
     *
     * @param json the JSON string produced by the dotnet-analyzer
     * @return the deserialized dependency graph
     * @throws RuntimeException if the JSON is empty or malformed
     */
    DependencyGraph parseOutput(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new RuntimeException("dotnet-analyzer produced empty output");
        }

        try {
            JsonNode tree = objectMapper.readTree(json);
            DependencyGraph graph = objectMapper.treeToValue(tree, DependencyGraph.class);
            reconcileSchemaVersion(tree, graph);
            return graph;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(
                    "Failed to parse dotnet-analyzer JSON output: " + e.getMessage(), e);
        }
    }

    /**
     * Compares the contract version declared by the .NET analyzer with the engine's (FR-022): a missing or
     * different value is reported as a warning naming both versions, and the engine version is emitted.
     */
    private void reconcileSchemaVersion(JsonNode tree, DependencyGraph graph) {
        JsonNode declared = tree.path("meta").path("schema_version");
        String dotnetVersion = declared.isTextual() ? declared.asText() : "absent";
        if (!SchemaVersion.CURRENT.equals(dotnetVersion)) {
            String warning = "dotnet-analyzer schema_version " + dotnetVersion
                    + " != engine " + SchemaVersion.CURRENT;
            logger.warn(warning);
            warnings.add(warning);
        }
        if (graph.getMeta() == null) {
            graph.setMeta(new DependencyGraph.Meta());
        }
        graph.getMeta().setSchemaVersion(SchemaVersion.CURRENT);
        if (graph.getApiContracts() == null) {
            graph.setApiContracts(new DependencyGraph.ApiContracts());
        }
        graph.getApiContracts().getMeta().setSchemaVersion(SchemaVersion.CURRENT);
    }

    /**
     * Warnings collected while parsing the .NET analyzer output (e.g. contract version mismatch).
     *
     * @return an unmodifiable view of the warnings
     */
    @Override
    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    /**
     * Return the .NET-specific dependency resolver that parses .csproj PackageReference elements.
     *
     * @return a {@link DotNetDependencyResolver}
     */
    @Override
    public DependencyResolver getDependencyResolver() {
        return new DotNetDependencyResolver();
    }
}
