using System.Text.Json.Serialization;

namespace DotNetAnalyzer.Model;

public class ApiEndpoint
{
    [JsonPropertyName("id")]
    public string Id { get; set; } = string.Empty;

    [JsonPropertyName("path")]
    public string Path { get; set; } = string.Empty;

    [JsonPropertyName("method")]
    public string Method { get; set; } = string.Empty;

    [JsonPropertyName("description")]
    public string? Description { get; set; }

    [JsonPropertyName("parameters")]
    public List<Parameter> Parameters { get; set; }

    [JsonPropertyName("request_body_schema")]
    public string? RequestBodySchema { get; set; }

    [JsonPropertyName("response_schema")]
    public string? ResponseSchema { get; set; }

    [JsonPropertyName("component_id")]
    public string ComponentId { get; set; } = string.Empty;

    public ApiEndpoint()
    {
        Parameters = new List<Parameter>();
    }
}

public class Parameter
{
    [JsonPropertyName("name")]
    public string Name { get; set; } = string.Empty;

    [JsonPropertyName("in")]
    public string In { get; set; } = string.Empty;

    [JsonPropertyName("type")]
    public string Type { get; set; } = string.Empty;

    [JsonPropertyName("required")]
    public bool Required { get; set; }
}
