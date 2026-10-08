# DO NOT MODIFY SCENARIOS
# These .feature files define expected behavior derived from requirements.
# During implementation:
#   - Write step definitions to match these scenarios
#   - Fix code to pass tests, don't modify .feature files
#   - If requirements change, re-run /iikit-04-testify

@US-006
Feature: Single product version
  As the release owner
  I want the product version declared in one place and reported identically by every artifact
  So that any artifact can be traced to an exact version

  @TS-031 @FR-020 @FR-021 @SC-007 @P2 @contract
  Scenario: Every declaration matches the single version source
    Given the product version source contains "1.2.0"
    When the version check is executed
    Then the engine build descriptor, the .NET analyzer project, the web manifest and the product manifest declare "1.2.0"
    And it exits with code 0

  @TS-032 @FR-021 @SC-007 @P2 @validation
  Scenario: A diverging declaration fails naming the artifact
    Given the web manifest declares version "0.1.0"
    And the product version source contains "1.2.0"
    When the version check is executed
    Then it exits with a non-zero code
    And the output names the web manifest, "0.1.0" and "1.2.0"

  @TS-033 @FR-021 @SC-007 @P2 @acceptance
  Scenario: Changing the version requires editing exactly one file
    Given a copy of the product
    When the product version source is changed to "1.2.1"
    And the version propagation command is executed
    Then the version check passes
    And the only file edited by hand is the product version source

  @TS-034 @FR-021 @P2 @contract
  Scenario: The MCP server reports the product version
    Given the engine is built
    When the MCP server is initialized
    Then the reported server version equals the product version

  @TS-035 @FR-022 @P2 @validation
  Scenario: The contract version is independent of the product version
    Given a copy of the product with contract version "1.0.0"
    When the product version is changed to "1.2.1" and propagated
    Then the contract version is still "1.0.0"
