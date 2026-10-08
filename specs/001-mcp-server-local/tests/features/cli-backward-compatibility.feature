# DO NOT MODIFY SCENARIOS
# These .feature files define expected behavior derived from requirements.
# During implementation:
#   - Write step definitions to match these scenarios
#   - Fix code to pass tests, don't modify .feature files
#   - If requirements change, re-run /iikit-04-testify

@US-004
Feature: CLI backward compatibility
  An existing CLI user updates the tool and expects their
  current workflow to remain fully operational with identical
  output.

  @TS-020 @FR-009 @SC-003 @SC-005 @P2 @acceptance
  Scenario: CLI produces identical output after MCP integration
    Given the updated JAR
    When executed with the original CLI arguments "<project-path> <output-file>"
    Then it produces the same JSON files with the same content as the previous CLI version

  @TS-021 @FR-009 @P2 @acceptance
  Scenario: Help text shows both CLI and MCP modes
    Given the updated JAR
    When executed without arguments or with "--help"
    Then it displays usage instructions for both CLI mode and MCP mode
