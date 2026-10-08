@BUG-006
Feature: Bug fix for BUG-006 — hermetic tool-locator lookup
  Scenario: Analyzer not found regardless of the developer environment
    Given the analyzer location variable is set in the developer environment
    And the locator is given an empty environment and an empty search path
    When the analyzer is located
    Then the lookup fails with installation instructions
