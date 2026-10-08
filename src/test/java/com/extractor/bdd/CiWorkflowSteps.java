package com.extractor.bdd;

import com.extractor.schema.DemoOutputs;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Step definitions of TS-039: the CI workflow runs schema-check as a blocking job. */
public class CiWorkflowSteps {

    private Map<String, Object> workflow;
    private Map<String, Object> schemaCheckJob;

    @Given("the continuous integration workflow definition")
    public void theContinuousIntegrationWorkflowDefinition() throws IOException {
        String yaml = Files.readString(DemoOutputs.repoRoot().resolve(".github/workflows/ci.yml"), StandardCharsets.UTF_8);
        workflow = cast(new Load(LoadSettings.builder().build()).loadFromString(yaml));
        assertNotNull(workflow.get("jobs"), "workflow has no jobs");
    }

    @When("the jobs are inspected")
    public void theJobsAreInspected() {
        Map<String, Object> jobs = cast(workflow.get("jobs"));
        for (Object job : jobs.values()) {
            Map<String, Object> definition = cast(job);
            if (steps(definition).stream().anyMatch(s -> String.valueOf(s.get("run")).contains("make schema-check"))) {
                schemaCheckJob = definition;
            }
        }
        assertNotNull(schemaCheckJob, "no job runs make schema-check");
    }

    @Then("a job runs {string} with JDK {int}, .NET {int} and Node {int}")
    public void aJobRunsWith(String command, int jdk, int dotnet, int node) {
        assertTrue(steps(schemaCheckJob).stream().anyMatch(s -> String.valueOf(s.get("run")).contains(command)));
        assertTrue(hasSetup("actions/setup-java", "java-version", String.valueOf(jdk)), "no JDK " + jdk);
        assertTrue(hasSetup("actions/setup-dotnet", "dotnet-version", dotnet + "."), "no .NET " + dotnet);
        assertTrue(hasSetup("actions/setup-node", "node-version", String.valueOf(node)), "no Node " + node);
    }

    @Then("that job is not configured to tolerate failure")
    public void thatJobIsNotConfiguredToTolerateFailure() {
        assertFalse(isTrue(schemaCheckJob.get("continue-on-error")), "job tolerates failure");
        for (Map<String, Object> step : steps(schemaCheckJob)) {
            if (String.valueOf(step.get("run")).contains("make schema-check")) {
                assertFalse(isTrue(step.get("continue-on-error")), "schema-check step tolerates failure");
                assertFalse(String.valueOf(step.get("run")).contains("|| true"), "schema-check failure is masked");
            }
        }
    }

    private boolean hasSetup(String action, String key, String versionPrefix) {
        return steps(schemaCheckJob).stream()
                .filter(s -> String.valueOf(s.get("uses")).startsWith(action + "@"))
                .map(s -> s.get("with"))
                .filter(Objects::nonNull)
                .map(w -> String.valueOf(CiWorkflowSteps.<Map<String, Object>>cast(w).get(key)))
                .anyMatch(v -> v.equals(versionPrefix) || v.startsWith(versionPrefix));
    }

    private static boolean isTrue(Object value) {
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static List<Map<String, Object>> steps(Map<String, Object> job) {
        Object steps = job.get("steps");
        return steps == null ? List.of() : CiWorkflowSteps.<List<Map<String, Object>>>cast(steps);
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }
}
