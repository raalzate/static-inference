using System.Text.Json;
using System.Text.Json.Serialization;
using FluentAssertions;
using DotNetAnalyzer.Model;
using Xunit;

namespace DotNetAnalyzer.Tests.Model;

/// <summary>
/// [BUG-001] meta.collected_at is opt-in so output stays deterministic.
/// </summary>
[Collection("EnvironmentVariables")]
public class MetaDeterminismTests
{
    private static readonly JsonSerializerOptions Options = new()
    {
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
    };

    [Fact(DisplayName = "BUG-001: collected_at omitted by default")]
    public void CollectedAt_ByDefault_IsOmitted()
    {
        Environment.SetEnvironmentVariable(Meta.CollectedAtOptIn, null);

        var json = JsonSerializer.Serialize(new Meta { CollectedAt = Meta.CurrentCollectedAt() }, Options);

        json.Should().NotContain("collected_at");
    }

    [Fact(DisplayName = "BUG-001: collected_at emitted when opted in")]
    public void CollectedAt_WithOptIn_IsEmitted()
    {
        Environment.SetEnvironmentVariable(Meta.CollectedAtOptIn, "true");
        try
        {
            var json = JsonSerializer.Serialize(new Meta { CollectedAt = Meta.CurrentCollectedAt() }, Options);

            json.Should().Contain("collected_at");
        }
        finally
        {
            Environment.SetEnvironmentVariable(Meta.CollectedAtOptIn, null);
        }
    }
}
