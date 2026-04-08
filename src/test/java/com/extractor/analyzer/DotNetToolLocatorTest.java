package com.extractor.analyzer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DotNetToolLocator.
 * Validates executable discovery order: JAR sibling dir, system PATH,
 * DOTNET_ANALYZER_PATH env var / system property override.
 * [TS-013, TS-014]
 *
 * TDD RED phase: these tests will not compile until DotNetToolLocator is implemented.
 */
class DotNetToolLocatorTest {

    @TempDir
    Path tempDir;

    // --- Sibling directory lookup ---

    /**
     * [TS-013] When a dotnet-analyzer executable exists in a sibling directory
     * relative to the provided jar location, locate() should return its path.
     */
    @Test
    void locate_withExecutableInSiblingDir_returnsPath() throws IOException {
        // Arrange: create a fake dotnet-analyzer executable next to the jar location
        Path executable = tempDir.resolve("dotnet-analyzer");
        Files.createFile(executable);
        executable.toFile().setExecutable(true);

        DotNetToolLocator locator = new DotNetToolLocator(tempDir);

        // Act
        Path result = locator.locate();

        // Assert
        assertNotNull(result, "locate() should return a non-null path");
        assertTrue(result.toAbsolutePath().toString().contains("dotnet-analyzer"),
                "Returned path should point to the dotnet-analyzer executable");
        assertEquals(executable.toAbsolutePath(), result.toAbsolutePath(),
                "Should resolve to the executable in the sibling directory");
    }

    // --- Environment / system property override ---

    /**
     * [TS-013] When DOTNET_ANALYZER_PATH is provided via system property
     * (used as a testable stand-in for the env var), locate() should honour it.
     */
    @Test
    void locate_withSystemPropertyOverride_returnsPath() throws IOException {
        // Arrange: create an executable at a custom location
        Path customDir = tempDir.resolve("custom");
        Files.createDirectories(customDir);
        Path executable = customDir.resolve("dotnet-analyzer");
        Files.createFile(executable);
        executable.toFile().setExecutable(true);

        // Use an empty base path so sibling lookup fails
        Path emptyBase = tempDir.resolve("empty");
        Files.createDirectories(emptyBase);
        DotNetToolLocator locator = new DotNetToolLocator(emptyBase);

        String previousValue = System.getProperty("DOTNET_ANALYZER_PATH");
        try {
            System.setProperty("DOTNET_ANALYZER_PATH", customDir.toString());

            // Act
            Path result = locator.locate();

            // Assert
            assertNotNull(result, "locate() should return a non-null path when system property is set");
            assertEquals(executable.toAbsolutePath(), result.toAbsolutePath(),
                    "Should resolve to the executable specified via DOTNET_ANALYZER_PATH");
        } finally {
            // Cleanup: restore previous system property state
            if (previousValue == null) {
                System.clearProperty("DOTNET_ANALYZER_PATH");
            } else {
                System.setProperty("DOTNET_ANALYZER_PATH", previousValue);
            }
        }
    }

    // --- Not found ---

    /**
     * [TS-014] When the executable cannot be found in any search location,
     * locate() should throw IllegalStateException with installation instructions.
     */
    @Test
    void locate_whenNotFound_throwsWithInstructions() {
        // Arrange: empty directory, no system property, tool not on PATH
        Path emptyBase = tempDir.resolve("nonexistent");
        DotNetToolLocator locator = new DotNetToolLocator(emptyBase);

        String previousValue = System.getProperty("DOTNET_ANALYZER_PATH");
        try {
            System.clearProperty("DOTNET_ANALYZER_PATH");

            // Act & Assert
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    locator::locate,
                    "Should throw IllegalStateException when executable is not found");

            assertTrue(ex.getMessage().contains("dotnet-analyzer"),
                    "Exception message should mention 'dotnet-analyzer' with installation instructions");
        } finally {
            if (previousValue != null) {
                System.setProperty("DOTNET_ANALYZER_PATH", previousValue);
            }
        }
    }
}
