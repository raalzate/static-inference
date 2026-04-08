package com.extractor.analyzer;

import com.extractor.model.DependencyGraph;
import com.extractor.utils.DependencyResolver;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    private static final long DEFAULT_TIMEOUT_MINUTES = 5;

    private final DotNetToolLocator locator;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new analyzer that uses the given locator to find the dotnet-analyzer executable.
     *
     * @param locator the tool locator for finding the dotnet-analyzer executable
     */
    public DotNetBridgeAnalyzer(DotNetToolLocator locator) {
        this.locator = locator;
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

        // Read stdout
        String stdout;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            stdout = reader.lines().collect(Collectors.joining("\n"));
        }

        // Read stderr for diagnostics
        String stderr;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()))) {
            stderr = reader.lines().collect(Collectors.joining("\n"));
        }

        boolean finished = process.waitFor(DEFAULT_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException(
                    "dotnet-analyzer timed out after " + DEFAULT_TIMEOUT_MINUTES + " minutes");
        }

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
            return objectMapper.readValue(json, DependencyGraph.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(
                    "Failed to parse dotnet-analyzer JSON output: " + e.getMessage(), e);
        }
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
