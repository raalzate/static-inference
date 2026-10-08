# DO NOT MODIFY SCENARIOS
# These .feature files define expected behavior derived from requirements.
# During implementation:
#   - Write step definitions to match these scenarios
#   - Fix code to pass tests, don't modify .feature files
#   - If requirements change, re-run /iikit-04-testify

@US-007
Feature: Unified development configuration and governance
  As a developer or AI agent working on dev-arch
  I want a single set of ignore, editor, agent and local MCP configuration at the product root
  So that every tool behaves the same across the product

  @TS-036 @FR-027 @P3 @contract
  Scenario: The local MCP server configuration uses a relative engine path
    Given the versioned local MCP server configuration
    Then the engine server entry runs the engine archive through a path relative to the product root
    And no entry contains an absolute path

  @TS-037 @FR-027 @P3 @acceptance @slow
  Scenario: The local MCP server starts with the built engine
    Given the product is built
    When the engine server from the local MCP configuration is started from the product root
    Then it answers the MCP initialization with the five analysis tools

  @TS-038 @FR-026 @FR-012 @SC-003 @P3 @validation
  Scenario Outline: Ignore rules exclude generated artifacts and keep required files versionable
    Given the product ignore rules applied below the monorepo global rules
    When the path "<path>" is checked
    Then it is <status>

    Examples:
      | path                                                       | status      |
      | apps/dev-arch/engine/target/java-dependency-extractor.jar | ignored     |
      | apps/dev-arch/web/engine-dist/engine.jar                  | ignored     |
      | apps/dev-arch/web/.next/build-manifest.json               | ignored     |
      | apps/dev-arch/node_modules/zod/package.json               | ignored     |
      | apps/dev-arch/package-lock.json                           | not ignored |
      | apps/dev-arch/specs/006-devarch-monorepo/spec.md          | not ignored |
      | apps/dev-arch/.specify/context.json                       | not ignored |

  @TS-039 @FR-026 @P3 @validation
  Scenario: The monorepo global ignore rules are not modified
    Given the monorepo global ignore rules on the main branch
    When they are compared with the feature branch
    Then they are identical

  @TS-040 @FR-026 @FR-028 @P3 @contract
  Scenario: A single set of editor rules and agent instructions lives at the product root
    Given the product folder
    Then exactly one editor configuration and one ignore file exist in the product, both at its root
    And the agent instruction files at the product root reference the iikit rules
    And no agent instruction file exists inside "web", "engine" or "schema"

  @TS-041 @FR-029 @P3 @acceptance
  Scenario: The iikit dashboard is generated from the product root
    Given the specifications 001 to 006 at the product root
    When the iikit dashboard is generated from the product root
    Then it lists the features 001 to 006
