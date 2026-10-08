# DO NOT MODIFY SCENARIOS
# These .feature files define expected behavior derived from requirements.
# During implementation:
#   - Write step definitions to match these scenarios
#   - Fix code to pass tests, don't modify .feature files
#   - If requirements change, re-run /iikit-04-testify

@US-001
Feature: Full project analysis via MCP
  An architect invokes analyze_project from an LLM agent to get
  dependency graph, architecture proposals, and API contracts
  for a Java monolith.

  @TS-001 @FR-003 @SC-001 @P1 @acceptance
  Scenario: Successful full analysis on a valid Java project
    Given the MCP server is running locally
    When the agent invokes "analyze_project" with a valid path to a Java project
    Then the response contains a structured result with dependency graph, microservice proposals, and API contracts
    And the corresponding output files are generated on disk

  @TS-002 @FR-003 @FR-008 @SC-006 @P1 @acceptance
  Scenario: Analysis with invalid project path
    Given the MCP server is running
    When the agent invokes "analyze_project" with a path that does not exist
    Then the response is an MCP error with isError true
    And the error message clearly indicates the path does not exist

  @TS-003 @FR-003 @FR-008 @SC-006 @P1 @acceptance
  Scenario: Analysis with path containing no Java sources
    Given the MCP server is running
    When the agent invokes "analyze_project" with a path that contains no Java source files
    Then the response is an MCP error with isError true
    And the error message indicates no Java source files were found

  @TS-004 @FR-003 @P1 @acceptance
  Scenario: Analysis maintains connection for long-running projects
    Given the server is started
    When the analysis takes more than 30 seconds
    Then the server maintains the connection active and notifies progress to the agent

  @TS-005 @FR-003 @P1 @contract
  Scenario: analyze_project returns correct response structure
    Given the MCP server is running
    When the agent invokes "analyze_project" with a valid Java project path
    Then the response contains "componentCount" as an integer
    And the response contains "edgeCount" as an integer
    And the response contains "proposalCount" as an integer
    And the response contains "endpointCount" as an integer
    And the response contains "filesGenerated" as a list of file paths
    And the response contains "summary" as a string

  @TS-006 @FR-003 @SC-005 @P1 @contract
  Scenario: analyze_project generates expected output files
    Given the MCP server is running
    When the agent invokes "analyze_project" with a valid project path and an output directory
    Then the files "output.json", "output_architecture.json", and "output_entrypoints.json" are created in the output directory
