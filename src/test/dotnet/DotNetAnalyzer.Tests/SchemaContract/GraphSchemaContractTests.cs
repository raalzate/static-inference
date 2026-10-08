using System.Text.Json.Nodes;
using DotNetAnalyzer.Model;
using FluentAssertions;
using Xunit;

namespace DotNetAnalyzer.Tests.SchemaContract;

/// <summary>
/// TS-003 (FR-006, FR-013): the raw dependency graph emitted by the .NET analyzer (null properties omitted)
/// validates against the formal contract. Each validated graph is written to
/// <c>target/schema-samples/dotnet/&lt;fixture&gt;/output.json</c> for the generated TS validators (TS-031).
/// </summary>
[Trait("Category", "SchemaContract")]
[Collection("EnvironmentVariables")]
public class GraphSchemaContractTests
{
    [Theory(DisplayName = "TS-003: raw .NET graph validates against the dependency-graph contract")]
    [InlineData("SampleProject")]
    [InlineData("CodeIssuesSample")]
    [InlineData("VbLegacy")]
    public async Task RawGraph_ValidatesAgainstContract(string fixture)
    {
        Environment.SetEnvironmentVariable(Meta.CollectedAtOptIn, null);

        var json = await GraphJson.AnalyzeFixtureAsync(fixture);
        var errors = ContractSchemas.Shared.Validate("dependency-graph", JsonNode.Parse(json));

        errors.Should().BeEmpty();
        json.Should().NotContain(": null", "the .NET analyzer omits null properties");
        WriteSample(fixture, json);
    }

    [Fact(DisplayName = "TS-003: an out-of-catalogue layer in a raw .NET graph is rejected with its path")]
    public async Task RawGraph_WithUnknownLayer_IsRejected()
    {
        var node = JsonNode.Parse(await GraphJson.AnalyzeFixtureAsync("SampleProject"))!;
        node["components"]![0]!["layer"] = "Servicio";

        var errors = ContractSchemas.Shared.Validate("dependency-graph", node);

        errors.Should().Contain(e => e.StartsWith("/components/0/layer"));
    }

    private static void WriteSample(string fixture, string json)
    {
        var dir = Path.Combine(ContractSchemas.Shared.RepoRoot, "target", "schema-samples", "dotnet", fixture);
        Directory.CreateDirectory(dir);
        File.WriteAllText(Path.Combine(dir, "output.json"), json);
    }
}
