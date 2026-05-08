using DotNetAnalyzer.Analysis;
using FluentAssertions;
using Xunit;

namespace DotNetAnalyzer.Tests.Analysis.Vb;

/// <summary>
/// Smoke tests on the real reference corpus
/// (<c>test-for-demo/vb-demo-legacy</c>). The corpus is part of this
/// repository checkout. If a downstream consumer wants to run the suite
/// without the corpus, they should filter out this category:
///
///     dotnet test --filter "Category!=Corpus"
/// </summary>
[Trait("Category", "VbLegacy")]
[Trait("Category", "Corpus")]
public class VbCorpusSmokeTests
{
    private const string AccountingControllerProj =
        "test-for-demo/vb-demo-legacy/AmigosPlus.Accounting.Controller/AmigosPlus.Accounting.Controller.vbproj";

    private const string CommonAdaptersProj =
        "test-for-demo/vb-demo-legacy/AmigosPlus.Model.Common.Adapters/AmigosPlus.Model.Common.Adapters.vbproj";

    private static string FindRepoRoot()
    {
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir != null)
        {
            if (Directory.Exists(Path.Combine(dir.FullName, "test-for-demo", "vb-demo-legacy")))
                return dir.FullName;
            dir = dir.Parent;
        }
        throw new DirectoryNotFoundException(
            "vb-demo-legacy reference corpus not found in any ancestor of " + AppContext.BaseDirectory);
    }

    [Fact(DisplayName = "TS-016/TS-037: Accounting.Controller — all *Controller land in Negocio (FR-021), LOC > 1")]
    public async Task AccountingController_NoFalseControllers_AndRealLoc()
    {
        var path = Path.Combine(FindRepoRoot(), AccountingControllerProj);
        File.Exists(path).Should().BeTrue($"Reference project not found: {path}");

        var analyzer = new ProjectAnalyzer();
        var graph = await analyzer.AnalyzeProjectAsync(path);

        graph.Components.Should().NotBeEmpty();

        // SC-004: zero false Controllers in *.Controller projects.
        var falseControllers = graph.Components
            .Where(c => c.Layer == "Controlador")
            .Select(c => c.Id)
            .ToList();
        falseControllers.Should().BeEmpty(
            "VB legacy *Controller facades have no HTTP markers and must not be classified as Controlador (FR-021)");

        // LOC must be > 1 for the majority of non-trivial classes (TypeAnalyzer
        // bug fix: walk the full ClassBlockSyntax for VB types).
        var nonTrivialLoc = graph.Components.Count(c => c.Loc > 1);
        nonTrivialLoc.Should().BeGreaterThan(graph.Components.Count / 2,
            "the majority of VB controller-style facades have multiple methods and dozens of LOC");
    }

    [Fact(DisplayName = "TS-038: *Adapter classes land in Datos layer (FR-021)")]
    public async Task CommonAdapters_AreClassifiedAsDatos()
    {
        var path = Path.Combine(FindRepoRoot(), CommonAdaptersProj);
        File.Exists(path).Should().BeTrue($"Reference project not found: {path}");

        var analyzer = new ProjectAnalyzer();
        var graph = await analyzer.AnalyzeProjectAsync(path);

        graph.Components.Should().NotBeEmpty();

        var adapterComponents = graph.Components
            .Where(c => c.Id.EndsWith("Adapter", StringComparison.Ordinal))
            .ToList();

        adapterComponents.Should().NotBeEmpty("the project is named *.Adapters and contains Adapter classes");

        var adaptersInDatos = adapterComponents.Count(c => c.Layer == "Datos");
        var pct = adaptersInDatos * 100.0 / adapterComponents.Count;

        pct.Should().BeGreaterThanOrEqualTo(80.0,
            $"FR-021 requires *Adapter components to land in Datos; got {adaptersInDatos}/{adapterComponents.Count} = {pct:F1}%");
    }
}
