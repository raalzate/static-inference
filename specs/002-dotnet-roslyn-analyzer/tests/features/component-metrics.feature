# DO NOT MODIFY SCENARIOS
# These .feature files define expected behavior derived from requirements.
# During implementation:
#   - Write step definitions to match these scenarios
#   - Fix code to pass tests, don't modify .feature files
#   - If requirements change, re-run /iikit-04-testify

@US-004
Feature: Quality Metrics for .NET Components
  Developers evaluate .NET code quality using the same metrics available for Java:
  CBO, LCOM, lines of code, and architectural layer classification
  (Controller, Service, Repository, Shared).

  @TS-031 @FR-007 @FR-009 @SC-006 @P2 @acceptance
  Scenario: get_component_metrics returns CBO, LCOM, LOC, layer and tables_used for a .NET component
    Given a .NET component that has been analyzed
    When "get_component_metrics" is invoked for that component
    Then the result contains "cbo", "lcom", "loc", "layer", and "tables_used" fields
    And the schema matches the schema used for Java components

  @TS-032 @FR-008 @P2 @acceptance
  Scenario Outline: .NET components are classified into the correct architectural layer
    Given a .NET component identified as "<component_type>"
    When the layer classifier processes it
    Then the component is assigned to the "<expected_layer>" layer

    Examples:
      | component_type                           | expected_layer |
      | ASP.NET Core controller with [ApiController] | Controlador    |
      | Business service class (*Service)        | Negocio        |
      | Repository or DbContext class            | Datos          |
      | Helper or Extensions class               | Compartida     |
