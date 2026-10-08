package com.extractor.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectDirectories;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PUBLISH_QUIET_PROPERTY_NAME;

/**
 * Cucumber JVM runner for the hash-locked scenarios of 005-json-schema-contract (research D11).
 * <p>
 * Runs the {@code .feature} files of {@code specs/005-json-schema-contract/tests/features} (surefire
 * {@code features.dir}, relative to the Maven base directory) with the step definitions of this package.
 * Steps that need the .NET SDK, Node or the end-to-end {@code schema-check} run are skipped with an explicit
 * reason when the tool is not available (see {@link ToolAvailability}).
 * <p>
 * Not tagged {@code schema-contract} on purpose: {@code schema-check} runs the JUnit contract tests with
 * {@code -Dgroups=schema-contract}, and the schema-check scenarios themselves invoke {@code schema-check}.
 */
@Suite
@SuiteDisplayName("005-json-schema-contract BDD scenarios")
@IncludeEngines("cucumber")
@SelectDirectories("specs/005-json-schema-contract/tests/features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.extractor.bdd")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "summary, json:target/cucumber-reports/schema-contract.json")
@ConfigurationParameter(key = PLUGIN_PUBLISH_QUIET_PROPERTY_NAME, value = "true")
public class RunSchemaContractFeaturesTest {
}
