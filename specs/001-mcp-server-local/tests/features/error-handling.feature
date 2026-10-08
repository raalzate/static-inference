# DO NOT MODIFY SCENARIOS
# These .feature files define expected behavior derived from requirements.
# During implementation:
#   - Write step definitions to match these scenarios
#   - Fix code to pass tests, don't modify .feature files
#   - If requirements change, re-run /iikit-04-testify

Feature: MCP error handling and edge cases
  Error responses follow MCP format with actionable messages.
  Analysis degrades gracefully on partial failures.

  Rule: Errors return structured MCP responses without stack traces

    @TS-022 @FR-008 @SC-006 @P1 @acceptance
    Scenario: Error responses do not expose stack traces
      Given the MCP server is running
      When any tool is invoked with input that causes an internal error
      Then the response has isError true
      And the response contains a human-readable error message
      And the response does not contain Java stack trace information

  Rule: Partial analysis failures do not abort the entire analysis

    @TS-023 @FR-003 @P2 @acceptance
    Scenario: Partially invalid sources produce partial results with warnings
      Given the MCP server is running
      When the agent invokes "analyze_project" on a project with some unparseable Java files
      Then the analysis completes with partial results
      And the response includes warnings indicating which files could not be parsed

  Rule: Path handling works cross-platform

    @TS-024 @FR-003 @P2 @validation
    Scenario: Project path with spaces is handled correctly
      Given the MCP server is running
      When the agent invokes "analyze_project" with a project path containing spaces
      Then the analysis completes successfully

    @TS-025 @FR-003 @P2 @validation
    Scenario: Non-existent output directory is handled
      Given the MCP server is running
      When the agent invokes "analyze_project" with an output directory that does not exist
      Then the system either creates the directory or reports a clear error

  Rule: Concurrent invocations are independent

    @TS-026 @FR-003 @P2 @acceptance
    Scenario: Simultaneous analyses on different projects do not interfere
      Given the MCP server is running
      When two agents invoke "analyze_project" simultaneously with different project paths
      Then each receives independent results corresponding to its own project

  Rule: Shared core produces deterministic output

    @TS-027 @FR-012 @SC-003 @SC-005 @P1 @contract
    Scenario: MCP and CLI produce identical analysis for the same project
      Given the same Java project
      When analyzed via CLI mode
      And analyzed via MCP "analyze_project" tool
      Then both outputs contain identical dependency graph, architecture, and API contract data
