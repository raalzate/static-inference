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
 * Tests for the LanguageAnalyzer interface contract.
 * Validates that JavaSpoonAnalyzer honours the LanguageAnalyzer abstraction
 * and preserves backward compatibility with the existing analysis pipeline.
 * [TS-041, TS-042, TS-043]
 */
class LanguageAnalyzerTest {

    private LanguageAnalyzer analyzer;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        analyzer = new JavaSpoonAnalyzer();
    }

    // --- Interface contract ---

    @Test
    void javaSpoonAnalyzer_implementsLanguageAnalyzer() {
        // Compile-time check: JavaSpoonAnalyzer must implement LanguageAnalyzer.
        // If this assignment compiles, the contract is satisfied.
        LanguageAnalyzer instance = new JavaSpoonAnalyzer();
        assertNotNull(instance);
    }

    // --- analyzeProject ---

    @Test
    void analyzeProject_withMinimalJavaProject_returnsNonNullGraph() throws Exception {
        Path projectDir = createMinimalJavaProject();

        DependencyGraph graph = analyzer.analyzeProject(projectDir);

        assertNotNull(graph, "DependencyGraph must not be null");
        assertNotNull(graph.getComponents(), "Components list must not be null");
        assertNotNull(graph.getEdges(), "Edges list must not be null");
    }

    // --- getDependencyResolver ---

    @Test
    void getDependencyResolver_returnsNonNullResolver() {
        DependencyResolver resolver = analyzer.getDependencyResolver();

        assertNotNull(resolver, "DependencyResolver must not be null");
    }

    // --- Error handling ---

    @Test
    void analyzeProject_withNonExistentPath_throwsException() {
        Path nonExistent = tempDir.resolve("does-not-exist");

        assertThrows(Exception.class, () -> analyzer.analyzeProject(nonExistent));
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
