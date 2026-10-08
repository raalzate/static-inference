# Research: .NET/C# Static Analysis

## 1. C# AST Parsing Options

### Decision: Microsoft.CodeAnalysis (Roslyn) via Workspaces API

**Rationale**: Roslyn is the official Microsoft C# compiler platform. It provides full semantic analysis (type resolution, symbol lookup, flow analysis), not just syntax parsing.

**Alternatives considered**:

| Option | Pros | Cons | Verdict |
|--------|------|------|---------|
| **Roslyn Workspaces** | Full semantic model, official Microsoft, loads .sln/.csproj natively, resolves NuGet refs | Requires .NET runtime, larger binary | **SELECTED** |
| **tree-sitter C#** | Lightweight, Java-native (JNI bindings), no .NET dependency | Syntax-only (no type resolution), can't resolve cross-file references, metrics would be inaccurate | Rejected: insufficient for dependency graph |
| **ANTLR C# grammar** | Java-native, mature grammar | Syntax-only, no semantic model, would need custom type resolution | Rejected: too much custom work for same result |
| **Mono.Cecil** | Analyzes compiled IL, no source needed | Requires compiled assemblies, misses source-level patterns | Rejected: need source analysis, not IL |

### Key Roslyn APIs

- `MSBuildWorkspace.Create()` → opens `.sln`/`.csproj` with full project context
- `AdhocWorkspace` + `CSharpSyntaxTree.ParseText()` → fallback for non-building projects
- `SemanticModel.GetSymbolInfo()` → resolves types across files
- `INamedTypeSymbol` → equivalent to Spoon's `CtType`
- `InvocationExpressionSyntax` → equivalent to Spoon's `CtInvocation`
- `IPropertySymbol`, `IFieldSymbol` → detect injected dependencies

## 2. Cross-Language Bridge Pattern

### Decision: Subprocess with stdout JSON

**Rationale**: Simplest reliable pattern for cross-language tool integration. The .NET tool is a CLI that writes JSON to stdout; Java reads it with ProcessBuilder.

**Alternatives considered**:

| Option | Pros | Cons | Verdict |
|--------|------|------|---------|
| **Subprocess + stdout** | Simple, testable, no dependencies, cross-platform | Process startup overhead (~1-2s) | **SELECTED** |
| **gRPC** | Efficient, strongly typed | Adds protobuf dependency, complex setup, overkill for single request | Rejected |
| **Named pipes / Unix sockets** | Fast IPC | Platform-specific, complex error handling | Rejected |
| **Shared file** | Simple | Temp file management, race conditions | Rejected |
| **IKVM (.NET on JVM)** | Single JVM process | Experimental, incomplete .NET support, Roslyn won't work | Rejected |

### Process startup overhead

The .NET self-contained executable starts in ~200-500ms. For analysis that takes 30s-5min, this is negligible (<1% overhead).

## 3. .NET Distribution Strategy

### Decision: Self-contained single-file executable

**Rationale**: Users should not need the .NET SDK installed to analyze .NET projects. Publishing as self-contained bundles the runtime.

```bash
# Build for each platform:
dotnet publish -c Release -r osx-x64 --self-contained -p:PublishSingleFile=true
dotnet publish -c Release -r linux-x64 --self-contained -p:PublishSingleFile=true
dotnet publish -c Release -r win-x64 --self-contained -p:PublishSingleFile=true
```

**Size**: ~30-50MB per platform (includes .NET 8 runtime + Roslyn + dependencies)

**Discovery**: The Java `DotNetToolLocator` looks for the executable in:
1. Same directory as the fat JAR (`./dotnet-analyzer` or `./dotnet-analyzer.exe`)
2. System PATH
3. Configurable via environment variable `DOTNET_ANALYZER_PATH`

## 4. Entity Framework Detection

### Decision: Multi-strategy detection

Entity Framework tables are detected via:
1. **DbSet properties**: `public DbSet<Order> Orders { get; set; }` → table "Orders"
2. **[Table] attribute**: `[Table("custom_orders")]` → table "custom_orders"
3. **Fluent API in OnModelCreating**: `modelBuilder.Entity<Order>().ToTable("orders")` → table "orders"
4. **Convention**: DbSet property name = table name (pluralized)

Strategy 3 (Fluent API) is lower priority — it requires analyzing method chains in `OnModelCreating`. Strategy 1 and 2 cover the majority of real-world EF usage.

## 5. .NET Dependency Injection Detection

### Decision: Constructor parameter analysis + service registration

The primary DI pattern in .NET is constructor injection:
```csharp
public class OrderService {
    private readonly IOrderRepository _repo;
    public OrderService(IOrderRepository repo) { _repo = repo; }
}
```

Detection strategy:
1. **Constructor parameters**: Each parameter type → edge from class to parameter type
2. **Interface resolution**: If parameter is an interface, find implementing class → create edge to implementation
3. **Service registration**: Parse `Startup.cs`/`Program.cs` for `services.AddScoped<IFoo, Foo>()` → explicit interface-implementation mapping

## 6. ASP.NET Core Endpoint Extraction

### Decision: Attribute-based routing analysis

Detect REST endpoints from:
1. **Controller attributes**: `[ApiController]`, `[Route("api/[controller]")]`
2. **Action attributes**: `[HttpGet]`, `[HttpPost("{id}")]`, `[HttpPut]`, `[HttpDelete]`
3. **Minimal APIs**: `app.MapGet("/api/orders", ...)` (lower priority, requires Program.cs analysis)

Map to existing `ApiEndpoint` model:
- `method`: HTTP method from attribute
- `path`: Route template from attributes
- `controller`: Controller class name
- `parameters`: From method parameters with `[FromBody]`, `[FromQuery]`, `[FromRoute]`
- `responseType`: Return type analysis

## 7. .NET Framework vs .NET Core Support

### Decision: Both supported with fallback

- **Primary**: .NET Core / .NET 5+ projects (`.csproj` with `<TargetFramework>net8.0</TargetFramework>`)
- **Secondary**: .NET Framework projects (`.csproj` with `<TargetFrameworkVersion>v4.8</TargetFrameworkVersion>`)
- **Fallback**: If `MSBuildWorkspace` can't load the project, fall back to `AdhocWorkspace` with direct file parsing (syntax-only mode with warnings about reduced accuracy)

.NET Framework projects may use older patterns (Global.asax, WebApiConfig instead of Startup.cs), but the core analysis (types, invocations, attributes) works the same via Roslyn.
