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

    /// <summary>
    /// Contract version (FR-009). Declared right after <see cref="Source"/> so it is serialized immediately
    /// after <c>source</c>; the rest of the property order is unchanged (FR-011).
    /// </summary>
    [JsonPropertyName("schema_version")]
    public string SchemaVersion { get; set; } = Model.SchemaVersion.Current;

    /// <summary>Environment variable that opts in to emitting collected_at.</summary>
    public const string CollectedAtOptIn = "STATIC_INFERENCE_COLLECTED_AT";

    /// <summary>Null unless opted in: wall-clock timestamps break deterministic output (BUG-001).</summary>
    [JsonPropertyName("collected_at")]
    public string? CollectedAt { get; set; }

    /// <summary>Current UTC timestamp when opted in via <see cref="CollectedAtOptIn"/>, otherwise null.</summary>
    public static string? CurrentCollectedAt() =>
        bool.TryParse(Environment.GetEnvironmentVariable(CollectedAtOptIn), out var enabled) && enabled
            ? DateTime.UtcNow.ToString("o")
            : null;

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
