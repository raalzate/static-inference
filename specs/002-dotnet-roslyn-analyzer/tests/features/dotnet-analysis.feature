# DO NOT MODIFY SCENARIOS
# These .feature files define expected behavior derived from requirements.
# During implementation:
#   - Write step definitions to match these scenarios
#   - Fix code to pass tests, don't modify .feature files
#   - If requirements change, re-run /iikit-04-testify

@US-001
Feature: Analyze a .NET Monolithic Project
  An LLM agent or software architect points the tool at a .NET/C# project directory
  and receives a structured DependencyGraph with components, edges, and API contracts,
  equivalent to the analysis available for Java projects.

  Background:
    Given a .NET/C# project directory containing ".sln" or ".csproj" files

  @TS-001 @FR-001 @FR-002 @FR-009 @SC-001 @P1 @acceptance
  Scenario: analyze_project returns a valid DependencyGraph for a .NET project
    Given the project contains controllers, services, and repositories
    When the user invokes "analyze_project" pointing at that directory
    Then the result contains a "DependencyGraph" with the same JSON schema as Java analysis
    And the result includes a non-empty "components" list
    And the result includes a non-empty "edges" list
    And the result includes an "api_contracts" section

  @TS-002 @FR-005 @P1 @acceptance
  Scenario: ASP.NET Core endpoints appear in api_contracts
    Given the project contains controllers decorated with "[ApiController]", "[HttpGet]", and "[HttpPost]"
    When the analysis is executed
    Then the REST endpoints appear in the "api_contracts" section of the result
    And each endpoint includes the HTTP method, path, and controller name

  @TS-003 @FR-006 @P1 @acceptance
  Scenario: Entity Framework tables appear in tables_used
    Given the project contains Entity Framework DbContext with DbSet properties
    When the analysis is executed
    Then the components include the referenced database tables in their "tables_used" field

  @TS-004 @FR-003 @SC-002 @P1 @acceptance
  Scenario: Analysis of a 200-class .NET project completes within 5 minutes
    Given a .NET project with up to 200 classes
    When the analysis is executed
    Then it completes in less than 5 minutes
    And the result contains all components with metadata (qualified name, file path, LOC, annotations)

  @TS-005 @FR-009 @SC-004 @P1 @acceptance
  Scenario: Inference engine generates microservice proposals from a .NET DependencyGraph
    Given the analysis produces a valid DependencyGraph for a .NET project
    When the inference engine processes that DependencyGraph
    Then coherent microservice decomposition proposals are generated
    And the proposals include cluster assignments and viability scores

  @TS-006 @FR-009 @SC-007 @P1 @contract
  Scenario: All 5 MCP tools work transparently with a .NET project
    Given a running MCP server and a .NET project directory
    When each of the 5 MCP tools is invoked with the .NET project path
    Then "analyze_project" returns a valid DependencyGraph
    And "get_dependency_graph" returns the edges for the .NET project
    And "get_architecture_proposal" returns microservice proposals
    And "get_component_metrics" returns metrics for each component
    And "get_api_contracts" returns the REST endpoints

  @TS-007 @FR-012 @P2 @acceptance
  Scenario: .NET project without successful compilation is analyzed in no-compilation mode
    Given a .NET project that has missing NuGet dependencies or does not build
    When the analysis is executed
    Then partial results are returned with available components
    And a warning indicates which parts were skipped due to compilation errors
    And the overall analysis does not abort
