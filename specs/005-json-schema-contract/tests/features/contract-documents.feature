# DO NOT MODIFY SCENARIOS
# These .feature files define expected behavior derived from requirements.
# During implementation:
#   - Write step definitions to match these scenarios
#   - Fix code to pass tests, don't modify .feature files
#   - If requirements change, re-run /iikit-04-testify

@US-001
Feature: Formal contract for the three output artifacts
  As an integrator consuming Static Inference artifacts
  I want a formal machine-readable contract describing the real current output
  So that I can validate any output without reading the engine source code

  @TS-001 @FR-001 @FR-003 @FR-012 @SC-001 @P1 @acceptance
  Scenario Outline: Java analyzer artifacts validate against the contract
    Given the Java demo project "<project>"
    When the engine analyzes it and writes the three artifacts
    Then the dependency graph validates against the dependency-graph contract with no errors
    And the consolidated architecture validates against the architecture contract with no errors
    And the entrypoints validate against the entrypoints contract with no errors

    Examples:
      | project                 |
      | java-standar            |
      | jee-architecture-master |
      | legacy-erp              |

  @TS-002 @FR-001 @FR-003 @FR-013 @SC-001 @P1 @acceptance
  Scenario Outline: .NET projects analyzed through the engine validate against the contract
    Given the .NET demo project "<project>"
    When the engine analyzes it and writes the three artifacts
    Then the dependency graph validates against the dependency-graph contract with no errors
    And the consolidated architecture validates against the architecture contract with no errors
    And the entrypoints validate against the entrypoints contract with no errors

    Examples:
      | project              |
      | dotnet-ecommerce     |
      | dotnet-legacy-clinic |
      | dotnet-unstructured  |

  @TS-003 @FR-006 @FR-013 @P1 @contract
  Scenario: Raw .NET analyzer graph that omits null properties validates
    Given the .NET analyzer test fixture "SampleProject"
    When the .NET analyzer emits its dependency graph directly on standard output
    Then the emitted graph omits the properties whose value is null
    And the emitted graph validates against the dependency-graph contract with no errors

  @TS-004 @FR-006 @P1 @validation
  Scenario Outline: Removing a required field is rejected with its location
    Given a valid dependency graph produced for "java-standar"
    When the field "<field>" is removed from the first element of "<collection>"
    Then validation against the dependency-graph contract fails
    And the validation error names the path "<path>" and the missing field "<field>"

    Examples:
      | collection | field     | path              |
      | components | id        | /components/0     |
      | components | layer     | /components/0     |
      | edges      | from      | /edges/0          |
      | edges      | weight    | /edges/0          |

  @TS-005 @FR-005 @P1 @validation
  Scenario Outline: Values outside a closed catalogue are rejected
    Given a valid "<document>" produced for "dotnet-ecommerce"
    When the value at "<pointer>" is replaced with "<value>"
    Then validation against the "<document>" contract fails
    And the validation error names the path "<pointer>" and the disallowed value "<value>"

    Examples:
      | document         | pointer                                   | value       |
      | dependency-graph | /components/0/layer                       | Servicio    |
      | dependency-graph | /components/0/code_issues/0/severity      | FATAL       |
      | dependency-graph | /components/0/code_issues/0/type          | SMELL       |
      | dependency-graph | /meta/source                              | dotnet      |
      | architecture     | /proposals/0/viability                    | Alta-Media  |
      | architecture     | /proposals/0/legacy_entrypoint/type       | grpc        |

  @TS-006 @FR-004 @P1 @validation
  Scenario Outline: Undeclared properties in closed objects are rejected
    Given a valid "<document>" produced for "java-standar"
    When an undeclared property "unexpected_field" is added at "<pointer>"
    Then validation against the "<document>" contract fails
    And the validation error names the path "<pointer>" and the unknown property "unexpected_field"

    Examples:
      | document         | pointer                        |
      | dependency-graph | /components/0                  |
      | dependency-graph | /edges/0                       |
      | dependency-graph | /meta                          |
      | architecture     | /proposals/0/metrics           |
      | entrypoints      | /endpoints/0                   |

  @TS-007 @FR-004 @P1 @validation
  Scenario Outline: Dynamic maps accept arbitrary keys
    Given a valid "<document>" produced for "java-standar"
    When a new entry with key "any.Key-123" and a well-formed value is added to the map at "<pointer>"
    Then validation against the "<document>" contract succeeds

    Examples:
      | document         | pointer                                 |
      | dependency-graph | /meta/microservice_candidates           |
      | dependency-graph | /api_contracts/schemas                  |
      | architecture     | /project_metadata/external_dependencies |
      | architecture     | /project_metadata/package_dependencies  |
      | entrypoints      | /schemas                                |

  @TS-008 @FR-005 @P2 @validation
  Scenario Outline: Open-valued fields accept values not seen before
    Given a valid "<document>" produced for "legacy-erp"
    When the value at "<pointer>" is replaced with "<value>"
    Then validation against the "<document>" contract succeeds

    Examples:
      | document         | pointer                                | value                         |
      | dependency-graph | /edges/0/type                          | call,use,injection-constructor |
      | dependency-graph | /components/0/code_issues/0/pattern    | BRAND_NEW_PATTERN             |
      | entrypoints      | /endpoints/0/method                    | GRPC                          |
      | architecture     | /proposals/0/metrics/tables_source     | orm                           |

  @TS-009 @FR-005 @P2 @validation
  Scenario: Reduced-fidelity .NET source is a valid source value
    Given a valid dependency graph produced for "dotnet-ecommerce"
    When the value at "/meta/source" is replaced with "roslyn-fallback"
    Then validation against the dependency-graph contract succeeds

  @TS-010 @FR-007 @P2 @contract
  Scenario: Duplicated interface flag is accepted but marked deprecated
    Given the dependency-graph contract
    When the definition of the component property "interface" is inspected
    Then it is marked as deprecated
    And a component that includes "interface" validates
    And a component that omits "interface" validates

  @TS-011 @FR-006 @P2 @validation
  Scenario: Empty graph is valid
    Given a dependency graph with no components, no edges and no endpoints that declares the current contract version
    When it is validated against the dependency-graph contract
    Then validation succeeds

  @TS-012 @FR-006 @P2 @validation
  Scenario Outline: Proposals with absent or internal legacy entrypoint are valid
    Given a valid consolidated architecture produced for "java-standar"
    When the legacy entrypoint of the first proposal is set to <variant>
    Then validation against the architecture contract succeeds

    Examples:
      | variant                                  |
      | null                                     |
      | absent                                   |
      | an object with only type "internal"      |
