# DO NOT MODIFY SCENARIOS
# These .feature files define expected behavior derived from requirements.
# During implementation:
#   - Write step definitions to match these scenarios
#   - Fix code to pass tests, don't modify .feature files
#   - If requirements change, re-run /iikit-04-testify

@US-003
Feature: Local installation and setup
  A developer installs and activates the MCP server locally
  to use it with an LLM agent, using a single executable artifact
  and a configuration file.

  @TS-015 @FR-001 @FR-002 @SC-004 @P1 @acceptance
  Scenario: Start MCP server via command-line argument
    Given the user has a compatible runtime environment installed
    When the user executes the JAR with the MCP mode argument
    Then the server starts and is available to receive invocations via stdio transport

  @TS-016 @FR-011 @SC-002 @P1 @acceptance
  Scenario: Agent discovers available tools
    Given the server is started
    When an LLM agent requests the list of available tools
    Then the server responds with 5 tools and their parameter schemas in standard MCP format

  @TS-017 @FR-013 @P1 @acceptance
  Scenario: Agent discovers server via configuration file
    Given the user configures the agent integration file (e.g., ".mcp.json")
    When the LLM agent starts
    Then it automatically discovers the server and its capabilities without additional configuration

  @TS-018 @FR-010 @SC-004 @P1 @contract
  Scenario: Single artifact supports both modes
    Given the updated JAR file
    When executed with CLI arguments
    Then it runs in CLI mode
    When executed with MCP mode argument
    Then it runs in MCP server mode

  @TS-019 @FR-011 @SC-002 @P1 @contract
  Scenario: Tool listing includes complete schemas
    Given the MCP server is running
    When a tools/list request is received
    Then the response includes all 5 tools
    And each tool has a name, description, and complete input JSON schema
