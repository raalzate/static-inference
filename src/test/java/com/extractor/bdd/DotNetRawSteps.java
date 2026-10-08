package com.extractor.bdd;

import com.extractor.analyzer.DotNetToolLocator;
import com.extractor.schema.DemoOutputs;
import com.extractor.schema.JsonDocs;
import com.extractor.schema.SchemaRegistry;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Steps that run the {@code dotnet-analyzer} executable directly on the .NET test fixtures and inspect its raw
 * standard output, before any reprocessing by the Java engine (TS-003, TS-015). Requires the executable.
 */
public class DotNetRawSteps {

    private static final Path FIXTURES = Path.of("src", "test", "dotnet", "DotNetAnalyzer.Tests", "Fixtures");

    /** Fixture name → path passed to the analyzer (relative to the fixtures directory). */
    private static final Map<String, Path> FIXTURE_TARGETS = Map.of(
            "SampleProject", Path.of("SampleProject"),
            "CodeIssuesSample", Path.of("CodeIssuesSample"),
            "VbLegacy", Path.of("VbLegacy", "MinimalVbProject", "Minimal.vbproj"));

    @Given("the .NET analyzer test fixture {string}")
    public void theDotNetAnalyzerTestFixture(String fixture) {
        ToolAvailability.requireDotnetAnalyzer();
        Path relative = FIXTURE_TARGETS.get(fixture);
        assertTrue(relative != null, "unknown .NET fixture " + fixture);
        Path target = DemoOutputs.repoRoot().resolve(FIXTURES).resolve(relative);
        assertTrue(Files.exists(target), "fixture not found: " + target);
        World.dotnetFixture = target;
    }

    @When("the .NET analyzer emits its dependency graph directly on standard output")
    public void theDotNetAnalyzerEmitsItsDependencyGraph() throws Exception {
        Path exe = new DotNetToolLocator(DemoOutputs.repoRoot().resolve("target")).locate();
        Process process = new ProcessBuilder(List.of(exe.toString(), World.dotnetFixture.toString())).start();
        process.getOutputStream().close();
        CompletableFuture<String> stdout = drain(process.getInputStream());
        CompletableFuture<String> stderr = drain(process.getErrorStream());
        assertTrue(process.waitFor(10, TimeUnit.MINUTES), "dotnet-analyzer timed out");
        assertEquals(0, process.exitValue(), "dotnet-analyzer failed: " + stderr.join());
        World.rawDotnetGraph = stdout.join();
        String sampleName = World.dotnetFixture.getFileName().toString().replace(".vbproj", "");
        DemoOutputs.writeSample("dotnet-raw", sampleName, DemoOutputs.GRAPH, World.rawDotnetGraph);
    }

    @Then("the emitted graph omits the properties whose value is null")
    public void theEmittedGraphOmitsNullProperties() {
        List<String> nulls = JsonDocs.nullPointers(JsonDocs.parse(World.rawDotnetGraph));
        assertTrue(nulls.isEmpty(), "raw .NET graph contains explicit nulls at " + nulls);
    }

    @Then("the emitted graph validates against the dependency-graph contract with no errors")
    public void theEmittedGraphValidates() {
        List<String> errors = SchemaRegistry.get().validate(SchemaRegistry.DEPENDENCY_GRAPH, World.rawDotnetGraph);
        assertTrue(errors.isEmpty(), "raw .NET graph violates the contract: " + errors);
    }

    private static CompletableFuture<String> drain(java.io.InputStream in) {
        return CompletableFuture.supplyAsync(() -> {
            try (in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                in.transferTo(out);
                return out.toString(StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
    }
}
