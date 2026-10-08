@BUG-003
Feature: Bug fix for BUG-003 — locate the analyzer on Windows
  Scenario: Windows executable next to the tool
    Given the platform is Windows
    And the analyzer executable with the Windows extension sits next to the tool
    When the analyzer is located
    Then the Windows executable is returned
