# DO NOT MODIFY SCENARIOS
# These .feature files define expected behavior derived from requirements.
# During implementation:
#   - Write step definitions to match these scenarios
#   - Fix code to pass tests, don't modify .feature files
#   - If requirements change, re-run /iikit-04-testify

@US-005
Feature: Total Java Backward Compatibility
  Existing Java users experience zero change in behavior, output format, or
  performance after the introduction of .NET support. This is a NON-NEGOTIABLE
  constitutional requirement (Principle I).

  Background:
    Given an existing Java project that was analyzed with the previous version

  @TS-041 @FR-010 @SC-005 @P1 @acceptance
  Scenario: Java project analysis produces identical results with the new version
    Given the Java project analysis result from the previous version is stored
    When the same Java project is analyzed with the new version that includes .NET support
    Then the result is identical to the stored reference result

  @TS-042 @FR-010 @SC-005 @P1 @acceptance
  Scenario: CLI arguments and behavior are unchanged for Java projects
    Given the CLI is invoked with the existing Java arguments
    When executed with the new version
    Then the CLI behavior is identical to the current version
    And no new required arguments are introduced

  @TS-043 @FR-010 @SC-005 @SC-007 @P1 @acceptance
  Scenario: All 5 MCP tools return identical responses for Java projects
    Given the 5 MCP tools are invoked over a Java project
    When used with the new version that includes .NET support
    Then each tool response is identical to the response from the previous version
