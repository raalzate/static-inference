using FluentAssertions;
using DotNetAnalyzer.Analysis;
using DotNetAnalyzer.Model;
using Xunit;

namespace DotNetAnalyzer.Tests.Integration;

public class FullPipelineTests
{
    private static string GetFixturePath()
    {
        var assemblyLocation = Path.GetDirectoryName(
            typeof(FullPipelineTests).Assembly.Location)!;
        var fixturePath = Path.Combine(assemblyLocation, "Fixtures", "SampleProject");

        if (!Directory.Exists(fixturePath))
        {
            // Fallback: walk up from assembly location to find the fixture directory
            var current = new DirectoryInfo(assemblyLocation);
            while (current != null)
            {
                var candidate = Path.Combine(current.FullName, "Fixtures", "SampleProject");
                if (Directory.Exists(candidate))
                {
                    return candidate;
                }
                current = current.Parent;
            }

            throw new DirectoryNotFoundException(
                $"Could not find Fixtures/SampleProject relative to {assemblyLocation}");
        }

        return fixturePath;
    }

    /// <summary>
    /// [TS-001] Full pipeline produces a valid dependency graph with components, edges, and meta.
    /// </summary>
    [Fact]
    public async Task AnalyzeProject_WithSampleProject_ReturnsValidGraph()
    {
        // Arrange
        var fixturePath = GetFixturePath();
        var analyzer = new ProjectAnalyzer();

        // Act
        var graph = await analyzer.AnalyzeProjectAsync(fixturePath);

        // Assert
        graph.Should().NotBeNull();
        graph.Components.Should().NotBeEmpty("the sample project contains analyzable types");
        graph.Edges.Should().NotBeEmpty("the sample project has type dependencies");
        graph.Meta.Should().NotBeNull();
        graph.Meta.Source.Should().Contain("roslyn",
            "the analyzer source should indicate Roslyn was used");
    }

    /// <summary>
    /// [TS-004] Pipeline discovers expected component types from the sample project.
    /// </summary>
    [Fact]
    public async Task AnalyzeProject_WithSampleProject_HasComponents()
    {
        // Arrange
        var fixturePath = GetFixturePath();
        var analyzer = new ProjectAnalyzer();

        // Act
        var graph = await analyzer.AnalyzeProjectAsync(fixturePath);

        // Assert — expected types exist as components
        var componentIds = graph.Components.Select(c => c.Id).ToList();

        componentIds.Should().Contain(id => id.Contains("OrdersController"),
            "the sample project has an OrdersController");
        componentIds.Should().Contain(id => id.Contains("OrderService"),
            "the sample project has an OrderService");
        componentIds.Should().Contain(id => id.Contains("OrderRepository"),
            "the sample project has an OrderRepository");

        // Assert — at least one API endpoint is extracted
        graph.ApiContracts.Should().NotBeNull();
        graph.ApiContracts.Endpoints.Should().NotBeEmpty(
            "the sample project has controller actions that expose API endpoints");

        // Assert — at least one component has tables_used populated
        graph.Components.Should().Contain(c => c.TablesUsed.Count > 0,
            "the sample project has a DbContext that references tables");
    }

    /// <summary>
    /// [TS-001] Meta source field contains "roslyn" indicating the analysis engine.
    /// </summary>
    [Fact]
    public async Task AnalyzeProject_WithSampleProject_MetaSourceIsRoslyn()
    {
        // Arrange
        var fixturePath = GetFixturePath();
        var analyzer = new ProjectAnalyzer();

        // Act
        var graph = await analyzer.AnalyzeProjectAsync(fixturePath);

        // Assert — in test environment without MSBuild, fallback mode sets "roslyn-fallback"
        graph.Meta.Source.Should().Contain("roslyn",
            "whether using MSBuild or fallback mode, the source must indicate Roslyn");
    }
}
