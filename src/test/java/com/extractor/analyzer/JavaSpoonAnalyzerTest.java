package com.extractor.analyzer;

import com.extractor.model.DependencyGraph;
import com.extractor.utils.DependencyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the JavaSpoonAnalyzer adapter.
 * Validates that JavaSpoonAnalyzer correctly delegates to ProjectAnalyzer
 * without changing behavior, and preserves backward-compatible metadata.
 * [TS-041, TS-042]
 *
 * TDD RED phase: these tests will not compile until T009 implements JavaSpoonAnalyzer.
 */
class JavaSpoonAnalyzerTest {

    private JavaSpoonAnalyzer analyzer;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        analyzer = new JavaSpoonAnalyzer();
    }

    // --- Delegation behavior ---

    /**
     * [TS-041] Analyze a minimal Java project via JavaSpoonAnalyzer and verify
     * that the DependencyGraph is returned with a non-null components list,
     * confirming delegation to the underlying ProjectAnalyzer.
     */
    @Test
    void analyzeProject_delegatesToProjectAnalyzer() throws Exception {
        Path projectDir = createMinimalJavaProject();

        DependencyGraph graph = analyzer.analyzeProject(projectDir);

        assertNotNull(graph, "DependencyGraph must not be null");
        assertNotNull(graph.getComponents(), "Components list must not be null");
        assertFalse(graph.getComponents().isEmpty(),
                "Components list should contain at least one component from the sample project");
    }

    /**
     * [TS-041] Verify that getDependencyResolver returns a non-null resolver,
     * confirming the adapter exposes the underlying ProjectAnalyzer's resolver.
     */
    @Test
    void getDependencyResolver_returnsNonNull() {
        DependencyResolver resolver = analyzer.getDependencyResolver();

        assertNotNull(resolver, "DependencyResolver must not be null");
    }

    /**
     * [TS-042] Analyze a directory with no source files and verify that the
     * resulting DependencyGraph has an empty components list.
     */
    @Test
    void analyzeProject_onEmptyDir_producesEmptyGraph() throws Exception {
        Path emptyProject = tempDir.resolve("empty-project");
        Files.createDirectories(emptyProject);

        DependencyGraph graph = analyzer.analyzeProject(emptyProject);

        assertNotNull(graph, "DependencyGraph must not be null even for empty projects");
        assertNotNull(graph.getComponents(), "Components list must not be null");
        assertTrue(graph.getComponents().isEmpty(),
                "Components list should be empty when no source files are present");
    }

    /**
     * [TS-042] Verify that the meta.source field is "spoon" to ensure backward
     * compatibility with consumers that rely on this metadata value.
     */
    @Test
    void analyzeProject_preservesMetaSourceAsSpoon() throws Exception {
        Path projectDir = createMinimalJavaProject();

        DependencyGraph graph = analyzer.analyzeProject(projectDir);

        assertNotNull(graph.getMeta(), "Meta must not be null");
        assertEquals("spoon", graph.getMeta().getSource(),
                "meta.source must be 'spoon' for backward compatibility");
    }

    // --- Helper ---

    /**
     * Creates a minimal Java project with a single source file for Spoon to analyze.
     */
    private Path createMinimalJavaProject() throws IOException {
        Path projectDir = tempDir.resolve("sample-project");
        Path srcDir = projectDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);

        Files.writeString(srcDir.resolve("HelloService.java"),
                """
                package com.example;

                public class HelloService {
                    public String greet(String name) {
                        return "Hello, " + name;
                    }
                }
                """);

        return projectDir;
    }
}
