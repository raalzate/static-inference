package com.extractor.bdd;

import com.extractor.schema.DemoOutputs;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Step definitions of {@code schema-check.feature} (US5). Each scenario copies the working tree (tracked and
 * untracked, non-ignored files) to a temporary directory, introduces the divergence there, optionally hides a
 * tool from the {@code PATH}, and runs {@code make schema-check} end to end.
 * <p>
 * Long-running: enabled only with {@code SCHEMA_CHECK_E2E=true} (see {@link ToolAvailability}) and never from
 * inside schema-check itself.
 */
public class SchemaCheckSteps {

    private static final Pattern FRONT_LINE =
            Pattern.compile("^\\[schema-check\\] (version|java|dotnet|ts): (OK|FAIL|NOT VERIFIED)( — .*)?$");
    private static final Pattern RESULT_LINE = Pattern.compile("^\\[schema-check\\] RESULT: (OK|FAIL)\\b.*$");
    /** Result of the run over the unmodified working tree, shared by TS-034 and TS-038. */
    private static ProcessRunner.Result alignedRun;

    private Path workCopy;
    private boolean modified;
    private String hiddenTool;
    private ProcessRunner.Result result;

    // --- Givens --------------------------------------------------------------------------------------

    @Given("Java, .NET and the generated types are aligned with the contract")
    public void javaDotNetAndGeneratedTypesAreAligned() throws IOException {
        ToolAvailability.requireSchemaCheckE2e();
        workCopy = copyWorkingTree();
    }

    @Given("JDK 17, the .NET SDK and Node 20 are available")
    public void jdkDotNetAndNodeAreAvailable() {
        assertTrue(Runtime.version().feature() >= 17, "JDK 17+ required");
        ToolAvailability.requireDotnet();
        ToolAvailability.requireNode();
    }

    @Given("an undeclared property {string} on every Java component is introduced")
    public void anUndeclaredPropertyOnEveryJavaComponent(String property) throws IOException {
        replaceIn("src/main/java/com/extractor/model/Component.java", "public class Component {",
                "public class Component {\n\n    @JsonProperty(\"" + property + "\")\n    private String "
                        + property + "Divergence = \"divergent\";\n");
    }

    @Given("a .NET fixture component whose layer is {string} is introduced")
    public void aDotNetFixtureComponentWhoseLayerIs(String layer) throws IOException {
        replaceIn("src/main/dotnet/DotNetAnalyzer/Analysis/ProjectAnalyzer.cs", "_layerClassifier.Classify(components);",
                "_layerClassifier.Classify(components);\n                foreach (var divergent in components) "
                        + "{ divergent.Layer = \"" + layer + "\"; }");
    }

    @Given("a contract change without regenerating the generated types is introduced")
    public void aContractChangeWithoutRegenerating() throws IOException {
        replaceIn("schema/" + version() + "/common.schema.json", "Unit of analysis (class/interface/enum or .NET type).",
                "Unit of analysis (class, interface, enum or .NET type) — divergent description.");
    }

    @Given("the contract declares version {string}")
    public void theContractDeclaresVersion(String version) throws IOException {
        ToolAvailability.requireSchemaCheckE2e();
        workCopy = copyWorkingTree();
        assertEquals(version, version());
    }

    @Given("the Java version constant is {string}")
    public void theJavaVersionConstantIs(String version) throws IOException {
        replaceIn("src/main/java/com/extractor/model/SchemaVersion.java", "CURRENT = \"" + version() + "\"",
                "CURRENT = \"" + version + "\"");
    }

    @Given("the .NET version constant is {string}")
    public void theDotNetVersionConstantIs(String version) throws IOException {
        replaceIn("src/main/dotnet/DotNetAnalyzer/Model/SchemaVersion.cs", "Current = \"" + version() + "\"",
                "Current = \"" + version + "\"");
    }

    @Given("the tool {string} is not available")
    public void theToolIsNotAvailable(String tool) throws IOException {
        ToolAvailability.requireSchemaCheckE2e();
        workCopy = copyWorkingTree();
        hiddenTool = tool;
    }

    // --- When ----------------------------------------------------------------------------------------

    @When("{string} is executed")
    public void isExecuted(String command) throws IOException {
        assertEquals("make schema-check", command);
        if (workCopy == null) {
            ToolAvailability.requireSchemaCheckE2e();
        }
        boolean aligned = workCopy == null || (!modified && hiddenTool == null);
        if (aligned && alignedRun != null) {
            result = alignedRun;
            return;
        }
        if (workCopy == null) {
            workCopy = copyWorkingTree();
        }
        Map<String, String> env = new HashMap<>();
        env.put("MVN_ARGS", "-o");
        env.put("JAVA_HOME", System.getProperty("java.home"));
        List<String> cmd = new ArrayList<>(List.of("env"));
        if (hiddenTool != null) {
            if ("java".equals(hiddenTool)) {
                cmd.addAll(List.of("-u", "JAVA_HOME"));
            }
            cmd.add("PATH=" + shimPath(hiddenTool));
        }
        cmd.addAll(List.of("make", "schema-check"));
        result = ProcessRunner.run(cmd, workCopy, env, Duration.ofMinutes(20));
        if (aligned) {
            alignedRun = result;
        }
    }

    // --- Thens ---------------------------------------------------------------------------------------

    @Then("it reports {string} for the fronts {string}, {string}, {string} and {string}")
    public void itReportsForTheFronts(String status, String a, String b, String c, String d) {
        for (String front : List.of(a, b, c, d)) {
            assertEquals(status, frontStatus(front), "front " + front + " in:\n" + result.output());
        }
    }

    @Then("it reports {string} for the front {string}")
    public void itReportsForTheFront(String status, String front) {
        assertEquals(status, frontStatus(front), "front " + front + " in:\n" + result.output());
    }

    @Then("it exits with code {int}")
    public void itExitsWithCode(int code) {
        assertEquals(code, result.exitCode(), result.output());
    }

    @Then("it exits with a non-zero code")
    public void itExitsWithANonZeroCode() {
        assertNotEquals(0, result.exitCode(), result.output());
    }

    @Then("it completes in less than {int} minutes")
    public void itCompletesInLessThanMinutes(int minutes) {
        assertTrue(result.elapsed().compareTo(Duration.ofMinutes(minutes)) < 0, "took " + result.elapsed());
    }

    @Then("the failure message names the unknown property {string}")
    public void theFailureMessageNamesTheUnknownProperty(String property) {
        String line = frontLine("java");
        assertTrue(line.contains(property), "java failure does not name '" + property + "': " + line);
    }

    @Then("the failure message names the path of the layer field")
    public void theFailureMessageNamesThePathOfTheLayerField() {
        String line = frontLine("dotnet");
        assertTrue(Pattern.compile("/components/\\d+/layer").matcher(line).find(),
                "dotnet failure does not name the layer path: " + line);
    }

    @Then("the failure message names the out-of-date generated file")
    public void theFailureMessageNamesTheOutOfDateGeneratedFile() {
        String line = frontLine("ts");
        assertTrue(Pattern.compile("generated/\\w+\\.ts out of date").matcher(line).find(),
                "ts failure does not name the out-of-date file: " + line);
    }

    @Then("the failure message names {string} and {string}")
    public void theFailureMessageNamesAnd(String first, String second) {
        String line = frontLine("version");
        assertTrue(line.contains(first) && line.contains(second), "version failure: " + line);
    }

    @Then("the summary reports the result as {string}")
    public void theSummaryReportsTheResultAs(String expected) {
        List<String> lines = outputLines();
        java.util.regex.Matcher m = RESULT_LINE.matcher(lines.get(lines.size() - 1));
        assertTrue(m.matches(), "last line is not a RESULT line: " + lines.get(lines.size() - 1));
        assertEquals(expected, m.group(1));
    }

    @Then("every front produces exactly one line matching {string}")
    public void everyFrontProducesExactlyOneLineMatching(String format) {
        assertEquals("[schema-check] <front>: OK|FAIL|NOT VERIFIED", format);
        for (String front : List.of("version", "java", "dotnet", "ts")) {
            long count = outputLines().stream()
                    .filter(l -> l.startsWith("[schema-check] " + front + ":"))
                    .filter(l -> FRONT_LINE.matcher(l).matches())
                    .count();
            assertEquals(1, count, "lines for front " + front + " in:\n" + result.output());
        }
    }

    @Then("the last line matches {string}")
    public void theLastLineMatches(String format) {
        assertEquals("[schema-check] RESULT: OK|FAIL", format);
        List<String> lines = outputLines();
        assertTrue(RESULT_LINE.matcher(lines.get(lines.size() - 1)).matches(), "last line: " + lines.get(lines.size() - 1));
    }

    // --- helpers -------------------------------------------------------------------------------------

    private List<String> outputLines() {
        return result.output().lines().filter(l -> l.startsWith("[schema-check] ")
                && !l.startsWith("[schema-check]   ")).toList();
    }

    private String frontLine(String front) {
        return outputLines().stream().filter(l -> l.startsWith("[schema-check] " + front + ":")).findFirst()
                .orElseThrow(() -> new AssertionError("no line for front " + front + ":\n" + result.output()));
    }

    private String frontStatus(String front) {
        java.util.regex.Matcher m = FRONT_LINE.matcher(frontLine(front));
        assertTrue(m.matches(), "malformed line for " + front);
        return m.group(2);
    }

    private static String version() throws IOException {
        return Files.readString(DemoOutputs.repoRoot().resolve("schema/VERSION"), StandardCharsets.UTF_8).trim();
    }

    private void replaceIn(String relative, String target, String replacement) throws IOException {
        Path file = workCopy.resolve(relative);
        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.contains(target), relative + " does not contain " + target);
        Files.writeString(file, content.replace(target, replacement), StandardCharsets.UTF_8);
        modified = true;
    }

    /** Copies tracked and untracked (non-ignored) files of the repository into a fresh temporary directory. */
    private static Path copyWorkingTree() throws IOException {
        Path repo = DemoOutputs.repoRoot();
        ProcessRunner.Result files = ProcessRunner.run(List.of("git", "ls-files", "-co", "--exclude-standard"), repo);
        assertEquals(0, files.exitCode(), files.output());
        Path copy = Files.createTempDirectory("si-005-schema-check-");
        for (String line : files.output().lines().toList()) {
            Path source = repo.resolve(line);
            if (!Files.isRegularFile(source)) {
                continue;
            }
            Path target = copy.resolve(line);
            Files.createDirectories(target.getParent());
            Files.copy(source, target, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
        }
        return copy;
    }

    /**
     * Builds a directory of symlinks to every executable on the current PATH except the hidden tool, so the
     * tool is "not available" for schema-check while everything else still resolves.
     */
    private static Path shimPath(String tool) throws IOException {
        Set<String> hidden = switch (tool) {
            case "java" -> Set.of("java", "javac", "jshell", "jar");
            case "node" -> Set.of("node", "npm", "npx", "corepack");
            case "dotnet" -> Set.of("dotnet");
            default -> throw new IllegalArgumentException("Unknown tool " + tool);
        };
        Path shim = Files.createTempDirectory("si-005-path-without-" + tool + "-");
        Set<String> seen = new LinkedHashSet<>();
        for (String dir : System.getenv("PATH").split(File.pathSeparator)) {
            File[] entries = new File(dir).listFiles();
            if (entries == null) {
                continue;
            }
            for (File entry : entries) {
                String name = entry.getName();
                if (hidden.contains(name) || !entry.canExecute() || entry.isDirectory() || !seen.add(name)) {
                    continue;
                }
                Files.createSymbolicLink(shim.resolve(name), entry.toPath().toAbsolutePath());
            }
        }
        return shim;
    }
}
