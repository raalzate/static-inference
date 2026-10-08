package com.extractor.analyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Finds the dotnet-analyzer executable on the system.
 * Search order: 1) JAR sibling directory,
 * 2) DOTNET_ANALYZER_PATH property/env var, 3) System PATH.
 * On Windows the {@code .exe} name is tried first.
 */
public class DotNetToolLocator {

    /** Logger. */
    private static final Logger LOG =
            LoggerFactory.getLogger(DotNetToolLocator.class);
    /** Base executable name, without platform extension. */
    private static final String EXECUTABLE_NAME = "dotnet-analyzer";
    /** System property / environment variable overriding the location. */
    private static final String ENV_VAR_NAME = "DOTNET_ANALYZER_PATH";

    /** Directory searched first (typically the JAR location). */
    private final Path baseDir;
    /** Candidate executable names, in lookup order. */
    private final List<String> executableNames;
    /** Environment lookup for DOTNET_ANALYZER_PATH and PATH. */
    private final UnaryOperator<String> env;

    /**
     * Creates a locator that searches relative to the given base directory
     * (typically JAR location).
     *
     * @param dir base directory for the sibling lookup
     */
    public DotNetToolLocator(final Path dir) {
        this(dir, System.getProperty("os.name", "")
                .toLowerCase().startsWith("windows"));
    }

    /**
     * Creates a locator for an explicit platform (testable).
     *
     * @param dir     base directory for the sibling lookup
     * @param windows whether to look for {@code dotnet-analyzer.exe} first
     */
    public DotNetToolLocator(final Path dir, final boolean windows) {
        this(dir, windows, System::getenv);
    }

    /**
     * Creates a locator with an explicit environment lookup
     * (hermetic tests, BUG-006).
     *
     * @param dir       base directory for the sibling lookup
     * @param windows   whether to look for {@code dotnet-analyzer.exe} first
     * @param envLookup environment lookup for DOTNET_ANALYZER_PATH and PATH
     */
    DotNetToolLocator(final Path dir, final boolean windows,
            final UnaryOperator<String> envLookup) {
        this.baseDir = dir;
        this.env = envLookup;
        this.executableNames = windows
                ? List.of(EXECUTABLE_NAME + ".exe", EXECUTABLE_NAME)
                : List.of(EXECUTABLE_NAME);
    }

    /**
     * Locates the dotnet-analyzer executable.
     *
     * @return the path to the executable
     * @throws IllegalStateException if the executable cannot be found
     */
    public Path locate() {
        // 1. Sibling directory (same folder as the JAR)
        if (baseDir != null && Files.isDirectory(baseDir)) {
            Path sibling = resolveIn(baseDir);
            if (sibling != null) {
                LOG.info("Found dotnet-analyzer in sibling directory: {}",
                        sibling);
                return sibling.toAbsolutePath();
            }
        }

        // 2. DOTNET_ANALYZER_PATH system property (testable) or env var
        Path fromEnv = locateFromOverride();
        if (fromEnv != null) {
            return fromEnv;
        }

        // 3. System PATH
        Path pathExe = findOnPath();
        if (pathExe != null) {
            LOG.info("Found dotnet-analyzer on system PATH: {}", pathExe);
            return pathExe;
        }

        throw new IllegalStateException(
                "Could not find 'dotnet-analyzer' executable. Searched:\n"
                + "  1. Sibling directory: " + baseDir + "\n"
                + "  2. DOTNET_ANALYZER_PATH environment variable\n"
                + "  3. System PATH\n"
                + "Installation: build the dotnet-analyzer project and "
                + "place the executable next to the Java fat JAR, "
                + "or set DOTNET_ANALYZER_PATH to its directory.");
    }

    private Path locateFromOverride() {
        String envPath = System.getProperty(ENV_VAR_NAME);
        if (envPath == null || envPath.isEmpty()) {
            envPath = env.apply(ENV_VAR_NAME);
        }
        if (envPath == null || envPath.isEmpty()) {
            return null;
        }
        Path envDir = Path.of(envPath);
        Path envExe = Files.isDirectory(envDir) ? resolveIn(envDir) : null;
        if (envExe != null) {
            LOG.info("Found dotnet-analyzer via {}: {}", ENV_VAR_NAME, envExe);
            return envExe.toAbsolutePath();
        }
        // Direct path to the executable
        Path envFileName = envDir.getFileName();
        if (Files.isRegularFile(envDir) && envFileName != null
                && envFileName.toString().contains(EXECUTABLE_NAME)) {
            LOG.info("Found dotnet-analyzer via {} (direct path): {}",
                    ENV_VAR_NAME, envDir);
            return envDir.toAbsolutePath();
        }
        return null;
    }

    private Path findOnPath() {
        String pathEnv = env.apply("PATH");
        if (pathEnv == null) {
            return null;
        }
        String separator = System.getProperty("path.separator");
        for (String dir : pathEnv.split(separator)) {
            Path candidate = resolveIn(Path.of(dir));
            if (candidate != null) {
                return candidate.toAbsolutePath();
            }
        }
        return null;
    }

    private Path resolveIn(final Path dir) {
        for (String name : executableNames) {
            Path candidate = dir.resolve(name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
