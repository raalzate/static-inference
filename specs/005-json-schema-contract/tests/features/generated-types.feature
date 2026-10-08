# DO NOT MODIFY SCENARIOS
# These .feature files define expected behavior derived from requirements.
# During implementation:
#   - Write step definitions to match these scenarios
#   - Fix code to pass tests, don't modify .feature files
#   - If requirements change, re-run /iikit-04-testify

@US-004
Feature: Generated types and runtime validators for typed consumers
  As a developer of the future web application
  I want types and runtime validators generated automatically from the contract
  So that I never maintain hand-written types that drift from the engine output

  @TS-028 @FR-017 @P2 @acceptance
  Scenario: Generation covers the three artifacts and the five MCP responses
    Given the contract at its current version
    When the type generation is executed
    Then type definitions exist for "DependencyGraph", "ConsolidatedArchitecture", "EntrypointsDocument", "AnalyzeProjectResponse" and "Component"
    And a runtime validator exists for each of the three artifacts and each of the five MCP responses

  @TS-029 @FR-017 @P2 @acceptance
  Scenario Outline: Generated validators accept real outputs
    Given the real output fixture "<fixture>"
    When it is validated with the generated validator for "<kind>"
    Then the validator accepts it

    Examples:
      | fixture                                         | kind             |
      | valid/java-standar/output.json                  | dependency-graph |
      | valid/java-standar/output_architecture.json     | architecture     |
      | valid/java-standar/output_entrypoints.json      | entrypoints      |
      | valid/dotnet-ecommerce/output.json              | dependency-graph |
      | valid/dotnet-ecommerce/output_architecture.json | architecture     |
      | valid/dotnet-ecommerce/output_entrypoints.json  | entrypoints      |
      | valid/mcp/analyze_project.json                  | mcp/analyze_project |

  @TS-030 @FR-017 @P2 @validation
  Scenario Outline: Generated validators reject outputs that violate the contract
    Given the invalid fixture "<fixture>"
    When it is validated with the generated validator for "<kind>"
    Then the validator rejects it
    And the rejection reports the path "<path>"

    Examples:
      | fixture                                   | kind             | path                  |
      | invalid/graph-missing-component-id.json   | dependency-graph | components.0.id       |
      | invalid/graph-layer-out-of-catalogue.json | dependency-graph | components.0.layer    |
      | invalid/graph-unknown-property.json       | dependency-graph | components.0          |
      | invalid/graph-missing-schema-version.json | dependency-graph | meta.schema_version   |
      | invalid/architecture-bad-viability.json   | architecture     | proposals.0.viability |

  @TS-031 @FR-017 @P2 @acceptance
  Scenario: Generated validators accept the samples written by the producer contract tests
    Given the Java and .NET contract tests have written their validated outputs as samples
    When the generated validators check every sample
    Then every sample is accepted

  @TS-032 @FR-018 @P2 @acceptance
  Scenario: Regenerating without contract changes produces identical output
    Given the generated types and validators are up to date with the contract
    When the type generation is executed again
    Then every generated file is byte-identical to the previously generated file

  @TS-033 @FR-017 @SC-005 @P2 @validation
  Scenario: No hand-written consumer types exist for the artifacts
    Given the repository source tree
    When every type definition of DependencyGraph, ConsolidatedArchitecture, EntrypointsDocument and their components is located
    Then every such definition resides in the generated output of the contract package
    And every generated file starts with a banner stating it is generated and must not be edited
