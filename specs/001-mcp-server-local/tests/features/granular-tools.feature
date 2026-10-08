# DO NOT MODIFY SCENARIOS
# These .feature files define expected behavior derived from requirements.
# During implementation:
#   - Write step definitions to match these scenarios
#   - Fix code to pass tests, don't modify .feature files
#   - If requirements change, re-run /iikit-04-testify

@US-002
Feature: Granular analysis tools via MCP
  A developer queries specific analysis results (API contracts,
  architecture proposals, component metrics, dependency graph)
  without receiving the full analysis output.

  @TS-007 @FR-006 @P2 @acceptance
  Scenario: Get API contracts returns only contract data
    Given the MCP server is running
    When the agent invokes "get_api_contracts" with a valid path
    Then the response contains only REST endpoints and messaging schemas
    And the response does not contain clustering or quality metrics

  @TS-008 @FR-005 @P2 @acceptance
  Scenario: Get architecture proposal filtered by viability
    Given the MCP server is running
    When the agent invokes "get_architecture_proposal" with a minimum viability of "Alta"
    Then the response contains only proposals that meet or exceed "Alta" viability

  @TS-009 @FR-007 @P2 @acceptance
  Scenario: Get component metrics for a specific component
    Given the MCP server is running
    When the agent invokes "get_component_metrics" with a valid component identifier
    Then the response contains CBO, LCOM, layer, and tables for that component

  @TS-010 @FR-004 @P2 @contract
  Scenario: get_dependency_graph returns full graph structure
    Given the MCP server is running
    When the agent invokes "get_dependency_graph" with a valid project path
    Then the response contains "components" as a list
    And the response contains "edges" as a list
    And the response contains "api_contracts"
    And the response contains "meta"

  @TS-011 @FR-005 @P2 @contract
  Scenario: get_architecture_proposal returns ConsolidatedArchitecture
    Given the MCP server is running
    When the agent invokes "get_architecture_proposal" with a valid project path
    Then the response contains "project_metadata"
    And the response contains "proposals" as a list
    And the response contains "summary"

  @TS-012 @FR-006 @P2 @contract
  Scenario: get_api_contracts returns ApiContracts structure
    Given the MCP server is running
    When the agent invokes "get_api_contracts" with a valid project path
    Then the response contains "endpoints" as a list
    And the response contains "schemas" as a map

  @TS-013 @FR-007 @P2 @contract
  Scenario: get_component_metrics returns single Component
    Given the MCP server is running
    When the agent invokes "get_component_metrics" with a valid project path and component ID
    Then the response contains "id", "loc", "cbo", "lcom", "layer", "tables_used"

  @TS-014 @FR-007 @FR-008 @SC-006 @P2 @contract
  Scenario: get_component_metrics with unknown component returns error
    Given the MCP server is running
    When the agent invokes "get_component_metrics" with a non-existent component ID
    Then the response is an MCP error with isError true
    And the error message contains the component ID and lists available components
