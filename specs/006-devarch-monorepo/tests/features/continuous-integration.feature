# DO NOT MODIFY SCENARIOS
# These .feature files define expected behavior derived from requirements.
# During implementation:
#   - Write step definitions to match these scenarios
#   - Fix code to pass tests, don't modify .feature files
#   - If requirements change, re-run /iikit-04-testify

@US-005
Feature: Continuous integration per area
  As a maintainer
  I want each change to trigger only the checks of the affected area
  So that CI is fast and does not affect the other monorepo projects

  @TS-025 @FR-023 @SC-008 @P2 @contract
  Scenario Outline: Changed paths trigger only the checks of their area
    Given the product continuous integration definition
    When a change touches only "<path>"
    Then the triggered checks are exactly "<checks>"

    Examples:
      | path                                                        | checks                                      |
      | apps/dev-arch/web/src/app/page.tsx                          | web                                         |
      | apps/dev-arch/engine/src/main/java/com/extractor/Foo.java   | engine-java, schema                         |
      | apps/dev-arch/engine/src/main/dotnet/DotNetAnalyzer/Foo.cs  | engine-dotnet, schema                       |
      | apps/dev-arch/schema/1.0.0/common.schema.json               | schema, engine-java, engine-dotnet, web     |

  @TS-026 @FR-024 @SC-008 @P2 @contract
  Scenario: Changes outside the product trigger no dev-arch check
    Given the product continuous integration definition
    When a change touches only "machine_learning/notebook.ipynb"
    Then the workflow is not triggered

  @TS-027 @FR-025 @P2 @contract
  Scenario: The contract check job stays blocking with the required toolchain
    Given the product continuous integration definition
    When the jobs are inspected
    Then a job runs "make schema-check" with JDK 17, .NET 8 and Node 20
    And that job is not configured to tolerate failure

  @TS-028 @FR-025 @P2 @contract
  Scenario: Inherited engine quality gates are preserved
    Given the product continuous integration definition
    When the engine jobs are inspected
    Then static analysis runs with the SpotBugs exclude filter
    And Checkstyle runs with a maximum of 4763 allowed violations
    And the .NET tests exclude the category "Corpus"
    And the .NET format verification runs without tolerating failure

  @TS-029 @FR-023 @P2 @contract
  Scenario: Release publishes the engine distribution per platform for dev-arch tags
    Given the product continuous integration definition
    When the release job is inspected
    Then it is triggered by tags matching "dev-arch-v*" and by pushes to main with engine changes
    And it depends on the Java and .NET engine jobs
    And it builds the platforms "linux-x64", "osx-arm64", "osx-x64" and "win-x64" as framework-dependent

  @TS-030 @SC-009 @P2 @validation
  Scenario: Area checks have bounded duration
    Given the product continuous integration definition
    When the jobs are inspected
    Then every area job declares a timeout of at most 20 minutes
