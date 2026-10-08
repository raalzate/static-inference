# DO NOT MODIFY SCENARIOS
# These .feature files define expected behavior derived from requirements.
# During implementation:
#   - Write step definitions to match these scenarios
#   - Fix code to pass tests, don't modify .feature files
#   - If requirements change, re-run /iikit-04-testify

@US-3 @P3
Feature: Graceful degradation during code issue analysis
  As a static analysis tool processing real-world heterogeneous projects
  I want errors analyzing individual files to not abort the entire analysis
  So that I always deliver a partial result with an indication of what was skipped

  Background:
    Given a RoslynCodeAnalyzer instance
    And a TypeAnalyzer instance

  @TS-024 @FR-005 @acceptance
  Scenario: Component with no .cs files produces empty code_issues (not an error)
    Given a component that references no source files
    When the analyzer runs on the compiled project
    Then the component has code_issues as an empty list
    And the analysis completes without throwing an exception

  @TS-025 @FR-005 @acceptance
  Scenario: Analysis of other components continues when one file cannot be parsed
    Given a .NET project containing one unparseable C# file and one valid C# file with violations
    When the full project analysis pipeline runs
    Then the valid component has its code_issues populated
    And the overall analysis does not throw an unhandled exception
    And a warning is emitted for the unparseable file

  @TS-026 @FR-005 @acceptance
  Scenario: Analyzer does not crash on empty source file
    Given a C# source file that is empty
      """
      """
    When the analyzer runs on the compiled project
    Then no code issues are reported
    And the analysis completes without throwing an exception

  @TS-027 @FR-005 @acceptance
  Scenario: Analyzer does not crash on file with only comments
    Given a C# source file that contains only comments
      """
      // This file intentionally left blank
      /* No code here */
      """
    When the analyzer runs on the compiled project
    Then no code issues are reported
    And the analysis completes without throwing an exception
