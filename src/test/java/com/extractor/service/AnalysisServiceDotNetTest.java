package com.extractor.service;

import com.extractor.model.DependencyGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that AnalysisService correctly routes Java projects through JavaSpoonAnalyzer
 * after the LanguageAnalyzer abstraction is introduced.
 * Verifies zero behavioral regression for Java projects (Constitution I).
 * [TS-041, TS-043]
 */
class AnalysisServiceDotNetTest {

    private AnalysisService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new AnalysisService();
    }

    @Test
    void analyzeProject_javaProject_routesThroughJavaAnalyzer() throws Exception {
        Path projectDir = createMinimalJavaProject();

        AnalysisResult result = service.analyzeProject(projectDir);

        assertNotNull(result);
        assertNotNull(result.getDependencyGraph());
        assertEquals("spoon", result.getDependencyGraph().getMeta().getSource(),
                "Java projects must produce meta.source='spoon' after LanguageAnalyzer refactoring");
    }

    @Test
    void getDependencyGraph_javaProject_returnsGraphWithSpoonSource() throws Exception {
        Path projectDir = createMinimalJavaProject();

        DependencyGraph graph = service.getDependencyGraph(projectDir);

        assertNotNull(graph);
        assertEquals("spoon", graph.getMeta().getSource());
        assertNotNull(graph.getComponents());
        assertNotNull(graph.getEdges());
    }

    @Test
    void analyzeProject_javaProject_producesIdenticalComponentCount() throws Exception {
        Path projectDir = createMinimalJavaProject();

        // Analyze twice — results must be identical (Constitution IV: deterministic)
        AnalysisResult result1 = service.analyzeProject(projectDir);
        AnalysisResult result2 = service.analyzeProject(projectDir);

        assertEquals(result1.getComponentCount(), result2.getComponentCount(),
                "Deterministic: same project must produce same component count");
        assertEquals(result1.getEdgeCount(), result2.getEdgeCount(),
                "Deterministic: same project must produce same edge count");
    }

    @Test
    void getApiContracts_javaProject_returnsContractsAfterRefactoring() throws Exception {
        Path projectDir = createMinimalJavaProject();

        DependencyGraph.ApiContracts contracts = service.getApiContracts(projectDir);

        assertNotNull(contracts);
        assertNotNull(contracts.getEndpoints());
    }

    private Path createMinimalJavaProject() throws IOException {
        Path projectDir = tempDir.resolve("java-project");
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
