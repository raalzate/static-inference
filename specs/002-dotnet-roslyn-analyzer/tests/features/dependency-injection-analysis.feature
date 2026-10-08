# DO NOT MODIFY SCENARIOS
# These .feature files define expected behavior derived from requirements.
# During implementation:
#   - Write step definitions to match these scenarios
#   - Fix code to pass tests, don't modify .feature files
#   - If requirements change, re-run /iikit-04-testify

@US-003
Feature: .NET Dependency Injection Analysis
  The dependency graph reflects injection relationships typical in .NET projects:
  constructor injection, IServiceCollection registrations, and interface-implementation
  relationships, producing accurate coupling edges for microservice clustering.

  Background:
    Given a .NET project with dependency injection patterns

  @TS-021 @FR-004 @FR-014 @P2 @acceptance
  Scenario: Constructor injection generates dependency edges
    Given a .NET class that receives dependencies via constructor injection
    When the analysis is executed
    Then the dependency graph contains edges from that class to each injected dependency

  @TS-022 @FR-004 @FR-014 @P2 @acceptance
  Scenario: Interface-implementation relationship is reflected in the graph
    Given a class that implements an interface and another class that depends on that interface
    When the analysis is executed
    Then the graph contains an edge representing the implementation relationship
    And the graph contains an edge representing the dependency through the interface

  @TS-023 @FR-014 @P2 @acceptance
  Scenario: IServiceCollection registrations are detected from Startup or Program
    Given a .NET project with service registrations such as "services.AddScoped<IFoo, Foo>()" in "Startup.cs" or "Program.cs"
    When the analysis is executed
    Then the interface-implementation relationships are detected correctly
    And the corresponding edges appear in the dependency graph
