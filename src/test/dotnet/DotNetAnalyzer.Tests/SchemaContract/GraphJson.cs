using System.Text.Json;
using System.Text.Json.Serialization;
using DotNetAnalyzer.Analysis;
using DotNetAnalyzer.Model;

namespace DotNetAnalyzer.Tests.SchemaContract;

/// <summary>
/// Produces the raw dependency graph JSON exactly as <c>dotnet-analyzer</c> writes it on stdout
/// (same serializer options as Program.cs) for the test fixtures.
/// </summary>
public static class GraphJson
{
    public static readonly JsonSerializerOptions ProgramOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower,
        WriteIndented = true,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
    };

    /// <summary>Fixture name → analyzer target, relative to <c>Fixtures/</c> in the test sources.</summary>
    public static string FixtureTarget(string fixture)
    {
        var fixtures = Path.Combine(ContractSchemas.Shared.RepoRoot, "src", "test", "dotnet",
            "DotNetAnalyzer.Tests", "Fixtures");
        return fixture switch
        {
            "SampleProject" => Path.Combine(fixtures, "SampleProject"),
            "CodeIssuesSample" => Path.Combine(fixtures, "CodeIssuesSample"),
            "VbLegacy" => Path.Combine(fixtures, "VbLegacy", "MinimalVbProject", "Minimal.vbproj"),
            _ => throw new ArgumentException($"Unknown fixture {fixture}", nameof(fixture)),
        };
    }

    public static string Serialize(DependencyGraph graph) => JsonSerializer.Serialize(graph, ProgramOptions);

    public static async Task<string> AnalyzeFixtureAsync(string fixture)
    {
        var graph = await new ProjectAnalyzer().AnalyzeProjectAsync(FixtureTarget(fixture));
        return Serialize(graph);
    }
}
