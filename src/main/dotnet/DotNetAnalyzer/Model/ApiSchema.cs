using System.Text.Json.Serialization;

namespace DotNetAnalyzer.Model;

public class ApiSchema
{
    [JsonPropertyName("type")]
    public string Type { get; set; } = "object";

    [JsonPropertyName("properties")]
    public Dictionary<string, SchemaProperty> Properties { get; set; }

    public ApiSchema()
    {
        Properties = new Dictionary<string, SchemaProperty>();
    }
}

public class SchemaProperty
{
    [JsonPropertyName("type")]
    public string Type { get; set; } = string.Empty;
}
