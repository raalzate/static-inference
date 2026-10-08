namespace DotNetAnalyzer.Model;

/// <summary>
/// Version of the formal output contract (<c>schema/VERSION</c>) declared as <c>meta.schema_version</c>
/// (005-json-schema-contract, FR-009). Must match the Java engine; verified by <c>make schema-check</c>.
/// </summary>
public static class SchemaVersion
{
    public const string Current = "1.0.0";
}
