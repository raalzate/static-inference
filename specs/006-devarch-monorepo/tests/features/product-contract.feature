# DO NOT MODIFY SCENARIOS
# These .feature files define expected behavior derived from requirements.
# During implementation:
#   - Write step definitions to match these scenarios
#   - Fix code to pass tests, don't modify .feature files
#   - If requirements change, re-run /iikit-04-testify

@US-004
Feature: Data contract at product level
  As an integrator of the engine and the web
  I want the versioned contract and its generated types at product level
  So that both consume it from the same place and divergences are still detected

  @TS-020 @FR-017 @FR-018 @SC-006 @P2 @acceptance @slow
  Scenario: The contract check passes from the product root
    Given the product is built
    When "make schema-check" is executed from the product root
    Then it reports "OK" for the fronts "version", "java", "dotnet" and "ts"
    And the last line is "[schema-check] RESULT: OK"

  @TS-021 @FR-018 @SC-006 @P2 @acceptance @slow
  Scenario Outline: A divergence in any front still fails the check naming that front
    Given the product is built
    And <divergence> is introduced
    When "make schema-check" is executed from the product root
    Then it reports "FAIL" for the front "<front>"
    And it exits with a non-zero code

    Examples:
      | divergence                                                        | front   |
      | a contract version different from the Java declared version       | version |
      | a contract change without regenerating the generated types        | ts      |

  @TS-022 @FR-017 @P2 @contract
  Scenario: The web resolves the contract package from the local workspace
    Given the web package manifest
    When the dependency on the contract package is resolved
    Then it resolves to the product "schema" folder
    And no external registry is queried for it

  @TS-023 @FR-019 @SC-005 @P2 @contract
  Scenario: Engine contract tests locate the product contract without manual configuration
    Given the engine area
    When the engine contract tests are executed from the engine folder without extra properties
    Then they load the contract version from the product "schema" folder
    And they pass

  @TS-024 @FR-019 @SC-005 @P2 @validation
  Scenario: Relocating existing tests does not change what they assert
    Given the engine test sources and the specifications 001 to 005 as imported
    When they are compared with the original engine repository
    Then no assertion statement differs
    And no ".feature" file differs
    And no assertion integrity hash differs
