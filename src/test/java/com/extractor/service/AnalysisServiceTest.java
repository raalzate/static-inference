package com.extractor.service;

import com.extractor.inference.ConsolidatedArchitecture;
import com.extractor.model.Component;
import com.extractor.model.DependencyGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AnalysisService shared core.
 * Validates each method with valid input, invalid path, and edge cases.
 * [TS-027] Ensures MCP and CLI use the same analysis pipeline.
 */
class AnalysisServiceTest {

    private AnalysisService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new AnalysisService();
    }

    // --- analyzeProject ---

    @Test
    void analyzeProject_withValidJavaProject_returnsCompleteResult() throws Exception {
        Path projectDir = createMinimalJavaProject();

        AnalysisResult result = service.analyzeProject(projectDir);

        assertNotNull(result);
        assertNotNull(result.getDependencyGraph());
        assertNotNull(result.getArchitecture());
        assertNotNull(result.getApiContracts());
        assertTrue(result.getComponentCount() >= 0);
        assertTrue(result.getEdgeCount() >= 0);
        assertNotNull(result.getWarnings());
    }

    @Test
    void analyzeProject_withNonExistentPath_throwsIllegalArgument() {
        Path nonExistent = tempDir.resolve("does-not-exist");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.analyzeProject(nonExistent));
        assertTrue(ex.getMessage().contains("does not exist"));
    }

    @Test
    void analyzeProject_withNullPath_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> service.analyzeProject(null));
    }

    @Test
    void analyzeProject_withFilePath_throwsIllegalArgument() throws IOException {
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "not a directory");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.analyzeProject(file));
        assertTrue(ex.getMessage().contains("not a directory"));
    }

    // --- getDependencyGraph ---

    @Test
    void getDependencyGraph_withValidProject_returnsGraph() throws Exception {
        Path projectDir = createMinimalJavaProject();

        DependencyGraph graph = service.getDependencyGraph(projectDir);

        assertNotNull(graph);
        assertNotNull(graph.getComponents());
        assertNotNull(graph.getEdges());
    }

    @Test
    void getDependencyGraph_withInvalidPath_throwsIllegalArgument() {
        Path nonExistent = tempDir.resolve("missing");
        assertThrows(IllegalArgumentException.class,
                () -> service.getDependencyGraph(nonExistent));
    }

    // --- getArchitectureProposal ---

    @Test
    void getArchitectureProposal_withNoFilter_returnsAll() throws Exception {
        Path projectDir = createMinimalJavaProject();

        ConsolidatedArchitecture arch = service.getArchitectureProposal(projectDir, null);

        assertNotNull(arch);
        assertNotNull(arch.getProposals());
    }

    @Test
    void getArchitectureProposal_withAltaFilter_returnsOnlyAlta() throws Exception {
        Path projectDir = createMinimalJavaProject();

        ConsolidatedArchitecture arch = service.getArchitectureProposal(projectDir, "Alta");

        assertNotNull(arch);
        arch.getProposals().forEach(p ->
                assertEquals("Alta", p.getViability(),
                        "Filtered result should only contain Alta viability"));
    }

    @Test
    void getArchitectureProposal_withInvalidPath_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getArchitectureProposal(tempDir.resolve("nope"), "Alta"));
    }

    // --- getApiContracts ---

    @Test
    void getApiContracts_withValidProject_returnsContracts() throws Exception {
        Path projectDir = createMinimalJavaProject();

        DependencyGraph.ApiContracts contracts = service.getApiContracts(projectDir);

        assertNotNull(contracts);
        assertNotNull(contracts.getEndpoints());
    }

    @Test
    void getApiContracts_withInvalidPath_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getApiContracts(tempDir.resolve("nope")));
    }

    // --- getComponentMetrics ---

    @Test
    void getComponentMetrics_withUnknownComponent_returnsNull() throws Exception {
        Path projectDir = createMinimalJavaProject();

        Component result = service.getComponentMetrics(projectDir, "com.nonexistent.Foo");

        assertNull(result);
    }

    @Test
    void getComponentMetrics_withInvalidPath_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getComponentMetrics(tempDir.resolve("nope"), "com.Foo"));
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
