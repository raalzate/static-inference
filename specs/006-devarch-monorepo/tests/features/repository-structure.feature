# DO NOT MODIFY SCENARIOS
# These .feature files define expected behavior derived from requirements.
# During implementation:
#   - Write step definitions to match these scenarios
#   - Fix code to pass tests, don't modify .feature files
#   - If requirements change, re-run /iikit-04-testify

@US-001
Feature: Single product with the engine history preserved
  As a dev-arch maintainer
  I want web, engine, contract, skills, docs and governance in one product folder with the engine history
  So that I stop maintaining parallel repositories without losing traceability

  @TS-001 @FR-001 @FR-005 @FR-006 @P1 @acceptance
  Scenario: The product folder contains the agreed areas and governance
    Given a clean clone of the monorepo on the feature branch
    When the product folder is listed
    Then it contains the directories "web", "engine", "schema", "skills", "docs" and "docker"
    And it contains "CONSTITUTION.md", "PREMISE.md" and the directory "specs" at its root
    And every other root entry belongs to the allowed governance, version, configuration, orchestration or acceptance-test entries

  @TS-002 @FR-002 @SC-004 @P1 @acceptance
  Scenario: Every engine commit is reachable from the engine area history
    Given the main branch of the original Static Inference repository
    When its commits are looked up in the monorepo
    Then 100% of them are reachable from the history of the engine area
    And the history of the engine shared analysis service includes the commit that released version 1.2.0

  @TS-003 @FR-004 @SC-003 @P1 @acceptance
  Scenario: No analyzer binaries are versioned in the product
    Given the files tracked under the product folder
    When they are searched for Java executable archives and .NET analyzer executables
    Then none is found
    And the vendored analyzer archive and the stale web entrypoints output are no longer tracked

  @TS-004 @FR-007 @P1 @acceptance
  Scenario: Previous working copies outside the product stay untouched
    Given the previous working copies of the engine and the web outside the product folder
    When the feature changes are compared against the monorepo main branch
    Then no file under those working copies is added, modified or deleted

  @TS-005 @FR-005 @SC-005 @P1 @contract
  Scenario: Imported specifications keep their content and integrity anchors
    Given the specifications 001 to 005 of the original engine repository
    When they are compared with the specifications at the product root
    Then every specification file is byte-identical
    And every assertion integrity hash in their context files still verifies

  @TS-042 @FR-003 @P1 @contract
  Scenario: The web is relocated intact into the web area
    Given the web files imported into the monorepo before this feature
    When they are compared with the files of the web area
    Then every source, documentation and configuration file is present with its relative path preserved
    And only the analysis route, the web manifest and the newly added engine resolution files differ
