@BUG-001
Feature: Bug fix for BUG-001 — deterministic analysis output
  Scenario: Two consecutive analyses of the same project produce byte-identical output
    Given a project that has already been analyzed once
    When the same project is analyzed again without changes
    Then the dependency graph output is byte-identical to the first run
    And the output contains no wall-clock timestamp unless the user explicitly opts in
