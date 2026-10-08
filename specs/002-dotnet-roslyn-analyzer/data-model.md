# Data Model: .NET/C# Analyzer

## New Java-side Entities

### LanguageAnalyzer (interface)

Abstraction for language-specific analysis. Both Java and .NET analyzers implement this.

- **analyzeProject(Path)**: Analyzes a project and returns a `DependencyGraph`
- **getDependencyResolver()**: Returns the dependency resolver for the analyzed project

Relationship: Used by `AnalysisService` to decouple from specific analyzers.

### ProjectType (enum)

Represents the detected project type.

- **JAVA**: Maven or Gradle project detected
- **DOTNET**: .NET solution or project detected
- **AMBIGUOUS**: Both Java and .NET markers found
- **UNKNOWN**: No recognized project markers found

### ProjectTypeDetector

Examines a directory and determines the project type.

- **detect(Path)**: Scans for marker files and returns `ProjectType`
- **markers**: `.sln`, `.csproj` → DOTNET; `pom.xml`, `build.gradle` → JAVA

Validation: AMBIGUOUS and UNKNOWN produce clear error messages.

### JavaSpoonAnalyzer

Wraps the existing `ProjectAnalyzer` to implement `LanguageAnalyzer`.

- Delegates all calls to `ProjectAnalyzer` (no logic change)
- Preserves backward compatibility

### DotNetBridgeAnalyzer

Invokes the .NET analyzer tool as a subprocess and parses the JSON output.

- **analyzeProject(Path)**: Runs `dotnet-analyzer <path>`, reads stdout JSON, deserializes to `DependencyGraph`
- **getDependencyResolver()**: Returns `DotNetDependencyResolver` that reads `.csproj` NuGet refs
- Error handling: captures stderr for diagnostics, timeout after configurable period

### DotNetToolLocator

Finds the .NET analyzer executable on the system.

- Search order: JAR sibling directory → PATH → DOTNET_ANALYZER_PATH env var
- Returns: Path to executable, or throws with installation instructions

## Existing Entities (Unchanged)

### DependencyGraph

Root model shared by both analyzers. Fields:
- `components`: List of Component
- `edges`: List of Edge
- `api_contracts`: ApiContracts (endpoints + schemas)
- `meta`: Meta (source analyzer name, timestamp)

The only change: `meta.source` will be `"spoon"` for Java or `"roslyn"` for .NET.

### Component

Represents a class/interface. All fields remain the same. .NET components populate:
- `id`: Fully qualified C# name (e.g., `MyApp.Services.OrderService`)
- `annotations`: C# attributes (e.g., `["ApiController", "Authorize"]`)
- `tables_used`: From Entity Framework detection
- `ejb_type`: Always null for .NET (Java-specific)
- `uses_jndi`: Always false for .NET (Java-specific)

### Edge

Relationship between components. Same structure, same weight logic.

### ApiEndpoint, ApiSchema

REST endpoint data. Same structure, populated from ASP.NET Core attributes.

## .NET-side Entities (C# models)

Mirror the Java models exactly for JSON serialization:

- `DependencyGraph.cs` → same JSON property names
- `Component.cs` → same JSON property names
- `Edge.cs` → same JSON property names
- `ApiEndpoint.cs` → same JSON property names
- `ApiSchema.cs` → same JSON property names

The JSON contract is the integration boundary. Both sides must produce/consume identical JSON structure.
