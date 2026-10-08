# DO NOT MODIFY SCENARIOS
# These .feature files define expected behavior derived from requirements.
# During implementation:
#   - Write step definitions to match these scenarios
#   - Fix code to pass tests, don't modify .feature files
#   - If requirements change, re-run /iikit-04-testify

@US-002
Feature: Automatic Project Type Detection
  The tool automatically detects whether a project is Java or .NET based on the
  marker files present in the directory, selecting the correct analyzer without
  requiring the user to specify the language explicitly.

  @TS-011 @FR-001 @SC-003 @P1 @acceptance
  Scenario Outline: Correct analyzer is selected based on marker files
    Given a directory containing "<marker_file>"
    When the analysis is invoked without specifying a language
    Then the system automatically uses the "<analyzer>" analyzer
    And the result is a valid DependencyGraph for the detected project type

    Examples:
      | marker_file  | analyzer |
      | project.sln  | .NET     |
      | app.csproj   | .NET     |
      | pom.xml      | Java     |
      | build.gradle | Java     |

  @TS-012 @FR-001 @FR-010 @SC-003 @P1 @acceptance
  Scenario: Java project analysis is preserved when only Java marker files are present
    Given a directory that contains "pom.xml" or "build.gradle" but no ".sln" or ".csproj" files
    When the analysis is invoked
    Then the system automatically uses the Java analyzer
    And the behavior is identical to the current version

  @TS-013 @FR-001 @FR-011 @SC-003 @P1 @acceptance
  Scenario: Mixed project triggers AMBIGUOUS error with guidance
    Given a directory that contains both ".csproj" and "pom.xml" files
    When the analysis is invoked
    Then the system reports an AMBIGUOUS project type error
    And the error message instructs the user to specify the target language explicitly

  @TS-014 @FR-001 @FR-011 @SC-003 @P1 @acceptance
  Scenario: Directory with no recognized markers triggers UNKNOWN error
    Given a directory that contains neither Java nor .NET marker files
    When the analysis is invoked
    Then the system returns a clear error indicating no recognized project type was found
    And the error message lists the supported marker files

  @TS-015 @FR-013 @P2 @acceptance
  Scenario: .NET Framework project is analyzed correctly
    Given a .NET Framework project (not .NET Core) with a ".csproj" targeting "net48" or earlier
    When the analysis is invoked
    Then the system successfully analyzes the project using the .NET analyzer
    And the result contains a valid DependencyGraph
