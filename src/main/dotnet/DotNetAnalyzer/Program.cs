using DotNetAnalyzer.Analysis;
using System.Text.Json;
using System.Text.Json.Serialization;

try
{
    if (args.Length != 1)
    {
        Console.Error.WriteLine("Usage: dotnet-analyzer <path>");
        Console.Error.WriteLine("  <path>  Path to a .sln, .csproj file or project directory");
        return 1;
    }

    var path = args[0];

    if (!File.Exists(path) && !Directory.Exists(path))
    {
        Console.Error.WriteLine($"Error: Path not found: {path}");
        return 1;
    }

    var analyzer = new ProjectAnalyzer();
    var graph = await analyzer.AnalyzeProjectAsync(path);

    var options = new JsonSerializerOptions
    {
        PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower,
        WriteIndented = true,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
    };

    var json = JsonSerializer.Serialize(graph, options);
    Console.Write(json);

    return 0;
}
catch (Exception ex)
{
    Console.Error.WriteLine($"Error: {ex.Message}");
    return 1;
}
