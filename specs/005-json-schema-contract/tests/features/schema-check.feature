# DO NOT MODIFY SCENARIOS
# These .feature files define expected behavior derived from requirements.
# During implementation:
#   - Write step definitions to match these scenarios
#   - Fix code to pass tests, don't modify .feature files
#   - If requirements change, re-run /iikit-04-testify

@US-005
Feature: Single divergence check across Java, .NET and generated types
  As a maintainer
  I want a single entry point that verifies Java, .NET and generated types against the contract
  So that any divergence is detected before integrating a change

  @TS-034 @FR-015 @FR-016 @SC-009 @P2 @acceptance
  Scenario: Aligned producers and generated types pass the check
    Given Java, .NET and the generated types are aligned with the contract
    And JDK 17, the .NET SDK and Node 20 are available
    When "make schema-check" is executed
    Then it reports "OK" for the fronts "version", "java", "dotnet" and "ts"
    And it exits with code 0
    And it completes in less than 10 minutes

  @TS-035 @FR-015 @SC-004 @P2 @acceptance
  Scenario Outline: A divergence in any front fails the check and names that front
    Given Java, .NET and the generated types are aligned with the contract
    And <divergence> is introduced
    When "make schema-check" is executed
    Then it reports "FAIL" for the front "<front>"
    And the failure message names <detail>
    And it exits with a non-zero code

    Examples:
      | divergence                                                              | front  | detail                                  |
      | an undeclared property "foo" on every Java component                    | java   | the unknown property "foo"              |
      | a .NET fixture component whose layer is "Servicio"                      | dotnet | the path of the layer field             |
      | a contract change without regenerating the generated types              | ts     | the out-of-date generated file          |

  @TS-036 @FR-016 @P2 @acceptance
  Scenario Outline: A producer declaring a different version fails the check
    Given the contract declares version "1.0.0"
    And the <producer> version constant is "1.0.1"
    When "make schema-check" is executed
    Then it reports "FAIL" for the front "version"
    And the failure message names "1.0.1" and "1.0.0"
    And it exits with a non-zero code

    Examples:
      | producer |
      | Java     |
      | .NET     |

  @TS-037 @FR-015 @P2 @acceptance
  Scenario Outline: A front that cannot be verified is not reported as success
    Given the tool "<tool>" is not available
    When "make schema-check" is executed
    Then it reports "NOT VERIFIED" for the front "<front>"
    And the summary reports the result as "FAIL"
    And it exits with a non-zero code

    Examples:
      | tool   | front  |
      | dotnet | dotnet |
      | node   | ts     |
      | java   | java   |

  @TS-038 @FR-015 @P3 @contract
  Scenario: Check output follows the documented line format
    When "make schema-check" is executed
    Then every front produces exactly one line matching "[schema-check] <front>: OK|FAIL|NOT VERIFIED"
    And the last line matches "[schema-check] RESULT: OK|FAIL"

  @TS-039 @FR-015 @P3 @contract
  Scenario: Continuous integration runs the check as a blocking job
    Given the continuous integration workflow definition
    When the jobs are inspected
    Then a job runs "make schema-check" with JDK 17, .NET 8 and Node 20
    And that job is not configured to tolerate failure
