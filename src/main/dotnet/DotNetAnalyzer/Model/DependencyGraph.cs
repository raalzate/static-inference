using System.Text.Json.Serialization;

namespace DotNetAnalyzer.Model;

public class DependencyGraph
{
    [JsonPropertyName("components")]
    public List<Component> Components { get; set; }

    [JsonPropertyName("edges")]
    public List<Edge> Edges { get; set; }

    [JsonPropertyName("api_contracts")]
    public ApiContracts ApiContracts { get; set; }

    [JsonPropertyName("meta")]
    public Meta Meta { get; set; }

    public DependencyGraph()
    {
        Components = new List<Component>();
        Edges = new List<Edge>();
        ApiContracts = new ApiContracts();
        Meta = new Meta();
    }
}

public class ApiContracts
{
    [JsonPropertyName("endpoints")]
    public List<ApiEndpoint> Endpoints { get; set; }

    [JsonPropertyName("schemas")]
    public Dictionary<string, ApiSchema> Schemas { get; set; }

    public ApiContracts()
    {
        Endpoints = new List<ApiEndpoint>();
        Schemas = new Dictionary<string, ApiSchema>();
    }
}

public class Meta
{
    [JsonPropertyName("source")]
    public string Source { get; set; } = "roslyn";

    [JsonPropertyName("collected_at")]
    public string CollectedAt { get; set; } = string.Empty;

    [JsonPropertyName("microservice_candidates")]
    public Dictionary<string, string> MicroserviceCandidates { get; set; }

    [JsonPropertyName("dependency_accuracy")]
    public object? DependencyAccuracy { get; set; }

    [JsonPropertyName("decomposition_accuracy")]
    public object? DecompositionAccuracy { get; set; }

    public Meta()
    {
        MicroserviceCandidates = new Dictionary<string, string>();
    }
}
