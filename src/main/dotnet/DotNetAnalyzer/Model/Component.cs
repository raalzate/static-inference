using System.Text.Json.Serialization;

namespace DotNetAnalyzer.Model;

public class Component
{
    [JsonPropertyName("id")]
    public string Id { get; set; } = string.Empty;

    [JsonPropertyName("files")]
    public List<string> Files { get; set; }

    [JsonPropertyName("loc")]
    public int Loc { get; set; }

    [JsonPropertyName("tables_used")]
    public List<string> TablesUsed { get; set; }

    [JsonPropertyName("sensitive_data")]
    public bool SensitiveData { get; set; }

    [JsonPropertyName("domain")]
    public string? Domain { get; set; }

    [JsonPropertyName("layer")]
    public string? Layer { get; set; }

    [JsonPropertyName("calls_out")]
    public List<string> CallsOut { get; set; }

    [JsonPropertyName("calls_in")]
    public List<string> CallsIn { get; set; }

    [JsonPropertyName("ejb_type")]
    public string? EjbType { get; set; }

    [JsonPropertyName("uses_jndi")]
    public bool UsesJndi { get; set; }

    [JsonPropertyName("annotations")]
    public List<string> Annotations { get; set; }

    [JsonPropertyName("is_interface")]
    public bool IsInterface { get; set; }

    [JsonPropertyName("secrets_references")]
    public List<string> SecretsReferences { get; set; }

    [JsonPropertyName("external_dependencies")]
    public List<string> ExternalDependencies { get; set; }

    [JsonPropertyName("package_dependencies")]
    public List<PackageGroup> PackageDependencies { get; set; }

    [JsonPropertyName("code_issues")]
    public List<CodeIssue> CodeIssues { get; set; }

    [JsonPropertyName("extends")]
    public string? Extends { get; set; }

    [JsonPropertyName("implements")]
    public List<string> Implements { get; set; }

    [JsonPropertyName("messaging_type")]
    public string? MessagingType { get; set; }

    [JsonPropertyName("messaging_role")]
    public string? MessagingRole { get; set; }

    [JsonPropertyName("web_type")]
    public string? WebType { get; set; }

    [JsonPropertyName("web_role")]
    public string? WebRole { get; set; }

    [JsonPropertyName("cbo")]
    public int? Cbo { get; set; }

    [JsonPropertyName("lcom")]
    public double? Lcom { get; set; }

    public Component()
    {
        Files = new List<string>();
        TablesUsed = new List<string>();
        CallsOut = new List<string>();
        CallsIn = new List<string>();
        Annotations = new List<string>();
        SecretsReferences = new List<string>();
        ExternalDependencies = new List<string>();
        PackageDependencies = new List<PackageGroup>();
        CodeIssues = new List<CodeIssue>();
        Implements = new List<string>();
    }
}

public class PackageGroup
{
    [JsonPropertyName("group")]
    public string Group { get; set; } = string.Empty;

    [JsonPropertyName("artifact")]
    public string Artifact { get; set; } = string.Empty;

    [JsonPropertyName("version")]
    public string? Version { get; set; }
}

public class CodeIssue
{
    /// <summary>Rule identifier (C# API). Serialized as "pattern" for Java compatibility.</summary>
    [JsonPropertyName("pattern")]
    public string Rule { get; set; } = string.Empty;

    [JsonPropertyName("type")]
    public string Type { get; set; } = "BUG_PATTERN";

    [JsonPropertyName("severity")]
    public string Severity { get; set; } = string.Empty;

    [JsonPropertyName("message")]
    public string Message { get; set; } = string.Empty;

    [JsonPropertyName("line")]
    public int Line { get; set; }

    /// <summary>Human-readable location (file:line). Not serialized to JSON.</summary>
    [JsonIgnore]
    public string? Location { get; set; }
}
