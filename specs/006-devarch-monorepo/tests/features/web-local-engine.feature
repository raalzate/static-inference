# DO NOT MODIFY SCENARIOS
# These .feature files define expected behavior derived from requirements.
# During implementation:
#   - Write step definitions to match these scenarios
#   - Fix code to pass tests, don't modify .feature files
#   - If requirements change, re-run /iikit-04-testify

@US-003
Feature: The web analyzes a ZIP with the locally built engine
  As a dev-arch web user in development mode
  I want my uploaded ZIP to be analyzed by the engine I just built
  So that the web always reflects the current engine capabilities

  @TS-014 @FR-013 @FR-016 @SC-002 @P1 @acceptance @slow
  Scenario: A Java demo ZIP is analyzed by the local engine
    Given the product is built
    And the web is running in development mode
    When the ZIP of the demo project "java-standar" is uploaded for analysis
    Then the analysis succeeds within 2 minutes
    And the response contains at least one component and at least one architecture proposal
    And the components data declares the contract version "1.0.0"

  @TS-015 @FR-015 @P1 @acceptance
  Scenario: Analysis without a built engine explains how to build it
    Given the staged engine archive does not exist
    And no engine path override is configured
    When a ZIP is uploaded for analysis
    Then the analysis fails with status 500
    And the error message contains "Engine not built", the path that was tried and "make build"

  @TS-016 @FR-014 @P1 @acceptance
  Scenario: An engine path override is honored
    Given the environment variable "ENGINE_JAR_PATH" points to an existing engine archive outside the web folder
    When the engine is resolved by the web
    Then the resolved engine archive is the one from "ENGINE_JAR_PATH"

  @TS-017 @FR-013 @P1 @contract
  Scenario Outline: Engine resolution rules
    Given the environment <environment>
    And the web working directory is "<cwd>"
    When the engine is resolved by the web
    Then the resolved Java executable is "<java>"
    And the resolved engine archive is "<jar>"

    Examples:
      | environment                                            | cwd     | java                 | jar                                   |
      | has no ENGINE_JAR_PATH and no JAVA_HOME                | /p/web  | java                 | /p/web/engine-dist/engine.jar         |
      | has JAVA_HOME "/jdk17"                                 | /p/web  | /jdk17/bin/java      | /p/web/engine-dist/engine.jar         |
      | has ENGINE_JAR_PATH "../engine/target/java-dependency-extractor.jar" | /p/web | java | /p/engine/target/java-dependency-extractor.jar |

  @TS-018 @FR-013 @P1 @validation
  Scenario: Project paths with spaces are passed to the engine intact
    Given the extracted project path contains spaces
    When the web invokes the engine
    Then the engine receives the project path as a single argument without a shell

  @TS-019 @FR-013 @SC-002 @P1 @acceptance @slow
  Scenario: A .NET demo ZIP is analyzed when the .NET SDK is available
    Given the product is built with the .NET analyzer staged next to the engine
    And the web is running in development mode
    When the ZIP of the demo project "dotnet-ecommerce" is uploaded for analysis
    Then the analysis succeeds
    And the components data declares a .NET source
