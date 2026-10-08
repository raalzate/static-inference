package com.extractor.bdd;

import com.extractor.schema.DemoOutputs;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.EngineFilter;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Step definitions of {@code versioning-governance.feature} (US6: TS-040..TS-044). */
public class GovernanceSteps {

    /** Reference documents of the contract (FR-020). */
    private static final List<String> REFERENCE_DOCUMENTS = List.of(
            "skills/static-inference-expert-claude/references/json-schema.md",
            "skills/static-inference-expert-kiro/references/json-schema.md",
            "MCP.md",
            "specs/001-mcp-server-local/contracts/mcp-tools.md",
            "specs/002-dotnet-roslyn-analyzer/contracts/dependency-graph-schema.md",
            "specs/003-dotnet-code-issues/contracts/code-issues-schema.md");

    /**
     * Statements that contradict the formal contract, per topic of TS-042 (the divergences listed in the spec).
     * A document that points to the formal contract as the source of truth may keep historical text.
     */
    private static final Map<String, Pattern> CONTRADICTIONS = new LinkedHashMap<>();

    static {
        CONTRADICTIONS.put("code_issues fields type, severity, message, line, pattern",
                Pattern.compile("\"rule\"\\s*:|\"location\"\\s*:|\\{\\s*rule\\s*,"));
        CONTRADICTIONS.put("meta.source values spoon, roslyn, roslyn-fallback",
                Pattern.compile("\"source\"\\s*:\\s*\"dotnet\"|`dotnet`\\s*\\((?:Roslyn|\\.NET)"));
        CONTRADICTIONS.put("components_with_secrets is an integer",
                Pattern.compile("components_with_secrets`?[^\\n]*(string\\[\\]|\\blist\\b|\"\\s*:\\s*\\[)"));
        CONTRADICTIONS.put("secrets_locations is a list of path, line, kind, redacted",
                Pattern.compile("secrets_locations`?[^\\n]*\\bmap\\b|\"secrets_locations\"\\s*:\\s*\\{"));
        CONTRADICTIONS.put("shared_domain is a string",
                Pattern.compile("shared_domain`?\\s*\\|\\s*object|\"shared_domain\"\\s*:\\s*\\{"));
        CONTRADICTIONS.put("proposal tables are objects with name, component, source",
                Pattern.compile("\\|\\s*`tables`\\s*\\|\\s*string\\[\\]"));
        CONTRADICTIONS.put("composite edge types, complexity fields and deprecated interface flag",
                Pattern.compile("`calls`\\s*\\\\?\\|\\s*`extends`|both keys are emitted, same value"));
    }

    private String policy;
    private String[] policyRow;
    private String migrationSection;
    private final Map<String, String> referenceDocs = new LinkedHashMap<>();
    private String topic;
    private Map<String, List<String>> baseline;
    private TestExecutionSummary javaSummary;
    private ProcessRunner.Result dotnetRun;

    private static Path repo() {
        return DemoOutputs.repoRoot();
    }

    // --- TS-040 / TS-041 -------------------------------------------------------------------------------

    @Given("the versioning policy document")
    public void theVersioningPolicyDocument() throws Exception {
        policy = Files.readString(repo().resolve("docs/SCHEMA.md"), StandardCharsets.UTF_8);
        assertTrue(policy.contains("schema/1.0.0"), "policy must point to the formal contract");
    }

    @When("a maintainer looks up the change {string}")
    public void aMaintainerLooksUpTheChange(String change) {
        for (String line : policy.lines().toList()) {
            if (!line.trim().startsWith("|")) {
                continue;
            }
            String[] cells = line.trim().substring(1).split("\\|");
            if (cells.length >= 3 && cells[0].trim().equalsIgnoreCase(change)) {
                policyRow = new String[] {cells[0].trim(), cells[1].trim(), cells[2].trim()};
                return;
            }
        }
        throw new AssertionError("policy table has no row for change '" + change + "'");
    }

    @Then("the document classifies it as a {string} version increment")
    public void theDocumentClassifiesItAs(String bump) {
        assertEquals(bump, policyRow[1].toLowerCase(Locale.ROOT), "increment of " + policyRow[0]);
    }

    @Then("the document states whether a migration note is required as {string}")
    public void theDocumentStatesWhetherAMigrationNoteIsRequired(String migration) {
        assertEquals(migration, policyRow[2].toLowerCase(Locale.ROOT), "migration note of " + policyRow[0]);
    }

    @When("a maintainer prepares a major version change")
    public void aMaintainerPreparesAMajorVersionChange() {
        int start = policy.indexOf("## Migration note format");
        assertTrue(start >= 0, "policy has no 'Migration note format' section");
        int end = policy.indexOf("\n## ", start + 3);
        migrationSection = policy.substring(start, end < 0 ? policy.length() : end).toLowerCase(Locale.ROOT);
        assertTrue(migrationSection.contains("major"), "migration note must be tied to major changes");
    }

    @Then("the document defines a migration note format with the previous version, the new version, the affected fields and the consumer action")
    public void theDocumentDefinesAMigrationNoteFormat() {
        for (String label : List.of("previous version", "new version", "affected fields", "consumer action")) {
            assertTrue(migrationSection.contains("**" + label + "**"), "migration note format lacks '" + label + "'");
        }
    }

    // --- TS-042 --------------------------------------------------------------------------------------

    @Given("the reference documents of the contract")
    public void theReferenceDocumentsOfTheContract() throws Exception {
        for (String doc : REFERENCE_DOCUMENTS) {
            Path path = repo().resolve(doc);
            assertTrue(Files.isRegularFile(path), "missing reference document " + doc);
            referenceDocs.put(doc, Files.readString(path, StandardCharsets.UTF_8));
        }
    }

    @When("the topic {string} is looked up")
    public void theTopicIsLookedUp(String lookedUp) {
        assertTrue(CONTRADICTIONS.containsKey(lookedUp), "unknown topic " + lookedUp);
        topic = lookedUp;
    }

    @Then("every reference document either matches the formal contract on {string} or points to the formal contract as the source of truth")
    public void everyReferenceDocumentMatchesOrPoints(String expectedTopic) {
        assertEquals(topic, expectedTopic);
        List<String> offenders = new ArrayList<>();
        for (Map.Entry<String, String> doc : referenceDocs.entrySet()) {
            boolean points = pointsToContract(doc.getValue());
            Matcher contradiction = CONTRADICTIONS.get(topic).matcher(doc.getValue());
            if (!points && contradiction.find()) {
                offenders.add(doc.getKey() + " (\"" + contradiction.group() + "\")");
            }
        }
        assertTrue(offenders.isEmpty(), "documents contradicting the contract on '" + topic + "': " + offenders);
    }

    private static boolean pointsToContract(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("schema/1.0.0") && (lower.contains("source of truth") || lower.contains("fuente de verdad"));
    }

    // --- TS-043 --------------------------------------------------------------------------------------

    @Given("the dependency list of the deployable engine artifact before this feature")
    public void theDependencyListBeforeThisFeature() throws Exception {
        baseline = new LinkedHashMap<>();
        String section = null;
        for (String line : Files.readAllLines(repo().resolve("specs/005-json-schema-contract/baseline-deps.txt"))) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                section = trimmed.substring(1, trimmed.length() - 1);
                baseline.put(section, new ArrayList<>());
            } else if (section != null && !trimmed.isEmpty() && !trimmed.startsWith("#")) {
                baseline.get(section).add(trimmed);
            }
        }
        assertFalse(baseline.getOrDefault("maven.direct.runtime", List.of()).isEmpty(), "empty Maven baseline");
    }

    @When("the dependency list after this feature is compared")
    public void theDependencyListAfterThisFeatureIsCompared() {
        assertNotNull(baseline);
    }

    @Then("the runtime dependency list of the fat JAR is unchanged")
    public void theRuntimeDependencyListOfTheFatJarIsUnchanged() throws Exception {
        assertEquals(new TreeSet<>(baseline.get("maven.direct.runtime")), new TreeSet<>(mavenDependencies(false)),
                "direct runtime dependencies of pom.xml changed");
        Path jdk = Path.of(System.getProperty("java.home"));
        ProcessRunner.Result list;
        try {
            list = ProcessRunner.run(List.of("mvn", "-B", "-q", "-o", "dependency:list",
                    "-DincludeScope=runtime", "-Dsort=true", "-DoutputFile=target/si-005-runtime-deps.txt"), repo(),
                    Map.of("JAVA_HOME", jdk.toString()), Duration.ofMinutes(5));
        } catch (java.io.UncheckedIOException e) {
            list = null; // mvn not on PATH: the direct-dependency comparison above still applies
        }
        if (list != null && list.exitCode() == 0) {
            List<String> resolved = Files.readAllLines(repo().resolve("target/si-005-runtime-deps.txt")).stream()
                    .map(String::trim).filter(l -> l.matches("^[a-z][\\w.-]*:[\\w.-]+:jar:.*"))
                    .map(l -> l.replaceAll(" --.*$", "")).sorted().toList();
            assertEquals(new TreeSet<>(baseline.get("maven.resolved.runtime")), new TreeSet<>(resolved),
                    "resolved runtime classpath of the fat JAR changed");
        }
    }

    @Then("the package references of the .NET analyzer are unchanged")
    public void thePackageReferencesOfTheDotNetAnalyzerAreUnchanged() throws Exception {
        assertEquals(new TreeSet<>(baseline.get("dotnet.analyzer.packages")),
                new TreeSet<>(packageReferences("src/main/dotnet/DotNetAnalyzer/DotNetAnalyzer.csproj")));
    }

    @Then("the contract validation libraries appear only in test or development scopes")
    public void theContractValidationLibrariesAppearOnlyInTestOrDevelopmentScopes() throws Exception {
        List<String> added = new ArrayList<>(mavenDependencies(true));
        added.removeAll(baseline.get("maven.direct.all"));
        for (String dependency : added) {
            assertTrue(dependency.endsWith(":test"), "dependency added outside test scope: " + dependency);
        }
        assertTrue(packageReferences("src/test/dotnet/DotNetAnalyzer.Tests/DotNetAnalyzer.Tests.csproj").stream()
                .anyMatch(p -> p.startsWith("JsonSchema.Net:")), "JsonSchema.Net must live in the test project");
        assertFalse(packageReferences("src/main/dotnet/DotNetAnalyzer/DotNetAnalyzer.csproj").stream()
                .anyMatch(p -> p.startsWith("JsonSchema.Net:")), "JsonSchema.Net must not reach dotnet-analyzer");
        String packageJson = Files.readString(repo().resolve("schema/package.json"), StandardCharsets.UTF_8);
        assertTrue(packageJson.contains("\"private\": true"), "schema package must be private");
        assertFalse(packageJson.contains("\"dependencies\""), "schema package may only have devDependencies");
    }

    /** Direct dependencies of pom.xml as group:artifact:version:scope (runtime only unless {@code all}). */
    private static List<String> mavenDependencies(boolean all) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document pom = factory.newDocumentBuilder().parse(repo().resolve("pom.xml").toFile());
        Map<String, String> properties = new LinkedHashMap<>();
        NodeList props = ((Element) pom.getElementsByTagName("properties").item(0)).getChildNodes();
        for (int i = 0; i < props.getLength(); i++) {
            if (props.item(i) instanceof Element e) {
                properties.put(e.getTagName(), e.getTextContent().trim());
            }
        }
        Element dependencies = (Element) pom.getElementsByTagName("dependencies").item(0);
        List<String> out = new ArrayList<>();
        NodeList deps = dependencies.getElementsByTagName("dependency");
        for (int i = 0; i < deps.getLength(); i++) {
            Element d = (Element) deps.item(i);
            String version = text(d, "version");
            if (version.startsWith("${")) {
                version = properties.get(version.substring(2, version.length() - 1));
            }
            String scope = text(d, "scope").isEmpty() ? "compile" : text(d, "scope");
            if (all || "compile".equals(scope) || "runtime".equals(scope)) {
                out.add(text(d, "groupId") + ":" + text(d, "artifactId") + ":" + version + ":" + scope);
            }
        }
        return out;
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }

    private static List<String> packageReferences(String csproj) throws Exception {
        String content = Files.readString(repo().resolve(csproj), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("PackageReference Include=\"([^\"]+)\" Version=\"([^\"]+)\"").matcher(content);
        List<String> out = new ArrayList<>();
        while (m.find()) {
            out.add(m.group(1) + ":" + m.group(2));
        }
        return out;
    }

    // --- TS-044 --------------------------------------------------------------------------------------

    @Given("the Java and .NET test suites that existed before this feature")
    public void theJavaAndDotNetTestSuitesThatExistedBefore() {
        ToolAvailability.requireDotnet();
    }

    @When("both suites are executed after the feature is implemented")
    public void bothSuitesAreExecuted() {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectPackage("com.extractor"))
                .filters(EngineFilter.includeEngines("junit-jupiter"),
                        ClassNameFilter.excludeClassNamePatterns("com\\.extractor\\.bdd\\..*",
                                "com\\.extractor\\.schema\\..*"))
                .build();
        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.execute(request, listener);
        javaSummary = listener.getSummary();
        dotnetRun = ProcessRunner.run(List.of("dotnet", "test", "src/test/dotnet/DotNetAnalyzer.Tests/DotNetAnalyzer.Tests.csproj",
                "--filter", "Category!=Corpus&Category!=SchemaContract"), repo(), Map.of(), Duration.ofMinutes(15));
    }

    @Then("every pre-existing test passes")
    public void everyPreExistingTestPasses() {
        StringWriter failures = new StringWriter();
        javaSummary.printFailuresTo(new PrintWriter(failures), 5);
        assertTrue(javaSummary.getTestsSucceededCount() >= 102, "expected the 102 pre-existing Java tests, ran "
                + javaSummary.getTestsSucceededCount());
        assertEquals(0, javaSummary.getTotalFailureCount(), "Java failures:\n" + failures);
        assertEquals(0, dotnetRun.exitCode(), ".NET suite failed:\n" + dotnetRun.output());
    }
}
