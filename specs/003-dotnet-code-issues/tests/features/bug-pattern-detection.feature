# DO NOT MODIFY SCENARIOS
# These .feature files define expected behavior derived from requirements.
# During implementation:
#   - Write step definitions to match these scenarios
#   - Fix code to pass tests, don't modify .feature files
#   - If requirements change, re-run /iikit-04-testify

@US-1 @P1
Feature: Detect bug patterns in C# components
  As a static analysis tool
  When I analyze a .NET/C# project
  I want to automatically detect common bug patterns in each component
  So that users get the same quality of code issue information as Java projects

  Background:
    Given a RoslynCodeAnalyzer instance
    And a TypeAnalyzer instance

  # ─── EMPTY_CATCH_BLOCK ────────────────────────────────────────────────────

  @TS-001 @FR-001 @FR-002 @SC-001 @SC-002 @acceptance
  Scenario: Empty catch block is detected as WARNING
    Given a C# class with an empty catch block
      """
      namespace Sample {
          public class Service {
              public void DoWork() {
                  try { var x = 1; }
                  catch (Exception e) { }
              }
          }
      }
      """
    When the analyzer runs on the compiled project
    Then the component "Sample.Service" has exactly 1 code issue
    And the issue has rule "EMPTY_CATCH_BLOCK"
    And the issue has severity "WARNING"
    And the issue has a non-empty message
    And the issue has a location matching pattern ":\d+$"

  @TS-002 @FR-001 @FR-004 @acceptance
  Scenario: Catch block with statements is not flagged
    Given a C# class with a non-empty catch block
      """
      namespace Sample {
          public class Service {
              public void DoWork() {
                  try { var x = 1; }
                  catch (Exception e) { Console.WriteLine(e.Message); }
              }
          }
      }
      """
    When the analyzer runs on the compiled project
    Then the component "Sample.Service" has no issues with rule "EMPTY_CATCH_BLOCK"

  # ─── CATCH_GENERIC_EXCEPTION ──────────────────────────────────────────────

  @TS-003 @FR-001 @FR-002 @SC-002 @acceptance
  Scenario: Catch of generic Exception is detected as INFO
    Given a C# class that catches System.Exception
      """
      namespace Sample {
          public class Service {
              public void DoWork() {
                  try { var x = 1; }
                  catch (Exception e) { throw; }
              }
          }
      }
      """
    When the analyzer runs on the compiled project
    Then the component "Sample.Service" has at least 1 code issue with rule "CATCH_GENERIC_EXCEPTION"
    And the issue has severity "INFO"

  @TS-004 @FR-001 @acceptance
  Scenario: Catch of specific exception type is not flagged
    Given a C# class that catches a specific exception
      """
      namespace Sample {
          public class Service {
              public void DoWork() {
                  try { var x = 1; }
                  catch (ArgumentNullException e) { throw; }
              }
          }
      }
      """
    When the analyzer runs on the compiled project
    Then the component "Sample.Service" has no issues with rule "CATCH_GENERIC_EXCEPTION"

  # ─── CONSOLE_LOGGING ──────────────────────────────────────────────────────

  @TS-005 @FR-001 @FR-002 @SC-002 @acceptance
  Scenario: Console.WriteLine is detected as INFO
    Given a C# class that uses Console.WriteLine
      """
      namespace Sample {
          public class Service {
              public void DoWork() {
                  Console.WriteLine("debug info");
              }
          }
      }
      """
    When the analyzer runs on the compiled project
    Then the component "Sample.Service" has at least 1 code issue with rule "CONSOLE_LOGGING"
    And the issue has severity "INFO"

  @TS-006 @FR-001 @acceptance
  Scenario: Console.Write is also detected
    Given a C# class that uses Console.Write
      """
      namespace Sample {
          public class Service {
              public void DoWork() {
                  Console.Write("x");
              }
          }
      }
      """
    When the analyzer runs on the compiled project
    Then the component "Sample.Service" has at least 1 code issue with rule "CONSOLE_LOGGING"

  @TS-007 @FR-001 @FR-004 @acceptance
  Scenario: ILogger usage does not trigger CONSOLE_LOGGING
    Given a C# class that uses only ILogger
      """
      namespace Sample {
          public class Service {
              private readonly object _logger;
              public Service(object logger) { _logger = logger; }
              public void DoWork() { }
          }
      }
      """
    When the analyzer runs on the compiled project
    Then the component "Sample.Service" has no issues with rule "CONSOLE_LOGGING"

  # ─── STRING_EQUALITY_OPERATOR ─────────────────────────────────────────────

  @TS-008 @FR-001 @FR-002 @SC-002 @acceptance
  Scenario: ReferenceEquals on strings is detected as WARNING
    Given a C# class that uses ReferenceEquals on string variables
      """
      namespace Sample {
          public class Service {
              public bool Compare(string a, string b) {
                  return ReferenceEquals(a, b);
              }
          }
      }
      """
    When the analyzer runs on the compiled project
    Then the component "Sample.Service" has at least 1 code issue with rule "STRING_EQUALITY_OPERATOR"
    And the issue has severity "WARNING"

  @TS-009 @FR-001 @FR-004 @acceptance
  Scenario: Normal string == comparison is not flagged in C#
    Given a C# class that uses == on string variables
      """
      namespace Sample {
          public class Service {
              public bool Compare(string a, string b) {
                  return a == b;
              }
          }
      }
      """
    When the analyzer runs on the compiled project
    Then the component "Sample.Service" has no issues with rule "STRING_EQUALITY_OPERATOR"

  # ─── HARDCODED_SECRET_IN_CODE ─────────────────────────────────────────────

  @TS-010 @FR-001 @FR-002 @SC-002 @acceptance
  Scenario: Hardcoded password variable is detected as CRITICAL
    Given a C# class with a hardcoded password
      """
      namespace Sample {
          public class Service {
              public void Connect() {
                  var password = "super_secret_123";
              }
          }
      }
      """
    When the analyzer runs on the compiled project
    Then the component "Sample.Service" has at least 1 code issue with rule "HARDCODED_SECRET_IN_CODE"
    And the issue has severity "CRITICAL"

  @TS-011 @FR-001 @acceptance
  Scenario: Hardcoded apiKey variable is detected as CRITICAL
    Given a C# class with a hardcoded API key
      """
      namespace Sample {
          public class Service {
              public void Init() {
                  var apiKey = "sk-abc123def456";
              }
          }
      }
      """
    When the analyzer runs on the compiled project
    Then the component "Sample.Service" has at least 1 code issue with rule "HARDCODED_SECRET_IN_CODE"
    And the issue has severity "CRITICAL"

  @TS-012 @FR-001 @FR-004 @acceptance
  Scenario: Regular string variable is not flagged as hardcoded secret
    Given a C# class with a non-secret string variable
      """
      namespace Sample {
          public class Service {
              public void DoWork() {
                  var name = "John Doe";
                  var city = "Bogotá";
              }
          }
      }
      """
    When the analyzer runs on the compiled project
    Then the component "Sample.Service" has no issues with rule "HARDCODED_SECRET_IN_CODE"

  # ─── DEPRECATED_THREAD_USAGE ──────────────────────────────────────────────

  @TS-013 @FR-001 @FR-002 @SC-002 @acceptance
  Scenario: Thread.Abort call is detected as ERROR
    Given a C# class that calls Thread.Abort
      """
      namespace Sample {
          public class Service {
              public void Stop(System.Threading.Thread t) {
                  t.Abort();
              }
          }
      }
      """
    When the analyzer runs on the compiled project
    Then the component "Sample.Service" has at least 1 code issue with rule "DEPRECATED_THREAD_USAGE"
    And the issue has severity "ERROR"

  @TS-014 @FR-001 @FR-004 @acceptance
  Scenario: Thread.Start is not flagged
    Given a C# class that calls Thread.Start
      """
      namespace Sample {
          public class Service {
              public void Run(System.Threading.Thread t) {
                  t.Start();
              }
          }
      }
      """
    When the analyzer runs on the compiled project
    Then the component "Sample.Service" has no issues with rule "DEPRECATED_THREAD_USAGE"

  # ─── MISSING_SWITCH_DEFAULT ───────────────────────────────────────────────

  @TS-015 @FR-001 @FR-002 @SC-002 @acceptance
  Scenario: Switch without default case is detected as INFO
    Given a C# class with a switch statement missing default
      """
      namespace Sample {
          public class Service {
              public string Describe(int x) {
                  switch (x) {
                      case 1: return "one";
                      case 2: return "two";
                  }
                  return "unknown";
              }
          }
      }
      """
    When the analyzer runs on the compiled project
    Then the component "Sample.Service" has at least 1 code issue with rule "MISSING_SWITCH_DEFAULT"
    And the issue has severity "INFO"

  @TS-016 @FR-001 @FR-004 @acceptance
  Scenario: Switch with default case is not flagged
    Given a C# class with a switch statement that has a default
      """
      namespace Sample {
          public class Service {
              public string Describe(int x) {
                  switch (x) {
                      case 1: return "one";
                      default: return "other";
                  }
              }
          }
      }
      """
    When the analyzer runs on the compiled project
    Then the component "Sample.Service" has no issues with rule "MISSING_SWITCH_DEFAULT"

  # ─── NEGATIVE / CLEAN CODE ────────────────────────────────────────────────

  @TS-017 @FR-004 @SC-001 @acceptance
  Scenario: Clean component with no patterns returns empty code_issues
    Given a C# class with no problematic patterns
      """
      namespace Sample {
          public class CleanService {
              private readonly string _name;
              public CleanService(string name) { _name = name; }
              public string GetName() => _name;
          }
      }
      """
    When the analyzer runs on the compiled project
    Then the component "Sample.CleanService" has 0 code issues
