@BUG-002
Feature: Bug fix for BUG-002 — bridge does not hang on verbose diagnostics
  Scenario: The .NET analyzer writes large diagnostics before its result
    Given a .NET analyzer that writes more diagnostics than the OS pipe buffer before its result
    When the .NET project is analyzed
    Then the analysis completes and returns the dependency graph

  Scenario: The .NET analyzer never finishes
    Given a .NET analyzer that does not terminate
    When the .NET project is analyzed with a short timeout
    Then the analysis fails with a timeout error within that timeout
