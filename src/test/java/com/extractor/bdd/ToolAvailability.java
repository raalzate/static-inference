package com.extractor.bdd;

import com.extractor.schema.DemoOutputs;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Detects the external tools some scenarios need ({@code dotnet}, {@code node}/{@code npm},
 * {@code dotnet-analyzer}, {@code bash}/{@code make}). A missing tool skips the scenario with an explicit
 * reason (JUnit assumption → Cucumber "skipped"); {@code make schema-check} runs every front and reports
 * a missing tool as NOT VERIFIED instead (FR-015).
 */
public final class ToolAvailability {

    /** Environment variable / system property enabling the end-to-end schema-check scenarios. */
    public static final String SCHEMA_CHECK_E2E = "SCHEMA_CHECK_E2E";
    /** Set by scripts/schema-check.sh for its child processes (prevents recursive schema-check runs). */
    public static final String SCHEMA_CHECK_RUNNING = "SCHEMA_CHECK_RUNNING";

    private static final Map<String, Boolean> CACHE = new ConcurrentHashMap<>();

    private ToolAvailability() {
    }

    /** True when {@code dotnet --version} succeeds. */
    public static boolean dotnet() {
        return CACHE.computeIfAbsent("dotnet", k -> runs(List.of("dotnet", "--version")));
    }

    /** True when Node ≥ 20 and npm are on the PATH. */
    public static boolean node() {
        return CACHE.computeIfAbsent("node", k -> nodeMajor() >= 20 && runs(List.of("npm", "--version")));
    }

    /** True when bash and make are on the PATH. */
    public static boolean shell() {
        return CACHE.computeIfAbsent("shell", k -> runs(List.of("bash", "--version"))
                && runs(List.of("make", "--version")));
    }

    /** Skips the scenario when the .NET SDK is missing. */
    public static void requireDotnet() {
        Assumptions.assumeTrue(dotnet(), "requires-dotnet: .NET SDK not available, scenario skipped");
    }

    /** Skips the scenario when the dotnet-analyzer executable cannot be located. */
    public static void requireDotnetAnalyzer() {
        Assumptions.assumeTrue(DemoOutputs.dotNetAnalyzerAvailable(),
                "requires-dotnet: dotnet-analyzer executable not found (target/, DOTNET_ANALYZER_PATH, PATH)");
    }

    /** Skips the scenario when Node ≥ 20 / npm are missing. */
    public static void requireNode() {
        Assumptions.assumeTrue(node(), "requires-node: Node >= 20 with npm not available, scenario skipped");
    }

    /** Skips end-to-end schema-check scenarios unless explicitly enabled and not already inside schema-check. */
    public static void requireSchemaCheckE2e() {
        Assumptions.assumeFalse(flag(SCHEMA_CHECK_RUNNING),
                "already running inside schema-check: nested schema-check scenarios skipped");
        Assumptions.assumeTrue(flag(SCHEMA_CHECK_E2E),
                "end-to-end schema-check scenarios run only with " + SCHEMA_CHECK_E2E + "=true (long-running)");
        Assumptions.assumeTrue(shell(), "requires bash and make");
    }

    static boolean flag(String name) {
        String value = System.getProperty(name);
        if (value == null) {
            value = System.getenv(name);
        }
        return value != null && !value.isBlank() && !"0".equals(value) && !"false".equalsIgnoreCase(value);
    }

    private static int nodeMajor() {
        try {
            Process process = new ProcessBuilder("node", "--version").redirectErrorStream(true).start();
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
                return -1;
            }
            String digits = out.startsWith("v") ? out.substring(1) : out;
            return Integer.parseInt(digits.split("\\.")[0]);
        } catch (IOException | NumberFormatException e) {
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    private static boolean runs(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            return process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
