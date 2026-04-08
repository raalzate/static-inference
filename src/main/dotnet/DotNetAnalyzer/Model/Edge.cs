using System.Text.Json.Serialization;

namespace DotNetAnalyzer.Model;

public class Edge
{
    [JsonPropertyName("from")]
    public string From { get; set; } = string.Empty;

    [JsonPropertyName("to")]
    public string To { get; set; } = string.Empty;

    [JsonPropertyName("weight")]
    public int Weight { get; set; }

    [JsonPropertyName("type")]
    public string Type { get; set; } = string.Empty;
}
