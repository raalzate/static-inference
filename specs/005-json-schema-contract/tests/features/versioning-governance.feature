# DO NOT MODIFY SCENARIOS
# These .feature files define expected behavior derived from requirements.
# During implementation:
#   - Write step definitions to match these scenarios
#   - Fix code to pass tests, don't modify .feature files
#   - If requirements change, re-run /iikit-04-testify

@US-006
Feature: Versioning policy, aligned documentation and minimal footprint
  As a maintainer or consumer
  I want a written versioning policy and reference documentation aligned with the formal contract
  So that I know which changes are compatible, when to bump the version and how to migrate

  @TS-040 @FR-019 @P3 @acceptance
  Scenario Outline: The policy classifies each kind of change
    Given the versioning policy document
    When a maintainer looks up the change "<change>"
    Then the document classifies it as a "<bump>" version increment
    And the document states whether a migration note is required as "<migration_note>"

    Examples:
      | change                                   | bump  | migration_note |
      | remove a field                           | major | required       |
      | rename a field                           | major | required       |
      | change the type of a field               | major | required       |
      | add a value to a closed catalogue        | major | required       |
      | add an optional field                    | minor | not required   |
      | clarify a description without validation effect | patch | not required |

  @TS-041 @FR-019 @P3 @acceptance
  Scenario: The policy defines the migration note format
    Given the versioning policy document
    When a maintainer prepares a major version change
    Then the document defines a migration note format with the previous version, the new version, the affected fields and the consumer action

  @TS-042 @FR-020 @SC-006 @P3 @acceptance
  Scenario Outline: Reference documentation no longer contradicts the contract
    Given the reference documents of the contract
    When the topic "<topic>" is looked up
    Then every reference document either matches the formal contract on "<topic>" or points to the formal contract as the source of truth

    Examples:
      | topic                                              |
      | code_issues fields type, severity, message, line, pattern |
      | meta.source values spoon, roslyn, roslyn-fallback  |
      | components_with_secrets is an integer              |
      | secrets_locations is a list of path, line, kind, redacted |
      | shared_domain is a string                          |
      | proposal tables are objects with name, component, source |
      | composite edge types, complexity fields and deprecated interface flag |

  @TS-043 @FR-021 @SC-008 @P1 @validation
  Scenario: No new runtime dependency is added to the deployable engine
    Given the dependency list of the deployable engine artifact before this feature
    When the dependency list after this feature is compared
    Then the runtime dependency list of the fat JAR is unchanged
    And the package references of the .NET analyzer are unchanged
    And the contract validation libraries appear only in test or development scopes

  @TS-044 @SC-007 @P1 @acceptance
  Scenario: Existing test suites keep passing
    Given the Java and .NET test suites that existed before this feature
    When both suites are executed after the feature is implemented
    Then every pre-existing test passes
