# Static Code Analysis

The Java Dependency Extractor includes a pattern-based static analysis engine built on top of the **Spoon** library. This feature allows for the detection of common bug patterns and code style issues directly during the architectural extraction process.

## Why Spoon-based Analysis?

Unlike traditional tools like Google Error Prone or Checkstyle which often require a fully compilable classpath and specific compiler plugins, our Spoon-based approach offers several advantages:

1.  **Resilience**: Works even with incomplete or non-compilable code (using `no-classpath` mode).
2.  **Integrated Flow**: Issues are extracted and mapped directly to the same component model used for architectural analysis.
3.  **No Extra Toolchain**: No need to configure separate Gradle/Maven plugins for the project being analyzed.

## Detected Patterns

Currently, the `StaticCodeAnalyzer` detects the following patterns:

### 🐛 Bug Patterns
*   **String Comparison with `==`**: Detects use of identity comparison (`==` or `!=`) when at least one side is a `String`. Suggests using `.equals()`.
*   **Empty Catch Blocks**: Identifies `catch` blocks that swallow exceptions without any logic or logging.
*   **Generic Exception Catching**: Warns when catching the base `Exception` or `Throwable` class instead of specific exceptions.

### 🎨 Code Style & Maintenance
*   **Capitalized Package Names**: Flags package components that start with an uppercase letter (e.g., `com.Project.Module`), violating standard Java naming conventions.
*   **Hardcoded String Comparisons**: Detects comparisons against literal strings in methods like `.equals()`, `.contains()`, or `.equalsIgnoreCase()`, suggesting the use of constants.
*   **Console Printing**: Detects direct use of `System.out.println` or `System.err.println`, recommending the use of a logging framework.

## Data Model

Each detection is represented as a `CodeIssue` in the `output.json`:

```json
{
  "type": "BUG_PATTERN | CODE_STYLE | SECURITY | PERFORMANCE",
  "severity": "INFO | WARNING | ERROR",
  "message": "Descriptive message of the finding",
  "line": 123,
  "pattern": "internal_pattern_id"
}
```

## How to Extend

To add new detection rules, modify the `StaticCodeAnalyzer.java` class. You can extend the `CtScanner` and override the appropriate `visit` methods (e.g., `visitCtMethod`, `visitCtField`).

Example of adding a rule for long methods:

```java
@Override
public <T> void visitCtMethod(CtMethod<T> method) {
    if (method.getBody() != null && method.getBody().getStatements().size() > 50) {
        component.addCodeIssue(new CodeIssue(...));
    }
    super.visitCtMethod(method);
}
```
