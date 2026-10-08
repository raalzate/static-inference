# DO NOT MODIFY SCENARIOS
# These .feature files define expected behavior derived from requirements.
# During implementation:
#   - Write step definitions to match these scenarios
#   - Fix code to pass tests, don't modify .feature files
#   - If requirements change, re-run /iikit-04-testify

@US-002
Feature: Build and test the whole product from a clean clone
  As a developer who just cloned the monorepo
  I want one command to build the product and one command to test it
  So that I know in minutes whether the product is healthy

  @TS-006 @FR-008 @FR-009 @FR-010 @SC-001 @SC-003 @P1 @acceptance @slow
  Scenario: Clean clone builds and tests green
    Given a clean clone of the monorepo on the feature branch
    And JDK 17, the .NET SDK and Node 20 are available
    When "make build" is executed from the product root
    And "make test" is executed from the product root
    Then both commands exit with code 0
    And the engine archive, the .NET analyzer and the web build output exist
    And the working tree has no changed or untracked files

  @TS-007 @FR-010 @SC-005 @P1 @acceptance @slow
  Scenario: The test command runs every subset and excludes the corpus category
    Given the product is built
    When "make test" is executed from the product root
    Then it runs the Java engine tests, the .NET analyzer tests, the contract check, the web tests and the acceptance tests
    And the .NET analyzer tests are run excluding the category "Corpus"
    And every test that passed in the original engine repository passes

  @TS-008 @FR-011 @P1 @acceptance
  Scenario: The selected JDK 17 is honored when the default JDK is another version
    Given the default "java" on the PATH is a JDK other than 17
    And JAVA_HOME points to a JDK 17
    When the prerequisite check is executed
    Then it reports "OK" for "jdk"
    And every Java tool invoked by the product commands uses the JDK at JAVA_HOME

  @TS-009 @FR-011 @P1 @validation
  Scenario Outline: A missing prerequisite fails before compiling
    Given <prerequisite> is not available
    When "make build" is executed from the product root
    Then it exits with a non-zero code before any compilation starts
    And the output names "<tool>", the version found and the version required

    Examples:
      | prerequisite                                 | tool   |
      | a JDK 17 (JAVA_HOME points to a JDK 25)      | jdk    |
      | the .NET 8 runtime                           | dotnet |
      | Node 20 or newer (Node 18 on the PATH)       | node   |

  @TS-010 @FR-008 @P1 @acceptance
  Scenario Outline: Subset test commands run only their subset
    Given the product is built
    When "<command>" is executed from the product root
    Then only the <subset> tests are run

    Examples:
      | command          | subset           |
      | make test-engine | Java and .NET engine |
      | make test-web    | web              |

  @TS-011 @FR-008 @SC-003 @P1 @acceptance @slow
  Scenario: Clean removes every generated artifact
    Given the product is built
    When "make clean" is executed from the product root
    Then the engine build directory, the web build output and the staged engine directory no longer exist
    And the working tree is identical to the clean clone

  @TS-012 @FR-008 @P1 @acceptance
  Scenario Outline: Reserved placeholder commands report their future feature and succeed
    When "<command>" is executed from the product root
    Then the output contains "<message>"
    And it exits with code 0

    Examples:
      | command     | message                                 |
      | make e2e    | not implemented yet (feature 011)       |
      | make doctor | not implemented yet (feature 012)       |

  @TS-013 @FR-012 @P1 @contract
  Scenario: Web and contract packages form a single reproducible workspace
    Given the product package manifest
    Then it declares the workspaces "web" and "schema"
    And a single dependency lock file is tracked at the product root
    And no lock file is tracked inside "web" or "schema"
