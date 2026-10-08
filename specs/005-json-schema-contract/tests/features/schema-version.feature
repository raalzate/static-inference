# DO NOT MODIFY SCENARIOS
# These .feature files define expected behavior derived from requirements.
# During implementation:
#   - Write step definitions to match these scenarios
#   - Fix code to pass tests, don't modify .feature files
#   - If requirements change, re-run /iikit-04-testify

@US-002
Feature: Contract version declared in every output
  As a consumer of the artifacts
  I want each output to declare the contract version it was produced with
  So that I can detect incompatibilities before interpreting the data

  @TS-013 @FR-008 @SC-003 @P1 @acceptance
  Scenario Outline: Every Java artifact declares schema version 1.0.0
    Given the Java demo project "java-standar"
    When the engine analyzes it and writes the three artifacts
    Then the artifact "<artifact>" contains "meta.schema_version" equal to "1.0.0"

    Examples:
      | artifact                  |
      | output.json               |
      | output_architecture.json  |
      | output_entrypoints.json   |

  @TS-014 @FR-008 @SC-003 @P1 @acceptance
  Scenario Outline: Every artifact of a .NET analysis declares schema version 1.0.0
    Given the .NET demo project "dotnet-ecommerce"
    When the engine analyzes it and writes the three artifacts
    Then the artifact "<artifact>" contains "meta.schema_version" equal to "1.0.0"

    Examples:
      | artifact                  |
      | output.json               |
      | output_architecture.json  |
      | output_entrypoints.json   |

  @TS-015 @FR-009 @P1 @acceptance
  Scenario Outline: The .NET analyzer declares the version before engine reprocessing
    Given the .NET analyzer test fixture "<fixture>"
    When the .NET analyzer emits its dependency graph directly on standard output
    Then the emitted graph contains "meta.schema_version" equal to "1.0.0"
    And "meta.schema_version" is serialized immediately after "meta.source"

    Examples:
      | fixture          |
      | SampleProject    |
      | CodeIssuesSample |
      | VbLegacy         |

  @TS-016 @FR-008 @P1 @validation
  Scenario Outline: Missing or malformed schema version is rejected
    Given a valid "<document>" produced for "java-standar"
    When "meta.schema_version" is <mutation>
    Then validation against the "<document>" contract fails
    And the validation error names the path "/meta" or "/meta/schema_version"

    Examples:
      | document         | mutation              |
      | dependency-graph | removed               |
      | dependency-graph | set to "1.0"          |
      | dependency-graph | set to "latest"       |
      | dependency-graph | set to the number 1   |
      | architecture     | removed               |
      | entrypoints      | removed               |

  @TS-017 @FR-010 @FR-011 @SC-003 @P1 @acceptance
  Scenario Outline: Two consecutive runs remain byte-identical
    Given the timestamp opt-in "STATIC_INFERENCE_COLLECTED_AT" is not enabled
    And the demo project "<project>"
    When the engine analyzes the project twice into two different output directories
    Then the three artifacts of both runs are byte-identical
    And no artifact contains "meta.collected_at"

    Examples:
      | project          |
      | java-standar     |
      | dotnet-ecommerce |

  @TS-018 @FR-010 @P2 @validation
  Scenario Outline: Optional collection timestamp is validated only when present
    Given a valid dependency graph produced for "java-standar"
    When "meta.collected_at" is <state>
    Then validation against the dependency-graph contract <result>

    Examples:
      | state                                   | result   |
      | absent                                  | succeeds |
      | set to "2026-10-07T12:34:56.789Z"       | succeeds |
      | set to "07/10/2026 12:34"               | fails    |

  @TS-019 @FR-010 @P2 @acceptance
  Scenario: Enabling the timestamp opt-in emits a valid ISO 8601 timestamp
    Given the timestamp opt-in "STATIC_INFERENCE_COLLECTED_AT" is enabled
    And the Java demo project "java-standar"
    When the engine analyzes it and writes the three artifacts
    Then the dependency graph contains "meta.collected_at" as an ISO 8601 date-time
    And the dependency graph validates against the dependency-graph contract with no errors

  @TS-020 @FR-011 @SC-007 @P1 @acceptance
  Scenario Outline: Adding the version changes nothing else in the output
    Given the baseline artifacts produced by release v1.1.0 for "<project>"
    When the current engine produces the same artifacts
    And "meta.schema_version" and the newly added "meta" objects are removed from the current artifacts
    Then the current artifacts are byte-identical to the baseline artifacts

    Examples:
      | project          |
      | java-standar     |
      | dotnet-ecommerce |

  @TS-021 @FR-022 @P2 @acceptance
  Scenario Outline: Engine warns when the .NET analyzer declares a different version
    Given the .NET analyzer returns a graph whose "meta.schema_version" is <dotnet_version>
    When the engine processes that graph
    Then the analysis warnings contain a message naming <dotnet_version> and "1.0.0"
    And the engine artifacts declare "meta.schema_version" equal to "1.0.0"

    Examples:
      | dotnet_version |
      | absent         |
      | "0.9.0"        |
      | "2.0.0"        |
