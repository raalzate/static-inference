# Implementation Plan: Soporte .NET/C# — Analizador con Roslyn

**Branch**: `002-dotnet-roslyn-analyzer` | **Date**: 2026-04-07 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/002-dotnet-roslyn-analyzer/spec.md`

## Summary

Extend the static analysis tool to support .NET/C# projects by: (1) introducing a `LanguageAnalyzer` interface that abstracts the analysis layer, (2) creating a separate .NET CLI tool using Roslyn that produces the same `DependencyGraph` JSON schema, (3) adding automatic project type detection, and (4) bridging the .NET analyzer from the Java process via subprocess invocation. The inference engine, MCP server, and CLI remain unchanged.

## Technical Context

**Language/Version**: Java 17 (existing) + C# / .NET 8.0 (new .NET tool)
**Primary Dependencies**: Spoon 10.4.2 (Java AST, existing), Microsoft.CodeAnalysis.CSharp.Workspaces 4.9.2 (Roslyn, new .NET tool), Jackson 2.20.0 (JSON, existing), System.Text.Json (JSON, .NET tool)
**Storage**: N/A (file-based output only)
**Testing**: JUnit 5.10.0 (Java side), xUnit 2.7.0 (C# side)
**Target Platform**: Any OS with JDK 17+ (all paths). .NET 8.0 SDK additionally required for .NET project analysis.
**Project Type**: Multi-module: existing Maven JAR + new dotnet tool project
**Performance Goals**: Full analysis in <5 minutes for projects up to 200 classes (SC-002, same as Java)
**Constraints**: Java fat JAR preserved, .NET tool as separate self-contained executable, .NET SDK required only for .NET analysis
**Scale/Scope**: Local developer tool, single concurrent analysis per invocation

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How Satisfied |
|-----------|--------|---------------|
| I. Backward Compatibility | PASS | Java analysis path completely unchanged. New code only adds alternatives. CLI args preserved. |
| II. Single Source of Truth | PASS | `LanguageAnalyzer` interface defines the contract. Both Java and .NET analyzers produce the same `DependencyGraph`. Inference engine remains the single source for clustering/proposals. |
| III. Test-First Development | PASS | TDD for Java-side refactoring (interface extraction, detector, bridge). xUnit tests for .NET analyzer. |
| IV. Deterministic Output | PASS | Both analyzers produce deterministic `DependencyGraph` JSON. .NET tool has no randomness. |
| V. Graceful Degradation | PASS | If .NET SDK not available, clear error message. Java analysis never affected. Mixed projects handled with clear messaging. |
| VI. Minimal Footprint | TENSION | .NET analysis requires .NET SDK — an additional runtime dependency. Mitigated: only required when analyzing .NET projects; Java-only users see zero change. Fat JAR preserved for Java. .NET tool distributed as self-contained single-file executable (no SDK needed at runtime if published as self-contained). |

### Principle VI Mitigation

The .NET analyzer will be published as a **self-contained single-file executable** (`dotnet publish -r <rid> --self-contained -p:PublishSingleFile=true`). This means:
- End users do NOT need the .NET SDK installed to analyze .NET projects
- The .NET tool is a single binary (~30-50MB) bundled alongside the Java fat JAR
- Java-only users are not affected at all

This satisfies the spirit of Principle VI: minimal footprint, no runtime infrastructure beyond what's bundled.

## Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                     Fat JAR (dual mode)                          │
│                                                                  │
│  ┌─────────────┐          ┌──────────────────────┐              │
│  │  CLI Adapter │          │   MCP Server Adapter  │              │
│  │  (Main.java) │          │  (McpServerMain.java) │              │
│  └──────┬──────┘          └──────────┬───────────┘              │
│         │                            │                           │
│         ▼                            ▼                           │
│  ┌──────────────────────────────────────────────┐               │
│  │            AnalysisService (core)             │               │
│  │  analyzeProject(Path) → detects type → route  │               │
│  └──────────────────┬───────────────────────────┘               │
│                     │                                            │
│         ┌───────────┼───────────┐                               │
│         ▼                       ▼                                │
│  ┌──────────────┐   ┌───────────────────┐                       │
│  │ ProjectType  │   │ «interface»       │                       │
│  │ Detector     │   │ LanguageAnalyzer  │                       │
│  │              │   │ analyze(Path):    │                       │
│  │ .sln → DOTNET│   │  DependencyGraph  │                       │
│  │ pom → JAVA   │   └────────┬──────────┘                       │
│  └──────────────┘       ┌────┴────┐                             │
│                         ▼         ▼                              │
│               ┌──────────┐  ┌──────────────┐                    │
│               │ JavaSpoon │  │ DotNetBridge │                    │
│               │ Analyzer  │  │ Analyzer     │                    │
│               │(existing) │  │(subprocess)  │                    │
│               └──────────┘  └──────┬───────┘                    │
│                                    │ invokes                     │
│         ┌──────────────────────────┼────────────────┐           │
│         ▼                          ▼                ▼           │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ Inference   │  │ Clustering   │  │ Recommend-   │          │
│  │ Engine      │  │ Algorithm    │  │ ationEngine  │          │
│  └─────────────┘  └──────────────┘  └──────────────┘          │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┘ subprocess (stdout JSON)
                    ▼
         ┌─────────────────────┐
         │  dotnet-analyzer    │
         │  (self-contained    │
         │   .NET executable)  │
         │                     │
         │  Roslyn Workspaces  │
         │  → parse C# AST    │
         │  → extract types    │
         │  → detect deps      │
         │  → calc metrics     │
         │  → output JSON      │
         └─────────────────────┘
```

## Project Structure

### Documentation (this feature)

```text
specs/002-dotnet-roslyn-analyzer/
  plan.md              # This file
  research.md          # Technology decisions and rationale
  data-model.md        # LanguageAnalyzer interface, ProjectType enum
  quickstart.md        # Setup and test scenarios
  contracts/           # DependencyGraph JSON schema (shared contract)
```

### Source Code (repository root)

```text
src/
  main/java/com/extractor/
    MicroserviceInferenceMain.java    # CLI adapter (unchanged)
    analyzer/
      LanguageAnalyzer.java           # NEW: interface for language-specific analysis
      ProjectTypeDetector.java        # NEW: detects Java/.NET/unknown
      ProjectType.java                # NEW: enum (JAVA, DOTNET, UNKNOWN, AMBIGUOUS)
      JavaSpoonAnalyzer.java          # NEW: wraps existing ProjectAnalyzer, implements LanguageAnalyzer
      DotNetBridgeAnalyzer.java       # NEW: invokes .NET tool subprocess, parses JSON
      DotNetToolLocator.java          # NEW: finds the .NET analyzer executable
      ProjectAnalyzer.java            # EXISTING (unchanged internally)
      SpoonLauncherFactory.java       # EXISTING (unchanged)
      ... (all existing files unchanged)
    service/
      AnalysisService.java            # MODIFIED: uses ProjectTypeDetector + LanguageAnalyzer
    mcp/                              # EXISTING (unchanged)
    inference/                        # EXISTING (unchanged)
    model/                            # EXISTING (unchanged)

  test/java/com/extractor/
    analyzer/
      LanguageAnalyzerTest.java       # Interface contract tests
      ProjectTypeDetectorTest.java    # Detection logic tests
      JavaSpoonAnalyzerTest.java      # Adapter tests
      DotNetBridgeAnalyzerTest.java   # Bridge + JSON parsing tests
      DotNetToolLocatorTest.java      # Tool discovery tests
    service/
      AnalysisServiceDotNetTest.java  # Integration tests for .NET routing
    integration/
      CliMcpParityTest.java           # EXISTING (unchanged, still passes)

dotnet-analyzer/                      # NEW: .NET project (separate directory)
  DotNetAnalyzer.sln
  src/
    DotNetAnalyzer/
      DotNetAnalyzer.csproj
      Program.cs                      # Entry point: parse args, run analysis, output JSON
      Analysis/
        ProjectAnalyzer.cs            # Main 5-pass analysis (mirrors Java structure)
        TypeAnalyzer.cs               # Pass 1: extract types from Roslyn symbols
        InvocationAnalyzer.cs         # Pass 2: method call dependencies
        DependencyInjectionAnalyzer.cs # Pass 3: constructor injection, IServiceCollection
        MetricsCalculator.cs          # CBO, LCOM, LOC computation
        LayerClassifier.cs            # Layer classification for .NET conventions
        EndpointExtractor.cs          # ASP.NET Core [HttpGet] etc.
        TableExtractor.cs             # Entity Framework DbSet, [Table]
        MessagingDetector.cs          # MassTransit, Azure Service Bus
        SecretsDetector.cs            # IConfiguration, appsettings patterns
      Model/
        DependencyGraph.cs            # Mirrors Java DependencyGraph exactly
        Component.cs                  # Mirrors Java Component exactly
        Edge.cs                       # Mirrors Java Edge exactly
        ApiEndpoint.cs                # Mirrors Java ApiEndpoint exactly
        ApiSchema.cs                  # Mirrors Java ApiSchema exactly
  tests/
    DotNetAnalyzer.Tests/
      DotNetAnalyzer.Tests.csproj
      Analysis/
        ProjectAnalyzerTests.cs
        TypeAnalyzerTests.cs
        InvocationAnalyzerTests.cs
        DependencyInjectionAnalyzerTests.cs
        MetricsCalculatorTests.cs
        LayerClassifierTests.cs
        EndpointExtractorTests.cs
        TableExtractorTests.cs
      Integration/
        FullPipelineTests.cs          # End-to-end with sample .NET project
      Fixtures/
        SampleProject/                # Minimal .NET project for testing
```

**Structure Decision**: Two projects side by side. The Java project remains a single Maven JAR (Principle VI). The .NET analyzer is a separate dotnet tool project in `dotnet-analyzer/`. The Java fat JAR invokes the .NET tool as a subprocess. This keeps the projects independently buildable and testable.

## Key Technical Decisions

### 1. Roslyn Workspaces API (not CSharpSyntaxTree alone)

Using `Microsoft.CodeAnalysis.CSharp.Workspaces` which provides:
- `MSBuildWorkspace.Open()` to load `.sln`/`.csproj` files with full project context
- Semantic model (`INamedTypeSymbol`, `IMethodSymbol`) for accurate type resolution
- Handles NuGet package references and project references automatically
- Fallback to `AdhocWorkspace` with `CSharpSyntaxTree.ParseText()` for projects that don't build (equivalent to Spoon's no-classpath mode)

### 2. Subprocess Bridge Pattern

The Java `DotNetBridgeAnalyzer` invokes the .NET tool as:
```
dotnet-analyzer <project-path>
```
The .NET tool writes the `DependencyGraph` JSON to stdout. Java reads stdout and deserializes with Jackson into the existing `DependencyGraph` model. This is:
- Simple and robust (no RPC, no sockets)
- Cross-platform (works on macOS, Linux, Windows)
- Testable (can mock subprocess output in tests)

### 3. LanguageAnalyzer Interface

```java
public interface LanguageAnalyzer {
    DependencyGraph analyzeProject(Path projectRoot) throws AnalysisException;
    DependencyResolver getDependencyResolver();
}
```

`JavaSpoonAnalyzer` wraps the existing `ProjectAnalyzer` without changing it. `DotNetBridgeAnalyzer` invokes the subprocess and parses the JSON. `AnalysisService` uses `ProjectTypeDetector` to pick the right one.

### 4. ProjectTypeDetector

Scans the directory for marker files:
- `.sln` or `.csproj` → DOTNET
- `pom.xml` or `build.gradle` → JAVA
- Both → AMBIGUOUS (error with guidance)
- Neither → UNKNOWN (error with supported types)

### 5. .NET Analyzer as Self-Contained Executable

Published with `dotnet publish --self-contained -p:PublishSingleFile=true`. This produces a single binary (~30-50MB) that includes the .NET runtime. Users do NOT need the .NET SDK installed. The binary is platform-specific (macOS/Linux/Windows variants).

### 6. DependencyGraph JSON Schema as Contract

The `DependencyGraph` JSON schema is the shared contract between Java and .NET. Both sides must produce identical structure. The contract is tested with JSON Schema validation in both Java and .NET test suites.

## Dependency Changes

### Java (pom.xml) — No changes

No new Java dependencies. The subprocess bridge uses only `java.lang.ProcessBuilder` and existing Jackson for JSON parsing.

### .NET (new DotNetAnalyzer.csproj)

```xml
<PackageReference Include="Microsoft.CodeAnalysis.CSharp.Workspaces" Version="4.9.2" />
<PackageReference Include="Microsoft.Build.Locator" Version="1.7.8" />
<PackageReference Include="System.Text.Json" Version="8.0.0" />
```

Test project:
```xml
<PackageReference Include="xUnit" Version="2.7.0" />
<PackageReference Include="FluentAssertions" Version="6.12.0" />
```

## .NET Attribute-to-Java Annotation Mapping

| .NET Attribute/Pattern | Java Equivalent | Component Field |
|----------------------|-----------------|-----------------|
| `[ApiController]` | `@RestController` | `annotations: ["ApiController"]` |
| `[HttpGet]`, `[HttpPost]`, etc. | `@GetMapping`, `@PostMapping` | `api_contracts.endpoints` |
| `[Route("...")]` | `@RequestMapping("...")` | `api_contracts.endpoints` |
| `[Table("...")]` | `@Table(name="...")` | `tables_used` |
| `DbSet<Entity>` | `@Entity` class | `tables_used` (table name from DbSet type) |
| Constructor injection | `@Autowired` | `calls_out` edges |
| `IServiceCollection.AddScoped<I,T>` | `@Component`+`@Autowired` | interface-implementation edges |
| `[Authorize]` | `@Secured` | `annotations: ["Authorize"]` |
| `IConfiguration` refs | `@Value`, `@ConfigurationProperties` | `secrets_references` |
| `[FromServices]` | `@Inject` | `calls_out` edges |
| MassTransit consumers | `@JmsListener`, Kafka | `messaging_type`, `messaging_role` |

## Layer Classification for .NET

The existing `LayerClassifier` already works on `Component` model (annotations + naming + packages). For .NET, the same patterns apply with .NET equivalents:

| Layer | .NET Signals |
|-------|-------------|
| Controlador | `[ApiController]`, `[HttpGet/Post/...]`, class name `*Controller` |
| Negocio | class name `*Service`, `*Manager`, `*Handler`, namespace `.Services.` |
| Datos/Persistencia | `DbContext`, `DbSet`, `[Table]`, class name `*Repository`, namespace `.Data.` |
| Compartida | class name `*Helper`, `*Extensions`, `*Middleware`, namespace `.Common.`, `.Infrastructure.` |

The existing `LayerClassifier` lists already cover most .NET naming conventions. Minor additions to annotation lists needed (e.g., `"ApiController"`, `"Authorize"`, `"FromServices"`).

## Implementation Strategy

### Phase 1: Java-side Interface Extraction (no behavior change)
1. Create `LanguageAnalyzer` interface
2. Create `ProjectType` enum
3. Create `JavaSpoonAnalyzer` wrapping existing `ProjectAnalyzer`
4. Modify `AnalysisService` to use `LanguageAnalyzer` (defaults to Java)
5. Verify all existing tests pass (Constitution I)

### Phase 2: Project Type Detection
6. Create `ProjectTypeDetector`
7. Integrate detection into `AnalysisService`
8. Handle AMBIGUOUS and UNKNOWN cases with clear errors

### Phase 3: .NET Analyzer Tool (C# project)
9. Scaffold `dotnet-analyzer` project with Roslyn dependencies
10. Implement `Program.cs` (CLI entry point, JSON output to stdout)
11. Implement `TypeAnalyzer` (Pass 1: extract C# types from Roslyn symbols)
12. Implement `InvocationAnalyzer` (Pass 2: method call dependencies)
13. Implement `DependencyInjectionAnalyzer` (Pass 3: constructor injection)
14. Implement `EndpointExtractor` (ASP.NET Core REST endpoints)
15. Implement `TableExtractor` (Entity Framework DbSet, [Table])
16. Implement `MetricsCalculator` (CBO, LCOM, LOC)
17. Implement `LayerClassifier` (layer classification for .NET)
18. Integration test with sample .NET project

### Phase 4: Java-side Bridge
19. Create `DotNetToolLocator` (finds the .NET analyzer executable)
20. Create `DotNetBridgeAnalyzer` (subprocess invocation + JSON parsing)
21. Create `DotNetDependencyResolver` (reads .csproj NuGet references)
22. Wire into `AnalysisService` routing

### Phase 5: Polish & Integration
23. Add .NET annotations to `LayerClassifier` lists
24. End-to-end test: Java CLI → .NET project → full analysis with proposals
25. End-to-end test: MCP server → .NET project → all 5 tools work
26. Update CLI help text to mention .NET support
27. Update `.mcp.json` example if needed
28. Verify Java backward compatibility (all existing tests pass)

## Complexity Tracking

| Tension | Why Needed | Mitigation |
|---------|------------|------------|
| Second runtime (.NET) | Roslyn is the only production-quality C# AST tool; no Java alternative exists | Self-contained executable bundles .NET runtime; Java-only users unaffected |
| Subprocess invocation | Cross-language bridge needed | Simple stdout JSON pipe; battle-tested pattern; easily testable with mocked output |
| Two build systems (Maven + dotnet) | Each language needs its native toolchain | Independent builds; CI can run both. Java fat JAR build unchanged |
