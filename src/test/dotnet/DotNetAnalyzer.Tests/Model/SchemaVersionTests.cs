using System.Text.Json.Nodes;
using DotNetAnalyzer.Model;
using DotNetAnalyzer.Tests.SchemaContract;
using FluentAssertions;
using Xunit;

namespace DotNetAnalyzer.Tests.Model;

/// <summary>
/// 005-json-schema-contract US2: the .NET analyzer declares meta.schema_version before any engine
/// reprocessing (FR-009) and stays deterministic (FR-010/FR-011).
/// </summary>
[Trait("Category", "SchemaContract")]
[Collection("EnvironmentVariables")]
public class SchemaVersionTests
{
    [Theory(DisplayName = "TS-015: raw graph declares schema_version 1.0.0 right after source")]
    [InlineData("SampleProject")]
    [InlineData("CodeIssuesSample")]
    [InlineData("VbLegacy")]
    public async Task RawGraph_DeclaresSchemaVersionAfterSource(string fixture)
    {
        Environment.SetEnvironmentVariable(Meta.CollectedAtOptIn, null);

        var json = await GraphJson.AnalyzeFixtureAsync(fixture);

        var meta = JsonNode.Parse(json)!["meta"]!.AsObject();
        meta["schema_version"]!.GetValue<string>().Should().Be("1.0.0");
        var names = meta.Select(p => p.Key).ToList();
        names.IndexOf("schema_version").Should().Be(names.IndexOf("source") + 1, $"meta order was {string.Join(",", names)}");
    }

    [Fact(DisplayName = "TS-015: SchemaVersion.Current matches schema/VERSION")]
    public void Current_MatchesContractVersion()
    {
        SchemaVersion.Current.Should().Be("1.0.0");
        SchemaVersion.Current.Should().Be(ContractSchemas.Shared.Version);
    }

    [Fact(DisplayName = "TS-017: two consecutive raw runs are byte-identical without collected_at")]
    public async Task TwoRuns_AreByteIdentical()
    {
        Environment.SetEnvironmentVariable(Meta.CollectedAtOptIn, null);

        var first = await GraphJson.AnalyzeFixtureAsync("SampleProject");
        var second = await GraphJson.AnalyzeFixtureAsync("SampleProject");

        second.Should().Be(first);
        first.Should().NotContain("collected_at");
    }
}
