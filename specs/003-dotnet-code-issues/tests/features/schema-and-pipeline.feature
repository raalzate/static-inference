# DO NOT MODIFY SCENARIOS
# These .feature files define expected behavior derived from requirements.
# During implementation:
#   - Write step definitions to match these scenarios
#   - Fix code to pass tests, don't modify .feature files
#   - If requirements change, re-run /iikit-04-testify

@US-2 @P2
Feature: Unified CodeIssue schema and pipeline integration
  As a consumer of the MCP Server
  I want the CodeIssue model in .NET to have a canonical documented schema
  So that I can build tools that consume the output predictably regardless of the language analyzed

  Background:
    Given a RoslynCodeAnalyzer instance
    And a TypeAnalyzer instance

  @TS-018 @FR-002 @FR-004 @SC-005 @acceptance
  Scenario: Every detected code issue has all required fields populated
    Given a C# class with an empty catch block
      """
      namespace Sample {
          public class Service {
              public void DoWork() {
                  try { var x = 1; }
                  catch (Exception e) { }
              }
          }
      }
      """
    When the analyzer runs on the compiled project
    Then each code issue in component "Sample.Service" has a non-null "rule" field
    And each code issue in component "Sample.Service" has a non-null "severity" field
    And each code issue in component "Sample.Service" has a non-null "message" field
    And each code issue in component "Sample.Service" has a "location" field matching ":\d+$"

  @TS-019 @FR-002 @acceptance
  Scenario: Severity values are restricted to the defined enum
    Given a C# class with multiple pattern violations
      """
      namespace Sample {
          public class Service {
              public void DoWork() {
                  try { } catch (Exception e) { }
                  Console.WriteLine("debug");
                  var password = "secret";
              }
          }
      }
      """
    When the analyzer runs on the compiled project
    Then all code issues in component "Sample.Service" have severity in ["INFO", "WARNING", "ERROR", "CRITICAL"]

  @TS-020 @FR-003 @FR-004 @SC-001 @acceptance
  Scenario: Analysis runs automatically as part of the .NET pipeline without extra steps
    Given a .NET project directory with C# files containing bug patterns
    When the full project analysis pipeline runs via ProjectAnalyzer
    Then the output dependency graph contains components with non-empty code_issues arrays
    And the analysis completes without requiring manual intervention

  @TS-021 @FR-004 @acceptance
  Scenario: Components without patterns always have an empty code_issues array (not null)
    Given a .NET project with a clean C# class
      """
      namespace Sample {
          public class Clean {
              public string Value { get; set; } = string.Empty;
          }
      }
      """
    When the analyzer runs on the compiled project
    Then the component "Sample.Clean" has code_issues as an empty list (not null)

  @TS-022 @FR-007 @SC-003 @acceptance
  Scenario: Analysis produces identical code_issues output across multiple runs
    Given a C# project with known bug patterns
    When the full project analysis pipeline runs three times on the same input
    Then the code_issues list is identical in all three runs
    And the issue order is the same in all three runs

  @TS-023 @FR-007 @acceptance
  Scenario: Code issues are ordered deterministically by location
    Given a C# class with multiple violations in different lines
      """
      namespace Sample {
          public class Service {
              public void DoWork() {
                  Console.WriteLine("a");
                  try { } catch (Exception e) { }
                  Console.Write("b");
              }
          }
      }
      """
    When the analyzer runs on the compiled project
    Then the code issues in component "Sample.Service" are sorted by location ascending
