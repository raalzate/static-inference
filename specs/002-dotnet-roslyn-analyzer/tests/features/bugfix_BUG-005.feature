@BUG-005
Feature: Bug fix for BUG-005 — released .NET analyzer can use full project loading
  Scenario: Release bundle is built so the .NET analyzer can load projects with the installed SDK
    Given the release pipeline definition
    When the .NET analyzer is published for a release
    Then it is published as a framework-dependent application, not as a single-file bundle
    And the release notes state the .NET runtime requirement
