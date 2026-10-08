using System.Text.Json.Nodes;
using Json.Schema;

namespace DotNetAnalyzer.Tests.SchemaContract;

/// <summary>
/// Loads the formal contract from <c>schema/&lt;VERSION&gt;/</c> (found by walking up from the test
/// assembly until <c>schema/VERSION</c> exists) and validates JSON documents against it
/// (005-json-schema-contract, plan Design Note 4). Every schema is registered under its absolute
/// <c>$id</c> (<c>https://static-inference.dev/schema/&lt;VERSION&gt;/...</c>), so references resolve offline.
/// </summary>
public sealed class ContractSchemas
{
    private static readonly Lazy<ContractSchemas> SharedInstance = new(() => new ContractSchemas(LocateSchemaRoot()));

    private readonly Dictionary<string, JsonSchema> _documents = new(StringComparer.Ordinal);
    private readonly List<(Uri Id, JsonSchema Schema)> _all = new();

    public static ContractSchemas Shared => SharedInstance.Value;

    public string SchemaRoot { get; }

    public string RepoRoot => Directory.GetParent(SchemaRoot)!.FullName;

    public string Version { get; }

    public string BaseIri => $"https://static-inference.dev/schema/{Version}/";

    private ContractSchemas(string schemaRoot)
    {
        SchemaRoot = schemaRoot;
        Version = File.ReadAllText(Path.Combine(schemaRoot, "VERSION")).Trim();
        var versionDir = Path.Combine(schemaRoot, Version);
        foreach (var file in Directory.EnumerateFiles(versionDir, "*.schema.json", SearchOption.AllDirectories)
                     .OrderBy(f => f, StringComparer.Ordinal))
        {
            var relative = Path.GetRelativePath(versionDir, file).Replace('\\', '/');
            var schema = JsonSchema.FromText(File.ReadAllText(file));
            _all.Add((new Uri(BaseIri + relative), schema));
            _documents[relative[..^".schema.json".Length]] = schema;
        }
    }

    /// <summary>
    /// Validates a document. <paramref name="kind"/> is <c>dependency-graph</c>, <c>architecture</c>,
    /// <c>entrypoints</c> or <c>mcp/&lt;tool&gt;.response</c>.
    /// Returns errors formatted as <c>&lt;JSON Pointer&gt;: &lt;keyword&gt;: &lt;message&gt;</c>; empty when valid.
    /// </summary>
    public IReadOnlyList<string> Validate(string kind, JsonNode? document)
    {
        if (!_documents.TryGetValue(kind, out var schema))
        {
            throw new ArgumentException($"Unknown contract kind '{kind}'", nameof(kind));
        }

        var options = new EvaluationOptions
        {
            OutputFormat = OutputFormat.List,
            RequireFormatValidation = true,
        };
        foreach (var (id, registered) in _all)
        {
            options.SchemaRegistry.Register(id, registered);
        }

        var results = schema.Evaluate(document, options);
        if (results.IsValid)
        {
            return Array.Empty<string>();
        }

        var errors = new List<string>();
        foreach (var detail in results.Details.Append(results))
        {
            if (detail.Errors is null)
            {
                continue;
            }

            foreach (var (keyword, message) in detail.Errors)
            {
                errors.Add($"{detail.InstanceLocation}: {keyword}: {message}");
            }
        }

        return errors.Distinct(StringComparer.Ordinal).OrderBy(e => e, StringComparer.Ordinal).ToList();
    }

    private static string LocateSchemaRoot()
    {
        var current = new DirectoryInfo(AppContext.BaseDirectory);
        while (current != null)
        {
            var candidate = Path.Combine(current.FullName, "schema", "VERSION");
            if (File.Exists(candidate))
            {
                return Path.GetDirectoryName(candidate)!;
            }

            current = current.Parent;
        }

        throw new DirectoryNotFoundException($"schema/VERSION not found above {AppContext.BaseDirectory}");
    }
}
