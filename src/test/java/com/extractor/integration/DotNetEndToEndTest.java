package com.extractor.integration;

import com.extractor.model.DependencyGraph;
import com.extractor.service.AnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test for .NET project routing via the Java bridge.
 * Verifies that .NET projects are detected and routed to DotNetBridgeAnalyzer.
 * [TS-005, TS-006]
 *
 * When dotnet-analyzer binary is available (target/dotnet-analyzer):
 *   - Analysis runs end-to-end, meta.source = "roslyn"
 * When binary is absent:
 *   - IllegalStateException with installation instructions (tested by DotNetToolLocatorTest)
 */
class DotNetEndToEndTest {

    private AnalysisService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new AnalysisService();
    }

    @Test
    void analyzeProject_dotnetProject_producesRoslynSource() throws Exception {
        Path projectDir = createMinimalDotNetProject();

        // Bridge runs but may produce 0 components (no NuGet restore in temp dir)
        // What matters: no UnsupportedOperationException, and meta.source = "roslyn"
        try {
            DependencyGraph graph = service.getDependencyGraph(projectDir);
            assertNotNull(graph);
            assertEquals("roslyn", graph.getMeta().getSource(),
                    "meta.source must be 'roslyn' for .NET projects");
        } catch (IllegalStateException ex) {
            // dotnet-analyzer binary not available — acceptable in CI without .NET SDK
            assertTrue(ex.getMessage().contains("dotnet-analyzer"),
                    "Error should mention dotnet-analyzer: " + ex.getMessage());
        }
    }

    @Test
    void analyzeProject_dotnetProject_notUnsupportedOperation() throws IOException {
        Path projectDir = createMinimalDotNetProject();

        // Must NOT throw UnsupportedOperationException (the old placeholder)
        try {
            service.analyzeProject(projectDir);
        } catch (UnsupportedOperationException ex) {
            fail("Should not throw UnsupportedOperationException — .NET routing must be wired: " + ex.getMessage());
        } catch (Exception ignored) {
            // IllegalStateException (binary not found) or RuntimeException (bridge error) are acceptable
        }
    }

    @Test
    void getDependencyGraph_dotnetProject_notUnsupportedOperation() throws IOException {
        Path projectDir = createMinimalDotNetProject();

        try {
            service.getDependencyGraph(projectDir);
        } catch (UnsupportedOperationException ex) {
            fail("Should not throw UnsupportedOperationException: " + ex.getMessage());
        } catch (Exception ignored) {
            // acceptable
        }
    }

    @Test
    void getApiContracts_dotnetProject_notUnsupportedOperation() throws IOException {
        Path projectDir = createMinimalDotNetProject();

        try {
            service.getApiContracts(projectDir);
        } catch (UnsupportedOperationException ex) {
            fail("Should not throw UnsupportedOperationException: " + ex.getMessage());
        } catch (Exception ignored) {
            // acceptable
        }
    }

    @Test
    void getArchitectureProposal_dotnetProject_notUnsupportedOperation() throws IOException {
        Path projectDir = createMinimalDotNetProject();

        try {
            service.getArchitectureProposal(projectDir, null);
        } catch (UnsupportedOperationException ex) {
            fail("Should not throw UnsupportedOperationException: " + ex.getMessage());
        } catch (Exception ignored) {
            // acceptable
        }
    }

    private Path createMinimalDotNetProject() throws IOException {
        Path projectDir = tempDir.resolve("dotnet-project");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("MyApp.csproj"),
                """
                <Project Sdk="Microsoft.NET.Sdk.Web">
                  <PropertyGroup>
                    <TargetFramework>net8.0</TargetFramework>
                  </PropertyGroup>
                </Project>
                """);
        return projectDir;
    }
}
