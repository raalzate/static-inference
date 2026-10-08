# DO NOT MODIFY SCENARIOS
# These .feature files define expected behavior derived from requirements.
# During implementation:
#   - Write step definitions to match these scenarios
#   - Fix code to pass tests, don't modify .feature files
#   - If requirements change, re-run /iikit-04-testify

@US-006
Feature: .NET Messaging and Configuration Pattern Detection
  The analysis detects messaging patterns (RabbitMQ, Azure Service Bus, MassTransit)
  and configuration/secrets references in .NET projects, enriching the dependency
  graph with the same kind of supplementary information available for Java.

  Background:
    Given a .NET project with messaging or configuration patterns

  @TS-051 @FR-003 @P3 @acceptance
  Scenario Outline: Messaging components are marked with the correct messaging_type
    Given a .NET project that uses "<messaging_framework>"
    When the analysis is executed
    Then the components with messaging patterns are marked with "messaging_type" set to "<expected_type>"
    And the "messaging_role" field indicates whether the component is a producer or consumer

    Examples:
      | messaging_framework | expected_type     |
      | MassTransit         | MassTransit       |
      | Azure Service Bus   | AzureServiceBus   |

  @TS-052 @FR-003 @P3 @acceptance
  Scenario: IConfiguration and appsettings references are detected
    Given a .NET project that uses "IConfiguration" or references "appsettings.json"
    When the analysis is executed
    Then the components that access configuration or secrets are identified
    And their "secrets_references" field contains the detected configuration keys
