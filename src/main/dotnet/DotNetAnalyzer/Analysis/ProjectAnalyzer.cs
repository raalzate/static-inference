using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.MSBuild;
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
                var fallbackCompilation = LoadCompilationViaAdhoc(projectPath);
                if (fallbackCompilation != null)
                {
                    compilations.Add(fallbackCompilation);
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

        // Assemble the dependency graph
        var graph = new DependencyGraph
        {
            Components = allComponents,
            Edges = allEdges,
            ApiContracts = new ApiContracts
            {
                Endpoints = allEndpoints,
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

        // Resolve directory → find .sln or .csproj automatically
        string resolvedPath = projectPath;
        if (Directory.Exists(projectPath))
        {
            var slnFiles = Directory.GetFiles(projectPath, "*.sln");
            var csprojFiles = Directory.GetFiles(projectPath, "*.csproj");

            if (slnFiles.Length > 0)
                resolvedPath = slnFiles[0];
            else if (csprojFiles.Length > 0)
                resolvedPath = csprojFiles[0];
            else
                throw new ArgumentException($"No .sln or .csproj found in directory: {projectPath}");
        }

        if (resolvedPath.EndsWith(".sln", StringComparison.OrdinalIgnoreCase))
        {
            var solution = await workspace.OpenSolutionAsync(resolvedPath);
            foreach (var project in solution.Projects)
            {
                try
                {
                    var compilation = await project.GetCompilationAsync();
                    if (compilation != null)
                        compilations.Add(compilation);
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
                compilations.Add(compilation);
        }
        else
        {
            throw new ArgumentException($"Unsupported project file type: {resolvedPath}. Expected .sln or .csproj");
        }

        return compilations;
    }

    private static Compilation? LoadCompilationViaAdhoc(string projectPath)
    {
        var directory = File.Exists(projectPath)
            ? Path.GetDirectoryName(projectPath) ?? "."
            : projectPath;

        var csFiles = Directory.GetFiles(directory, "*.cs", SearchOption.AllDirectories);

        if (csFiles.Length == 0)
        {
            Console.Error.WriteLine("[WARN] No .cs files found in directory.");
            return null;
        }

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
