using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.MSBuild;
using DotNetAnalyzer.Analysis.Common;
using DotNetAnalyzer.Analysis.Vb;
using DotNetAnalyzer.Model;

namespace DotNetAnalyzer.Analysis;

public class ProjectAnalyzer
{
    private static bool _msBuildRegistered;
    private static readonly object _registrationLock = new();

    private readonly TypeAnalyzer _typeAnalyzer = new();
    private readonly InvocationAnalyzer _invocationAnalyzer = new();
    private readonly DependencyInjectionAnalyzer _diAnalyzer = new();
    private readonly EndpointExtractor _endpointExtractor = new();
    private readonly TableExtractor _tableExtractor = new();
    private readonly MetricsCalculator _metricsCalculator = new();
    private readonly LayerClassifier _layerClassifier = new();
    private readonly MessagingDetector _messagingDetector = new();
    private readonly SecretsDetector _secretsDetector = new();
    private readonly RoslynCodeAnalyzer _codeAnalyzer = new();

    public async Task<DependencyGraph> AnalyzeProjectAsync(string projectPath)
    {
        var compilations = new List<Compilation>();
        bool usedFallback = false;

        try
        {
            compilations = await LoadCompilationsViaMSBuild(projectPath);
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine($"[WARN] MSBuildWorkspace failed: {ex.Message}. Falling back to AdhocWorkspace.");
        }

        if (compilations.Count == 0)
        {
            try
            {
                var fallback = LoadCompilationsViaAdhoc(projectPath);
                if (fallback.Count > 0)
                {
                    compilations.AddRange(fallback);
                    usedFallback = true;
                    Console.Error.WriteLine("[WARN] Using AdhocWorkspace fallback — semantic analysis may have reduced accuracy.");
                }
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine($"[WARN] AdhocWorkspace fallback also failed: {ex.Message}");
            }
        }

        if (compilations.Count == 0)
        {
            Console.Error.WriteLine("[WARN] No compilations could be loaded. Returning empty dependency graph.");
            return BuildEmptyGraph();
        }

        // Aggregate results across all compilations
        var allComponents = new List<Component>();
        var allEdges = new List<Edge>();
        var allEndpoints = new List<ApiEndpoint>();

        foreach (var compilation in compilations)
        {
            try
            {
                // Pass 1: Type analysis
                var components = _typeAnalyzer.AnalyzeTypes(compilation);

                // Pass 2: Invocation analysis
                var edges = _invocationAnalyzer.AnalyzeInvocations(compilation, components);

                // Pass 3: Dependency injection analysis
                var diEdges = _diAnalyzer.Analyze(compilation, components);
                edges.AddRange(diEdges);

                // Pass 4: Endpoint extraction
                var endpoints = _endpointExtractor.ExtractEndpoints(compilation, components);

                // Pass 5: Table extraction (mutates components in-place)
                _tableExtractor.ExtractTables(compilation, components);

                // Pass 6: Build calls_in / calls_out from edges
                BuildCallLists(components, edges);

                // Pass 7: Quality metrics (CBO, LCOM, LOC)
                _metricsCalculator.Calculate(compilation, components);

                // Pass 8: Layer classification
                _layerClassifier.Classify(components);

                // Pass 9: Messaging detection (MassTransit, Azure Service Bus)
                _messagingDetector.Detect(compilation, components);

                // Pass 10: Secrets/configuration detection (IConfiguration, appsettings)
                _secretsDetector.Detect(compilation, components);

                // Pass 11: Code issue detection (bug patterns, style, security)
                _codeAnalyzer.Analyze(compilation, components);

                allComponents.AddRange(components);
                allEdges.AddRange(edges);
                allEndpoints.AddRange(endpoints);
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine($"[WARN] Error analyzing compilation: {ex.Message}");
            }
        }

        // Determinism (Constitución IV, SC-008): final ordering pass over every
        // list serialized into the JSON output. Done once, here, after all passes.
        var orderedComponents = allComponents
            .OrderBy(c => c.Id, StringComparer.Ordinal)
            .ToList();

        foreach (var c in orderedComponents)
        {
            c.Files = c.Files.OrderBy(f => f, StringComparer.Ordinal).ToList();
            c.TablesUsed = c.TablesUsed.Distinct(StringComparer.Ordinal).OrderBy(t => t, StringComparer.Ordinal).ToList();
            c.SecretsReferences = c.SecretsReferences.Distinct(StringComparer.Ordinal).OrderBy(s => s, StringComparer.Ordinal).ToList();
            c.ExternalDependencies = c.ExternalDependencies.Distinct(StringComparer.Ordinal).OrderBy(d => d, StringComparer.Ordinal).ToList();
            c.Annotations = c.Annotations.Distinct(StringComparer.Ordinal).OrderBy(a => a, StringComparer.Ordinal).ToList();
            c.Implements = c.Implements.Distinct(StringComparer.Ordinal).OrderBy(i => i, StringComparer.Ordinal).ToList();
            c.CallsIn = c.CallsIn.Distinct(StringComparer.Ordinal).OrderBy(x => x, StringComparer.Ordinal).ToList();
            c.CallsOut = c.CallsOut.Distinct(StringComparer.Ordinal).OrderBy(x => x, StringComparer.Ordinal).ToList();
        }

        var orderedEdges = allEdges
            .OrderBy(e => e.From, StringComparer.Ordinal)
            .ThenBy(e => e.To, StringComparer.Ordinal)
            .ThenBy(e => e.Type, StringComparer.Ordinal)
            .ToList();

        var orderedEndpoints = allEndpoints
            .OrderBy(e => e.Method, StringComparer.Ordinal)
            .ThenBy(e => e.Path, StringComparer.Ordinal)
            .ToList();

        // Assemble the dependency graph
        var graph = new DependencyGraph
        {
            Components = orderedComponents,
            Edges = orderedEdges,
            ApiContracts = new ApiContracts
            {
                Endpoints = orderedEndpoints,
            },
            Meta = new Meta
            {
                Source = usedFallback ? "roslyn-fallback" : "roslyn",
                CollectedAt = DateTime.UtcNow.ToString("o"),
            },
        };

        return graph;
    }

    private async Task<List<Compilation>> LoadCompilationsViaMSBuild(string projectPath)
    {
        EnsureMSBuildRegistered();

        var compilations = new List<Compilation>();
        var workspace = MSBuildWorkspace.Create();

        workspace.WorkspaceFailed += (sender, args) =>
        {
            if (args.Diagnostic.Kind == WorkspaceDiagnosticKind.Failure)
            {
                Console.Error.WriteLine($"[WARN] Workspace diagnostic: {args.Diagnostic.Message}");
            }
        };

        // Resolve directory → find .sln, .csproj or .vbproj automatically.
        // .sln has highest precedence; otherwise we collect all .csproj AND .vbproj
        // present in the directory (so a mixed directory is processed end to end).
        var resolvedPaths = new List<string>();
        if (Directory.Exists(projectPath))
        {
            var slnFiles = Directory.GetFiles(projectPath, "*.sln");
            // Stable order for determinism (Constitución IV).
            Array.Sort(slnFiles, StringComparer.Ordinal);

            if (slnFiles.Length > 0)
            {
                resolvedPaths.Add(slnFiles[0]);
            }
            else
            {
                var csprojFiles = Directory.GetFiles(projectPath, "*.csproj");
                var vbprojFiles = Directory.GetFiles(projectPath, "*.vbproj");
                Array.Sort(csprojFiles, StringComparer.Ordinal);
                Array.Sort(vbprojFiles, StringComparer.Ordinal);

                resolvedPaths.AddRange(csprojFiles);
                resolvedPaths.AddRange(vbprojFiles);

                if (resolvedPaths.Count == 0)
                    throw new ArgumentException(
                        $"No .sln, .csproj or .vbproj found in directory: {projectPath}");
            }
        }
        else
        {
            resolvedPaths.Add(projectPath);
        }

        if (resolvedPaths.Count == 1
            && resolvedPaths[0].EndsWith(".vbproj", StringComparison.OrdinalIgnoreCase))
        {
            VbProjectLoader.WarnIfNonWindows();
        }

        foreach (var resolvedPath in resolvedPaths)
        {
            if (resolvedPath.EndsWith(".sln", StringComparison.OrdinalIgnoreCase))
            {
                var solution = await workspace.OpenSolutionAsync(resolvedPath);
                var orderedProjects = solution.Projects
                    .OrderBy(p => p.FilePath ?? p.Name, StringComparer.Ordinal)
                    .ToList();

                foreach (var project in orderedProjects)
                {
                    try
                    {
                        var compilation = await project.GetCompilationAsync();
                        if (compilation != null)
                        {
                            compilations.Add(VbProjectLoader.StripAutogenerated(compilation));
                        }
                    }
                    catch (Exception ex)
                    {
                        Console.Error.WriteLine($"[WARN] Could not compile project '{project.Name}': {ex.Message}");
                    }
                }
            }
            else if (resolvedPath.EndsWith(".csproj", StringComparison.OrdinalIgnoreCase))
            {
                var project = await workspace.OpenProjectAsync(resolvedPath);
                var compilation = await project.GetCompilationAsync();
                if (compilation != null)
                {
                    compilations.Add(VbProjectLoader.StripAutogenerated(compilation));
                }
            }
            else if (resolvedPath.EndsWith(".vbproj", StringComparison.OrdinalIgnoreCase))
            {
                Compilation? compilation = null;
                try
                {
                    var project = await workspace.OpenProjectAsync(resolvedPath);
                    compilation = await project.GetCompilationAsync();
                }
                catch (Exception ex)
                {
                    Console.Error.WriteLine($"[WARN] MSBuildWorkspace failed for '{resolvedPath}': {ex.Message}");
                }

                // Legacy .vbproj on systems without VS Build Tools resolves but yields
                // an empty compilation. Detect that and fall back to Adhoc per-project
                // so we still emit a usable graph for this project.
                if (compilation == null || !compilation.SyntaxTrees.Any())
                {
                    var adhoc = VbProjectLoader.LoadAdhoc(resolvedPath);
                    if (adhoc != null)
                    {
                        Console.Error.WriteLine(
                            $"[WARN] Falling back to Adhoc VB compilation for '{resolvedPath}' (MSBuild returned no syntax trees).");
                        compilations.Add(adhoc);
                    }
                }
                else
                {
                    compilations.Add(VbProjectLoader.StripAutogenerated(compilation));
                }
            }
            else
            {
                throw new ArgumentException(
                    $"Unsupported project file type: {resolvedPath}. Expected .sln, .csproj or .vbproj");
            }
        }

        return compilations;
    }

    private static List<Compilation> LoadCompilationsViaAdhoc(string projectPath)
    {
        var compilations = new List<Compilation>();

        // Decide language by extension (file) or by detecting .vb / .cs in the dir.
        bool isVbProj = File.Exists(projectPath)
                        && projectPath.EndsWith(".vbproj", StringComparison.OrdinalIgnoreCase);
        bool isCsProj = File.Exists(projectPath)
                        && projectPath.EndsWith(".csproj", StringComparison.OrdinalIgnoreCase);

        var directory = File.Exists(projectPath)
            ? Path.GetDirectoryName(projectPath) ?? "."
            : projectPath;

        if (isVbProj)
        {
            var vbCompilation = VbProjectLoader.LoadAdhoc(projectPath);
            if (vbCompilation != null)
                compilations.Add(vbCompilation);
            return compilations;
        }

        if (!isCsProj)
        {
            // Directory or .sln-flavored fallback: glob both languages.
            var vbCompilation = VbProjectLoader.LoadAdhoc(directory);
            if (vbCompilation != null)
                compilations.Add(vbCompilation);
        }

        var csCompilation = LoadCSharpAdhoc(directory);
        if (csCompilation != null)
            compilations.Add(csCompilation);

        return compilations;
    }

    private static Compilation? LoadCSharpAdhoc(string directory)
    {
        var csFiles = Directory.GetFiles(directory, "*.cs", SearchOption.AllDirectories);

        if (csFiles.Length == 0)
        {
            Console.Error.WriteLine("[WARN] No .cs files found in directory.");
            return null;
        }

        Array.Sort(csFiles, StringComparer.Ordinal);

        var syntaxTrees = new List<SyntaxTree>();
        foreach (var file in csFiles)
        {
            try
            {
                var sourceText = File.ReadAllText(file);
                var tree = CSharpSyntaxTree.ParseText(sourceText, path: file);
                syntaxTrees.Add(tree);
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine($"[WARN] Could not parse file '{file}': {ex.Message}");
            }
        }

        if (syntaxTrees.Count == 0)
        {
            return null;
        }

        // In single-file apps Assembly.Location can be empty; collect from loaded assemblies instead
        var references = AppDomain.CurrentDomain.GetAssemblies()
            .Select(a => a.Location)
            .Where(loc => !string.IsNullOrEmpty(loc) && File.Exists(loc))
            .Select(loc => (MetadataReference)MetadataReference.CreateFromFile(loc))
            .ToArray();

        var compilation = CSharpCompilation.Create(
            "FallbackCompilation",
            syntaxTrees,
            references);

        return compilation;
    }

    private static void BuildCallLists(List<Component> components, List<Edge> edges)
    {
        var componentMap = components.ToDictionary(c => c.Id, c => c);

        foreach (var edge in edges)
        {
            if (componentMap.TryGetValue(edge.From, out var fromComponent))
            {
                if (!fromComponent.CallsOut.Contains(edge.To))
                {
                    fromComponent.CallsOut.Add(edge.To);
                }
            }

            if (componentMap.TryGetValue(edge.To, out var toComponent))
            {
                if (!toComponent.CallsIn.Contains(edge.From))
                {
                    toComponent.CallsIn.Add(edge.From);
                }
            }
        }
    }

    private static void EnsureMSBuildRegistered()
    {
        if (_msBuildRegistered) return;

        lock (_registrationLock)
        {
            if (_msBuildRegistered) return;

            try
            {
                var instances = Microsoft.Build.Locator.MSBuildLocator.QueryVisualStudioInstances().ToList();
                if (instances.Count > 0)
                {
                    Microsoft.Build.Locator.MSBuildLocator.RegisterInstance(instances.OrderByDescending(i => i.Version).First());
                }
                else
                {
                    Microsoft.Build.Locator.MSBuildLocator.RegisterDefaults();
                }
            }
            catch (InvalidOperationException)
            {
                // Already registered — safe to ignore
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine($"[WARN] MSBuildLocator registration failed: {ex.Message}");
            }

            _msBuildRegistered = true;
        }
    }

    private static DependencyGraph BuildEmptyGraph()
    {
        return new DependencyGraph
        {
            Meta = new Meta
            {
                Source = "roslyn",
                CollectedAt = DateTime.UtcNow.ToString("o"),
            },
        };
    }
}
