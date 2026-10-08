# DO NOT MODIFY SCENARIOS
# These .feature files define expected behavior derived from requirements.
# During implementation:
#   - Write step definitions to match these scenarios
#   - Fix code to pass tests, don't modify .feature files
#   - If requirements change, re-run /iikit-04-testify

@US-003
Feature: MCP tool responses described by the same contract
  As an LLM agent invoking the MCP tools
  I want successful responses described by the same contract as the disk artifacts
  So that CLI mode and MCP mode are verifiably equivalent

  Background:
    Given the MCP server is running over the Java demo project "java-standar"

  @TS-022 @FR-002 @FR-014 @SC-002 @P1 @acceptance
  Scenario Outline: Every successful tool response validates against its response contract
    When the tool "<tool>" is invoked successfully with <arguments>
    Then the response is not marked as error
    And the response text validates against the "<response_contract>" contract with no errors

    Examples:
      | tool                      | arguments                                       | response_contract                          |
      | analyze_project           | a temporary output directory                    | mcp/analyze_project.response               |
      | get_dependency_graph      | the project path                                | mcp/get_dependency_graph.response          |
      | get_architecture_proposal | the project path                                | mcp/get_architecture_proposal.response     |
      | get_api_contracts         | the project path                                | mcp/get_api_contracts.response             |
      | get_component_metrics     | the id of the first component of the graph      | mcp/get_component_metrics.response         |

  @TS-023 @FR-002 @SC-002 @P1 @acceptance
  Scenario Outline: Architecture proposal filtered by viability still validates
    When the tool "get_architecture_proposal" is invoked with minimum viability "<level>"
    Then the response validates against the "mcp/get_architecture_proposal.response" contract with no errors

    Examples:
      | level |
      | Alta  |
      | Media |
      | Baja  |

  @TS-024 @FR-014 @P1 @acceptance
  Scenario: CLI and MCP outputs are both valid and equivalent
    Given the CLI has produced the three artifacts for the same project
    When the MCP tools return the dependency graph, the architecture proposal and the API contracts
    Then each CLI artifact validates against its document contract
    And each MCP response validates against its response contract
    And each CLI artifact is equivalent to the corresponding MCP response

  @TS-025 @FR-002 @P2 @contract
  Scenario Outline: Response contracts reuse artifact definitions without duplication
    Given the response contract "<response_contract>"
    When its root definition is resolved
    Then it is a reference to "<target>"
    And it declares no properties of its own

    Examples:
      | response_contract                      | target                                       |
      | mcp/get_dependency_graph.response      | dependency-graph contract                    |
      | mcp/get_architecture_proposal.response | architecture contract                        |
      | mcp/get_api_contracts.response         | entrypoints contract                         |
      | mcp/get_component_metrics.response     | Component definition of the common contract  |

  @TS-026 @FR-002 @P2 @contract
  Scenario: analyze_project response keeps its current shape
    When the tool "analyze_project" is invoked successfully with a temporary output directory
    Then the response contains exactly the properties "componentCount", "edgeCount", "proposalCount", "endpointCount", "filesGenerated" and "summary"
    And "filesGenerated" lists exactly three files ending in "output.json", "output_architecture.json" and "output_entrypoints.json"
    And the response does not contain "meta"

  @TS-027 @FR-002 @P2 @contract
  Scenario: get_component_metrics response is a single component without version metadata
    When the tool "get_component_metrics" is invoked with the id of the first component of the graph
    Then the response validates against the Component definition of the common contract
    And the response does not contain "meta"
