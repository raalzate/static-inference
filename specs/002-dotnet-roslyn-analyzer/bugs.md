# Bug Reports: 002-dotnet-roslyn-analyzer

## BUG-001

**Reported**: 2026-10-07
**Severity**: high
**Status**: fixed
**GitHub Issue**: _(none)_

**Description**: Analysis output is not deterministic: `meta.collected_at` carries a wall-clock timestamp (Java and .NET), violating Constitution principle IV

**Reproduction Steps**:
1. Run the CLI twice on the same project (`test-for-demo/java-standar`) into two output files
2. Compare the dependency graph JSON byte by byte
3. Observe only `meta.collected_at` differs

**Root Cause**: `DependencyGraph.Meta` (Java) and `Meta` (.NET) stamped `Instant.now()`/`DateTime.UtcNow` unconditionally.

**Fix Reference**: `collected_at` is null/omitted unless `STATIC_INFERENCE_COLLECTED_AT=true`; tests `DependencyGraphMetaTest`, `MetaDeterminismTests`; CLI runs on java-standar and dotnet-ecommerce byte-identical.

---

## BUG-002

**Reported**: 2026-10-07
**Severity**: high
**Status**: fixed
**GitHub Issue**: _(none)_

**Description**: Java↔.NET bridge can hang: stdout is drained to EOF before stderr, and the timeout is only evaluated after both streams close

**Reproduction Steps**:
1. Use a dotnet-analyzer executable that writes more than the OS pipe buffer (~64 KB) to stderr before writing stdout
2. Run the analysis of a .NET project
3. Observe the bridge blocks forever; the configured timeout never fires

**Root Cause**: `DotNetBridgeAnalyzer` read stdout to EOF, then stderr, then `waitFor(timeout)`: a full stderr pipe blocked the child, and blocking reads prevented the timeout from firing.

**Fix Reference**: Both streams drained concurrently (`CompletableFuture`), `waitFor` enforced before joining, process tree killed on timeout; timeout injectable; test `DotNetBridgeProcessTest`.

---

## BUG-003

**Reported**: 2026-10-07
**Severity**: medium
**Status**: fixed
**GitHub Issue**: _(none)_

**Description**: The dotnet-analyzer tool locator does not find `dotnet-analyzer.exe` on Windows

**Reproduction Steps**:
1. On Windows, place `dotnet-analyzer.exe` next to the JAR (or in PATH / DOTNET_ANALYZER_PATH directory)
2. Analyze a .NET project
3. Observe "Could not find 'dotnet-analyzer' executable"

**Root Cause**: `DotNetToolLocator` only resolved the extension-less name `dotnet-analyzer`.

**Fix Reference**: Locator tries `dotnet-analyzer.exe` first on Windows (sibling, DOTNET_ANALYZER_PATH, PATH); tests in `DotNetToolLocatorTest`.

---

## BUG-004

**Reported**: 2026-10-07
**Severity**: high
**Status**: fixed
**GitHub Issue**: _(none)_

**Description**: Proposal names can differ between runs: the domain-token extraction stops at the first matching role keyword, but role keywords are iterated in an unspecified, per-process order

**Reproduction Steps**:
1. Analyze a project with a class whose name contains two role keywords (e.g. `OrderServiceHandler`)
2. Repeat the analysis in a new process several times
3. Observe the proposal name alternates (e.g. "Componente de Order" vs "Componente de Order y Orderservice")

**Root Cause**: `MicroserviceNameGenerator.extractDomainTokens` breaks on the first matching role while iterating `Set.of(...)`, whose iteration order is salted per JVM.

**Fix Reference**: role keywords held in `List.of(...)` (fixed precedence); test `MicroserviceNameGeneratorTest`; 5 runs on jee-architecture-master byte-identical.
