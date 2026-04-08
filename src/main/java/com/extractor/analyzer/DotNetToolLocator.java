package com.extractor.analyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Finds the dotnet-analyzer executable on the system.
 * Search order: 1) JAR sibling directory, 2) System PATH, 3) DOTNET_ANALYZER_PATH property/env var.
 */
public class DotNetToolLocator {

    private static final Logger logger = LoggerFactory.getLogger(DotNetToolLocator.class);
    private static final String EXECUTABLE_NAME = "dotnet-analyzer";
    private static final String ENV_VAR_NAME = "DOTNET_ANALYZER_PATH";

    private final Path baseDir;

    /**
     * Creates a locator that searches relative to the given base directory (typically JAR location).
     */
    public DotNetToolLocator(Path baseDir) {
        this.baseDir = baseDir;
    }

    /**
     * Locates the dotnet-analyzer executable.
     *
     * @return the path to the executable
     * @throws IllegalStateException if the executable cannot be found
     */
    public Path locate() {
        // 1. Check sibling directory (same folder as the JAR)
        if (baseDir != null && Files.isDirectory(baseDir)) {
            Path sibling = baseDir.resolve(EXECUTABLE_NAME);
            if (Files.exists(sibling)) {
                logger.info("Found dotnet-analyzer in sibling directory: {}", sibling);
                return sibling.toAbsolutePath();
            }
        }

        // 2. Check DOTNET_ANALYZER_PATH system property (testable) or environment variable
        String envPath = System.getProperty(ENV_VAR_NAME);
        if (envPath == null || envPath.isEmpty()) {
            envPath = System.getenv(ENV_VAR_NAME);
        }
        if (envPath != null && !envPath.isEmpty()) {
            Path envDir = Path.of(envPath);
            Path envExe = envDir.resolve(EXECUTABLE_NAME);
            if (Files.exists(envExe)) {
                logger.info("Found dotnet-analyzer via {}: {}", ENV_VAR_NAME, envExe);
                return envExe.toAbsolutePath();
            }
            // Try as direct path to executable
            if (Files.exists(envDir) && envDir.getFileName().toString().contains(EXECUTABLE_NAME)) {
                logger.info("Found dotnet-analyzer via {} (direct path): {}", ENV_VAR_NAME, envDir);
                return envDir.toAbsolutePath();
            }
        }

        // 3. Check system PATH
        Path pathExe = findOnPath();
        if (pathExe != null) {
            logger.info("Found dotnet-analyzer on system PATH: {}", pathExe);
            return pathExe;
        }

        throw new IllegalStateException(
                "Could not find 'dotnet-analyzer' executable. Searched:\n"
                        + "  1. Sibling directory: " + baseDir + "\n"
                        + "  2. DOTNET_ANALYZER_PATH environment variable\n"
                        + "  3. System PATH\n"
                        + "Installation: build the dotnet-analyzer project and place the executable "
                        + "next to the Java fat JAR, or set DOTNET_ANALYZER_PATH to its directory.");
    }

    private Path findOnPath() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;

        for (String dir : pathEnv.split(System.getProperty("path.separator"))) {
            Path candidate = Path.of(dir).resolve(EXECUTABLE_NAME);
            if (Files.exists(candidate)) {
                return candidate.toAbsolutePath();
            }
        }
        return null;
    }
}
