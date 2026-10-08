package com.extractor.bdd;

import com.extractor.schema.DemoOutputs;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Step definitions of {@code generated-types.feature} (US4). They delegate to the Node package
 * {@code schema/} ({@code gen/generate.mjs}, {@code npm run check}); skipped when Node ≥ 20 is missing,
 * except TS-033 which only inspects the source tree.
 */
public class GeneratedTypesSteps {

    private static final List<String> KINDS = List.of("dependency-graph", "architecture", "entrypoints",
            "mcp/analyze_project", "mcp/get_dependency_graph", "mcp/get_architecture_proposal",
            "mcp/get_api_contracts", "mcp/get_component_metrics");
    private static final List<String> GENERATED_FILES = List.of("index.ts", "types.ts", "validators.ts");
    private static final String BANNER_LINE = "// GENERATED FILE — DO NOT EDIT.";
    /** Type names of the three documents and their components (schema titles). */
    private static final Pattern TYPE_DECLARATION = Pattern.compile("(?m)^\\s*(?:export\\s+)?(?:declare\\s+)?"
            + "(?:interface|type|class)\\s+(DependencyGraph|ConsolidatedArchitecture|EntrypointsDocument|Component"
            + "|Edge|GraphMeta|ApiContracts|ApiEndpoint|ApiSchema|Proposal|ProposalMetrics|ProjectMetadata"
            + "|LegacyEntrypoint|SupportLibrary|CodeIssue)\\b");

    private Path generatedDir;
    private Path regeneratedDir;
    private Path fixture;
    private ProcessRunner.Result result;
    private final List<Path> declarationFiles = new ArrayList<>();

    private static Path repo() {
        return DemoOutputs.repoRoot();
    }

    private static Path schemaDir() {
        return repo().resolve("schema");
    }

    private static void requireNodePackage() {
        ToolAvailability.requireNode();
        if (!Files.isDirectory(schemaDir().resolve("node_modules"))) {
            ProcessRunner.Result install = ProcessRunner.run(List.of("npm", "--prefix", "schema", "ci"), repo());
            assertEquals(0, install.exitCode(), "npm ci failed: " + install.output());
        }
    }

    private Path generateInto() throws IOException {
        Path out = Files.createTempDirectory("si-005-generated-");
        ProcessRunner.Result gen = ProcessRunner.run(
                List.of("node", "schema/gen/generate.mjs", "--out", out.toString()), repo());
        assertEquals(0, gen.exitCode(), "generation failed: " + gen.output());
        return out;
    }

    // --- TS-028 --------------------------------------------------------------------------------------

    @Given("the contract at its current version")
    public void theContractAtItsCurrentVersion() {
        requireNodePackage();
        assertTrue(Files.isRegularFile(schemaDir().resolve("VERSION")));
    }

    @When("the type generation is executed")
    public void theTypeGenerationIsExecuted() throws IOException {
        generatedDir = generateInto();
    }

    @Then("type definitions exist for {string}, {string}, {string}, {string} and {string}")
    public void typeDefinitionsExistFor(String a, String b, String c, String d, String e) throws IOException {
        String types = Files.readString(generatedDir.resolve("types.ts"), StandardCharsets.UTF_8);
        for (String name : List.of(a, b, c, d, e)) {
            assertTrue(Pattern.compile("export (interface|type) " + name + "\\b").matcher(types).find(),
                    "types.ts declares no " + name);
        }
    }

    @Then("a runtime validator exists for each of the three artifacts and each of the five MCP responses")
    public void aRuntimeValidatorExistsForEachArtifactAndResponse() throws IOException {
        String validators = Files.readString(generatedDir.resolve("validators.ts"), StandardCharsets.UTF_8);
        assertTrue(validators.contains("export const validatorsByKind"), "no validatorsByKind export");
        String registry = validators.substring(validators.indexOf("export const validatorsByKind"));
        registry = registry.substring(0, registry.indexOf("};"));
        for (String kind : KINDS) {
            Pattern entry = Pattern.compile("(?m)^\\s*\"?" + Pattern.quote(kind) + "\"?\\s*:\\s*\\w+Schema,?$");
            assertTrue(entry.matcher(registry).find(), "no validator registered for " + kind);
        }
    }

    // --- TS-029 / TS-030 -------------------------------------------------------------------------------

    @Given("the real output fixture {string}")
    public void theRealOutputFixture(String name) {
        requireNodePackage();
        fixture = schemaDir().resolve("fixtures").resolve(name);
        assertTrue(Files.isRegularFile(fixture), "missing fixture " + fixture);
    }

    @Given("the invalid fixture {string}")
    public void theInvalidFixture(String name) {
        theRealOutputFixture(name);
    }

    @When("it is validated with the generated validator for {string}")
    public void itIsValidatedWithTheGeneratedValidatorFor(String kind) {
        result = ProcessRunner.run(List.of("npm", "--prefix", "schema", "run", "--silent", "check", "--",
                "--file", fixture.toString(), "--kind", kind), repo());
    }

    @Then("the validator accepts it")
    public void theValidatorAcceptsIt() {
        assertEquals(0, result.exitCode(), "validator rejected the fixture: " + result.output());
    }

    @Then("the validator rejects it")
    public void theValidatorRejectsIt() {
        assertNotEquals(0, result.exitCode(), "validator accepted an invalid fixture: " + result.output());
    }

    @Then("the rejection reports the path {string}")
    public void theRejectionReportsThePath(String path) {
        assertTrue(result.output().contains(path + ":"), "rejection does not name " + path + ": " + result.output());
    }

    // --- TS-031 --------------------------------------------------------------------------------------

    @Given("the Java and .NET contract tests have written their validated outputs as samples")
    public void theContractTestsHaveWrittenSamples() throws IOException {
        requireNodePackage();
        DemoOutputs.JAVA_PROJECTS.forEach(DemoOutputs::of);
        Path samples = repo().resolve("target").resolve("schema-samples");
        try (Stream<Path> files = Files.walk(samples)) {
            assertTrue(files.anyMatch(p -> p.toString().endsWith(".json")), "no samples in " + samples);
        }
    }

    @When("the generated validators check every sample")
    public void theGeneratedValidatorsCheckEverySample() {
        result = ProcessRunner.run(List.of("npm", "--prefix", "schema", "run", "--silent", "check", "--",
                "--samples-only"), repo());
    }

    @Then("every sample is accepted")
    public void everySampleIsAccepted() {
        assertEquals(0, result.exitCode(), "samples rejected: " + result.output());
        Matcher m = Pattern.compile("(\\d+) sample\\(s\\) accepted").matcher(result.output());
        assertTrue(m.find() && Integer.parseInt(m.group(1)) > 0, "no sample was checked: " + result.output());
    }

    // --- TS-032 --------------------------------------------------------------------------------------

    @Given("the generated types and validators are up to date with the contract")
    public void theGeneratedTypesAreUpToDate() throws IOException {
        requireNodePackage();
        generatedDir = generateInto();
        assertSameFiles(schemaDir().resolve("generated"), generatedDir);
    }

    @When("the type generation is executed again")
    public void theTypeGenerationIsExecutedAgain() throws IOException {
        regeneratedDir = generateInto();
    }

    @Then("every generated file is byte-identical to the previously generated file")
    public void everyGeneratedFileIsByteIdentical() throws IOException {
        assertSameFiles(generatedDir, regeneratedDir);
        assertSameFiles(schemaDir().resolve("generated"), regeneratedDir);
    }

    // --- TS-033 --------------------------------------------------------------------------------------

    @Given("the repository source tree")
    public void theRepositorySourceTree() {
        assertTrue(Files.isDirectory(schemaDir().resolve("generated")), "schema/generated is missing");
    }

    @When("every type definition of DependencyGraph, ConsolidatedArchitecture, EntrypointsDocument and their components is located")
    public void everyTypeDefinitionIsLocated() throws IOException {
        try (Stream<Path> files = Files.walk(repo())) {
            for (Path file : files.filter(GeneratedTypesSteps::isTypeScriptSource).toList()) {
                if (TYPE_DECLARATION.matcher(Files.readString(file, StandardCharsets.UTF_8)).find()) {
                    declarationFiles.add(repo().relativize(file));
                }
            }
        }
        assertFalse(declarationFiles.isEmpty(), "no type definition found at all");
    }

    @Then("every such definition resides in the generated output of the contract package")
    public void everyDefinitionResidesInTheGeneratedOutput() {
        Path generated = Path.of("schema", "generated");
        List<Path> handWritten = declarationFiles.stream().filter(p -> !p.startsWith(generated)).toList();
        assertTrue(handWritten.isEmpty(), "hand-written contract types found in " + handWritten);
    }

    @Then("every generated file starts with a banner stating it is generated and must not be edited")
    public void everyGeneratedFileStartsWithABanner() throws IOException {
        for (String name : GENERATED_FILES) {
            List<String> head = Files.readAllLines(schemaDir().resolve("generated").resolve(name), StandardCharsets.UTF_8)
                    .stream().limit(3).toList();
            assertTrue(head.contains(BANNER_LINE), name + " has no generated banner: " + head);
        }
    }

    private static boolean isTypeScriptSource(Path p) {
        String s = p.toString();
        if (s.contains("node_modules") || s.contains("/target/") || s.contains("/.git/") || s.contains("/obj/")
                || s.contains("/bin/") || s.contains("/.tessl/") || s.contains("/.specify/")) {
            return false;
        }
        return s.endsWith(".ts") || s.endsWith(".tsx") || s.endsWith(".mts") || s.endsWith(".cts");
    }

    private static void assertSameFiles(Path expectedDir, Path actualDir) throws IOException {
        for (String name : GENERATED_FILES) {
            assertEquals(-1L, Files.mismatch(expectedDir.resolve(name), actualDir.resolve(name)),
                    name + " differs between " + expectedDir + " and " + actualDir);
        }
    }
}
