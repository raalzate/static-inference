@BUG-004
Feature: Bug fix for BUG-004 — deterministic proposal names
  Scenario: Component name containing two role keywords
    Given a cluster whose only member is named with two role keywords such as "OrderServiceHandler"
    When the proposal name is generated
    Then the role keyword declared first in the fixed precedence list is used
    And the generated name is the same in every process
