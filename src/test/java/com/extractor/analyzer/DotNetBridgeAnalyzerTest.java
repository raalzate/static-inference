package com.extractor.analyzer;

import com.extractor.model.DependencyGraph;
import com.extractor.utils.DependencyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DotNetBridgeAnalyzer}.
 * Tests focus on the {@code parseOutput} method which deserializes the
 * dotnet-analyzer JSON output into a {@link DependencyGraph}.
 * <p>
 * [TS-001, TS-004, TS-005, TS-006, TS-007]
 */
class DotNetBridgeAnalyzerTest {

    @TempDir
    Path tempDir;

    private DotNetBridgeAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        // Use a locator pointed at a temp dir; for parseOutput tests we never invoke the subprocess
        DotNetToolLocator locator = new DotNetToolLocator(tempDir);
        analyzer = new DotNetBridgeAnalyzer(locator);
    }

    // --- Valid JSON parsing ---

    /**
     * [TS-001] When the dotnet-analyzer produces valid JSON matching the DependencyGraph schema,
     * parseOutput should deserialize it into correct components and edges.
     */
    @Test
    void analyzeProject_withValidJsonOutput_returnsDependencyGraph() {
        // Arrange: sample JSON that matches the DependencyGraph schema
        String validJson = "{\n"
                + "  \"components\": [\n"
                + "    {\n"
                + "      \"id\": \"MyApp.Controllers.HomeController\",\n"
                + "      \"files\": [\"Controllers/HomeController.cs\"],\n"
                + "      \"loc\": 45,\n"
                + "      \"tables_used\": [],\n"
                + "      \"sensitive_data\": false,\n"
                + "      \"domain\": \"MyApp.Controllers\",\n"
                + "      \"layer\": \"controller\",\n"
                + "      \"calls_out\": [\"MyApp.Services.UserService\"],\n"
                + "      \"calls_in\": [],\n"
                + "      \"annotations\": [\"ApiController\", \"Route\"],\n"
                + "      \"is_interface\": false,\n"
                + "      \"external_dependencies\": [\"Microsoft.AspNetCore.Mvc\"],\n"
                + "      \"implements\": []\n"
                + "    },\n"
                + "    {\n"
                + "      \"id\": \"MyApp.Services.UserService\",\n"
                + "      \"files\": [\"Services/UserService.cs\"],\n"
                + "      \"loc\": 120,\n"
                + "      \"tables_used\": [\"Users\"],\n"
                + "      \"sensitive_data\": true,\n"
                + "      \"domain\": \"MyApp.Services\",\n"
                + "      \"layer\": \"service\",\n"
                + "      \"calls_out\": [],\n"
                + "      \"calls_in\": [\"MyApp.Controllers.HomeController\"],\n"
                + "      \"annotations\": [],\n"
                + "      \"is_interface\": false,\n"
                + "      \"external_dependencies\": [\"Microsoft.EntityFrameworkCore\"],\n"
                + "      \"implements\": [\"MyApp.Services.IUserService\"]\n"
                + "    }\n"
                + "  ],\n"
                + "  \"edges\": [\n"
                + "    {\n"
                + "      \"from\": \"MyApp.Controllers.HomeController\",\n"
                + "      \"to\": \"MyApp.Services.UserService\",\n"
                + "      \"weight\": 3,\n"
                + "      \"type\": \"call\"\n"
                + "    }\n"
                + "  ],\n"
                + "  \"meta\": {\n"
                + "    \"source\": \"roslyn\",\n"
                + "    \"collected_at\": \"2026-01-15T10:00:00Z\"\n"
                + "  }\n"
                + "}";

        // Act
        DependencyGraph graph = analyzer.parseOutput(validJson);

        // Assert: components
        assertNotNull(graph, "Parsed graph should not be null");
        assertNotNull(graph.getComponents(), "Components list should not be null");
        assertEquals(2, graph.getComponents().size(), "Should have 2 components");

        assertEquals("MyApp.Controllers.HomeController", graph.getComponents().get(0).getId());
        assertEquals("controller", graph.getComponents().get(0).getLayer());
        assertEquals(45, graph.getComponents().get(0).getLoc());
        assertFalse(graph.getComponents().get(0).isSensitiveData());

        assertEquals("MyApp.Services.UserService", graph.getComponents().get(1).getId());
        assertEquals("service", graph.getComponents().get(1).getLayer());
        assertEquals(120, graph.getComponents().get(1).getLoc());
        assertTrue(graph.getComponents().get(1).isSensitiveData());
        assertEquals(1, graph.getComponents().get(1).getTablesUsed().size());
        assertEquals("Users", graph.getComponents().get(1).getTablesUsed().get(0));

        // Assert: edges
        assertNotNull(graph.getEdges(), "Edges list should not be null");
        assertEquals(1, graph.getEdges().size(), "Should have 1 edge");
        assertEquals("MyApp.Controllers.HomeController", graph.getEdges().get(0).getFrom());
        assertEquals("MyApp.Services.UserService", graph.getEdges().get(0).getTo());
        assertEquals(3, graph.getEdges().get(0).getWeight());
        assertEquals("call", graph.getEdges().get(0).getType());

        // Assert: meta
        assertNotNull(graph.getMeta(), "Meta should not be null");
        assertEquals("roslyn", graph.getMeta().getSource());
    }

    // --- Empty output ---

    /**
     * [TS-004] When the dotnet-analyzer produces empty stdout, parseOutput should throw.
     */
    @Test
    void analyzeProject_withEmptyOutput_throwsException() {
        // Act & Assert
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> analyzer.parseOutput(""),
                "Empty output should throw RuntimeException");

        assertTrue(ex.getMessage().contains("empty output"),
                "Exception message should indicate empty output");
    }

    /**
     * [TS-004] Null output should also throw.
     */
    @Test
    void analyzeProject_withNullOutput_throwsException() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> analyzer.parseOutput(null),
                "Null output should throw RuntimeException");

        assertTrue(ex.getMessage().contains("empty output"),
                "Exception message should indicate empty output");
    }

    // --- Invalid JSON ---

    /**
     * [TS-005] When the dotnet-analyzer produces malformed JSON, parseOutput should throw.
     */
    @Test
    void analyzeProject_withInvalidJson_throwsException() {
        String malformedJson = "{ this is not valid json }}}";

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> analyzer.parseOutput(malformedJson),
                "Malformed JSON should throw RuntimeException");

        assertTrue(ex.getMessage().contains("Failed to parse"),
                "Exception message should indicate parse failure");
    }

    /**
     * [TS-005] Truncated JSON should also throw.
     */
    @Test
    void analyzeProject_withTruncatedJson_throwsException() {
        String truncatedJson = "{\"components\": [{\"id\": \"Foo\"";

        assertThrows(RuntimeException.class,
                () -> analyzer.parseOutput(truncatedJson),
                "Truncated JSON should throw RuntimeException");
    }

    // --- Dependency resolver ---

    /**
     * [TS-006, TS-007] getDependencyResolver should return a non-null
     * DotNetDependencyResolver instance.
     */
    @Test
    void getDependencyResolver_returnsNonNull() {
        DependencyResolver resolver = analyzer.getDependencyResolver();

        assertNotNull(resolver, "getDependencyResolver() should return a non-null resolver");
        assertInstanceOf(DotNetDependencyResolver.class, resolver,
                "Resolver should be a DotNetDependencyResolver");
    }
}
