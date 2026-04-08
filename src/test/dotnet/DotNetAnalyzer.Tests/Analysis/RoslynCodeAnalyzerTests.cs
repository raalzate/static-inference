using FluentAssertions;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using DotNetAnalyzer.Analysis;
using DotNetAnalyzer.Model;
using Xunit;

namespace DotNetAnalyzer.Tests.Analysis;

public class RoslynCodeAnalyzerTests
{
    private static CSharpCompilation CreateCompilation(params string[] sources)
    {
        var syntaxTrees = sources.Select(s => CSharpSyntaxTree.ParseText(s)).ToArray();

        var references = new[]
        {
            MetadataReference.CreateFromFile(typeof(object).Assembly.Location),
            MetadataReference.CreateFromFile(typeof(Enumerable).Assembly.Location),
            MetadataReference.CreateFromFile(typeof(Console).Assembly.Location),
            MetadataReference.CreateFromFile(typeof(System.Threading.Thread).Assembly.Location),
        };

        var runtimeDir = Path.GetDirectoryName(typeof(object).Assembly.Location)!;
        var runtimeRef = MetadataReference.CreateFromFile(Path.Combine(runtimeDir, "System.Runtime.dll"));

        return CSharpCompilation.Create(
            "TestAssembly",
            syntaxTrees,
            references.Append(runtimeRef),
            new CSharpCompilationOptions(OutputKind.DynamicallyLinkedLibrary));
    }

    private static (RoslynCodeAnalyzer analyzer, List<Component> components) AnalyzeSource(string source)
    {
        var compilation = CreateCompilation(source);
        var analyzer = new RoslynCodeAnalyzer();
        var components = new TypeAnalyzer().AnalyzeTypes(compilation);
        analyzer.Analyze(compilation, components);
        return (analyzer, components);
    }

    private static Component GetComponent(List<Component> components, string nameFragment)
    {
        var comp = components.FirstOrDefault(c => c.Id.Contains(nameFragment));
        comp.Should().NotBeNull($"expected to find component containing '{nameFragment}'");
        return comp!;
    }

    #region TS-001: EMPTY_CATCH_BLOCK — positive

    [Fact]
    public void TS001_EmptyCatchBlock_DetectedAsWarning()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    public void DoWork() {
                        try { var x = 1; }
                        catch (System.Exception e) { }
                    }
                }
            }");

        var svc = GetComponent(components, "Service");
        svc.CodeIssues.Should().ContainSingle(i => i.Rule == "EMPTY_CATCH_BLOCK");
        svc.CodeIssues.First(i => i.Rule == "EMPTY_CATCH_BLOCK").Severity.Should().Be("WARNING");
        svc.CodeIssues.First(i => i.Rule == "EMPTY_CATCH_BLOCK").Message.Should().NotBeNullOrEmpty();
        svc.CodeIssues.First(i => i.Rule == "EMPTY_CATCH_BLOCK").Location.Should().MatchRegex(@":\d+$");
    }

    #endregion

    #region TS-002: EMPTY_CATCH_BLOCK — negative (non-empty catch)

    [Fact]
    public void TS002_NonEmptyCatchBlock_NotFlagged()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    public void DoWork() {
                        try { var x = 1; }
                        catch (System.Exception e) { System.Console.WriteLine(e.Message); }
                    }
                }
            }");

        var svc = GetComponent(components, "Service");
        svc.CodeIssues.Should().NotContain(i => i.Rule == "EMPTY_CATCH_BLOCK");
    }

    #endregion

    #region TS-003: CATCH_GENERIC_EXCEPTION — positive

    [Fact]
    public void TS003_CatchGenericException_DetectedAsInfo()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    public void DoWork() {
                        try { var x = 1; }
                        catch (System.Exception e) { throw; }
                    }
                }
            }");

        var svc = GetComponent(components, "Service");
        svc.CodeIssues.Should().Contain(i => i.Rule == "CATCH_GENERIC_EXCEPTION");
        svc.CodeIssues.First(i => i.Rule == "CATCH_GENERIC_EXCEPTION").Severity.Should().Be("INFO");
    }

    #endregion

    #region TS-004: CATCH_GENERIC_EXCEPTION — negative (specific exception)

    [Fact]
    public void TS004_CatchSpecificException_NotFlagged()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    public void DoWork() {
                        try { var x = 1; }
                        catch (System.ArgumentNullException e) { throw; }
                    }
                }
            }");

        var svc = GetComponent(components, "Service");
        svc.CodeIssues.Should().NotContain(i => i.Rule == "CATCH_GENERIC_EXCEPTION");
    }

    #endregion

    #region TS-005: CONSOLE_LOGGING — Console.WriteLine

    [Fact]
    public void TS005_ConsoleWriteLine_DetectedAsInfo()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    public void DoWork() {
                        System.Console.WriteLine(""debug info"");
                    }
                }
            }");

        var svc = GetComponent(components, "Service");
        svc.CodeIssues.Should().Contain(i => i.Rule == "CONSOLE_LOGGING");
        svc.CodeIssues.First(i => i.Rule == "CONSOLE_LOGGING").Severity.Should().Be("INFO");
    }

    #endregion

    #region TS-006: CONSOLE_LOGGING — Console.Write

    [Fact]
    public void TS006_ConsoleWrite_DetectedAsInfo()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    public void DoWork() {
                        System.Console.Write(""x"");
                    }
                }
            }");

        var svc = GetComponent(components, "Service");
        svc.CodeIssues.Should().Contain(i => i.Rule == "CONSOLE_LOGGING");
    }

    #endregion

    #region TS-007: CONSOLE_LOGGING — negative (no Console usage)

    [Fact]
    public void TS007_NoConsoleUsage_NotFlagged()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    private readonly object _logger;
                    public Service(object logger) { _logger = logger; }
                    public void DoWork() { }
                }
            }");

        var svc = GetComponent(components, "Service");
        svc.CodeIssues.Should().NotContain(i => i.Rule == "CONSOLE_LOGGING");
    }

    #endregion

    #region TS-008: STRING_EQUALITY_OPERATOR — ReferenceEquals on strings

    [Fact]
    public void TS008_ReferenceEqualsOnStrings_DetectedAsWarning()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    public bool Compare(string a, string b) {
                        return object.ReferenceEquals(a, b);
                    }
                }
            }");

        var svc = GetComponent(components, "Service");
        svc.CodeIssues.Should().Contain(i => i.Rule == "STRING_EQUALITY_OPERATOR");
        svc.CodeIssues.First(i => i.Rule == "STRING_EQUALITY_OPERATOR").Severity.Should().Be("WARNING");
    }

    #endregion

    #region TS-009: STRING_EQUALITY_OPERATOR — negative (normal == on strings)

    [Fact]
    public void TS009_NormalStringEquality_NotFlagged()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    public bool Compare(string a, string b) {
                        return a == b;
                    }
                }
            }");

        var svc = GetComponent(components, "Service");
        svc.CodeIssues.Should().NotContain(i => i.Rule == "STRING_EQUALITY_OPERATOR");
    }

    #endregion

    #region TS-010: HARDCODED_SECRET_IN_CODE — password

    [Fact]
    public void TS010_HardcodedPassword_DetectedAsCritical()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    public void Connect() {
                        var password = ""super_secret_123"";
                    }
                }
            }");

        var svc = GetComponent(components, "Service");
        svc.CodeIssues.Should().Contain(i => i.Rule == "HARDCODED_SECRET_IN_CODE");
        svc.CodeIssues.First(i => i.Rule == "HARDCODED_SECRET_IN_CODE").Severity.Should().Be("CRITICAL");
    }

    #endregion

    #region TS-011: HARDCODED_SECRET_IN_CODE — apiKey

    [Fact]
    public void TS011_HardcodedApiKey_DetectedAsCritical()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    public void Init() {
                        var apiKey = ""sk-abc123def456"";
                    }
                }
            }");

        var svc = GetComponent(components, "Service");
        svc.CodeIssues.Should().Contain(i => i.Rule == "HARDCODED_SECRET_IN_CODE");
    }

    #endregion

    #region TS-012: HARDCODED_SECRET_IN_CODE — negative (regular variable)

    [Fact]
    public void TS012_RegularStringVariable_NotFlagged()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    public void DoWork() {
                        var name = ""John Doe"";
                        var city = ""Bogota"";
                    }
                }
            }");

        var svc = GetComponent(components, "Service");
        svc.CodeIssues.Should().NotContain(i => i.Rule == "HARDCODED_SECRET_IN_CODE");
    }

    #endregion

    #region TS-013: DEPRECATED_THREAD_USAGE — Thread.Abort

    [Fact]
    public void TS013_ThreadAbort_DetectedAsError()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    public void Stop(System.Threading.Thread t) {
                        t.Abort();
                    }
                }
            }");

        var svc = GetComponent(components, "Service");
        svc.CodeIssues.Should().Contain(i => i.Rule == "DEPRECATED_THREAD_USAGE");
        svc.CodeIssues.First(i => i.Rule == "DEPRECATED_THREAD_USAGE").Severity.Should().Be("ERROR");
    }

    #endregion

    #region TS-014: DEPRECATED_THREAD_USAGE — negative (Thread.Start)

    [Fact]
    public void TS014_ThreadStart_NotFlagged()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    public void Run(System.Threading.Thread t) {
                        t.Start();
                    }
                }
            }");

        var svc = GetComponent(components, "Service");
        svc.CodeIssues.Should().NotContain(i => i.Rule == "DEPRECATED_THREAD_USAGE");
    }

    #endregion

    #region TS-015: MISSING_SWITCH_DEFAULT — no default

    [Fact]
    public void TS015_SwitchWithoutDefault_DetectedAsInfo()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    public string Describe(int x) {
                        switch (x) {
                            case 1: return ""one"";
                            case 2: return ""two"";
                        }
                        return ""unknown"";
                    }
                }
            }");

        var svc = GetComponent(components, "Service");
        svc.CodeIssues.Should().Contain(i => i.Rule == "MISSING_SWITCH_DEFAULT");
        svc.CodeIssues.First(i => i.Rule == "MISSING_SWITCH_DEFAULT").Severity.Should().Be("INFO");
    }

    #endregion

    #region TS-016: MISSING_SWITCH_DEFAULT — negative (has default)

    [Fact]
    public void TS016_SwitchWithDefault_NotFlagged()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    public string Describe(int x) {
                        switch (x) {
                            case 1: return ""one"";
                            default: return ""other"";
                        }
                    }
                }
            }");

        var svc = GetComponent(components, "Service");
        svc.CodeIssues.Should().NotContain(i => i.Rule == "MISSING_SWITCH_DEFAULT");
    }

    #endregion

    #region TS-017: Clean code — no issues

    [Fact]
    public void TS017_CleanCode_ZeroIssues()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class CleanService {
                    private readonly string _name;
                    public CleanService(string name) { _name = name; }
                    public string GetName() => _name;
                }
            }");

        var svc = GetComponent(components, "CleanService");
        svc.CodeIssues.Should().BeEmpty();
    }

    #endregion

    #region TS-018: All fields populated

    [Fact]
    public void TS018_DetectedIssue_HasAllRequiredFields()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    public void DoWork() {
                        try { var x = 1; }
                        catch (System.Exception e) { }
                    }
                }
            }");

        var svc = GetComponent(components, "Service");
        svc.CodeIssues.Should().NotBeEmpty();
        foreach (var issue in svc.CodeIssues)
        {
            issue.Rule.Should().NotBeNullOrEmpty();
            issue.Severity.Should().NotBeNullOrEmpty();
            issue.Message.Should().NotBeNullOrEmpty();
            issue.Location.Should().MatchRegex(@":\d+$");
        }
    }

    #endregion

    #region TS-019: Severity enum validation

    [Fact]
    public void TS019_AllSeverities_InAllowedEnum()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    public void DoWork() {
                        try { } catch (System.Exception e) { }
                        System.Console.WriteLine(""debug"");
                        var password = ""secret"";
                    }
                }
            }");

        var svc = GetComponent(components, "Service");
        var allowedSeverities = new[] { "INFO", "WARNING", "ERROR", "CRITICAL" };
        foreach (var issue in svc.CodeIssues)
        {
            allowedSeverities.Should().Contain(issue.Severity,
                $"issue '{issue.Rule}' has unexpected severity '{issue.Severity}'");
        }
    }

    #endregion

    #region TS-022: Deterministic ordering

    [Fact]
    public void TS022_MultipleViolations_OrderedByLocation()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    public void DoWork() {
                        System.Console.WriteLine(""a"");
                        try { } catch (System.Exception e) { }
                        System.Console.Write(""b"");
                    }
                }
            }");

        var svc = GetComponent(components, "Service");
        svc.CodeIssues.Should().HaveCountGreaterThan(1);

        // Verify sorted by location
        var locations = svc.CodeIssues.Select(i => i.Location).ToList();
        locations.Should().BeInAscendingOrder();
    }

    #endregion

    #region TS-024: Component with no files — empty code_issues

    [Fact]
    public void TS024_ComponentWithNoFiles_EmptyCodeIssues()
    {
        var component = new Component { Id = "NoFiles.Component" };
        var compilation = CreateCompilation("namespace Empty { public class Stub { } }");
        var analyzer = new RoslynCodeAnalyzer();

        analyzer.Analyze(compilation, new List<Component> { component });

        component.CodeIssues.Should().NotBeNull();
        component.CodeIssues.Should().BeEmpty();
    }

    #endregion

    #region TS-026: Empty source file

    [Fact]
    public void TS026_EmptySourceFile_NoIssuesNoCrash()
    {
        var (_, components) = AnalyzeSource("");

        // Should not crash; may or may not produce a component
        // If no types found, components will be empty — that's fine
        foreach (var comp in components)
        {
            comp.CodeIssues.Should().NotBeNull();
        }
    }

    #endregion

    #region TS-027: Source file with only comments

    [Fact]
    public void TS027_CommentsOnlyFile_NoIssuesNoCrash()
    {
        var (_, components) = AnalyzeSource(@"
            // This file intentionally left blank
            /* No code here */
        ");

        foreach (var comp in components)
        {
            comp.CodeIssues.Should().NotBeNull();
        }
    }

    #endregion

    #region TS-028: NULL_FORGIVING_OPERATOR — positive (= null!)

    [Fact]
    public void TS028_NullForgivingOperator_DetectedAsWarning()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class ProductModel {
                    public string Name { get; set; } = null!;
                    public string Description { get; set; } = null!;
                    public decimal Price { get; set; }
                }
            }");

        var model = GetComponent(components, "ProductModel");
        model.CodeIssues.Where(i => i.Rule == "NULL_FORGIVING_OPERATOR").Should().HaveCount(2);
        model.CodeIssues.First(i => i.Rule == "NULL_FORGIVING_OPERATOR").Severity.Should().Be("WARNING");
        model.CodeIssues.First(i => i.Rule == "NULL_FORGIVING_OPERATOR").Message.Should().Contain("null-forgiving");
    }

    #endregion

    #region TS-029: NULL_FORGIVING_OPERATOR — negative (nullable property)

    [Fact]
    public void TS029_NullableProperty_NotFlagged()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Model {
                    public string? Name { get; set; }
                }
            }");

        var model = GetComponent(components, "Model");
        model.CodeIssues.Should().NotContain(i => i.Rule == "NULL_FORGIVING_OPERATOR");
    }

    #endregion

    #region TS-030: NULL_FORGIVING_OPERATOR — negative (default value)

    [Fact]
    public void TS030_PropertyWithDefaultValue_NotFlagged()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Model {
                    public string Name { get; set; } = string.Empty;
                }
            }");

        var model = GetComponent(components, "Model");
        model.CodeIssues.Should().NotContain(i => i.Rule == "NULL_FORGIVING_OPERATOR");
    }

    #endregion

    #region TS-031: NULL_FORGIVING_OPERATOR — edge (= default!)

    [Fact]
    public void TS031_DefaultForgivingOperator_NotFlagged()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Model {
                    public string Name { get; set; } = default!;
                }
            }");

        var model = GetComponent(components, "Model");
        model.CodeIssues.Should().NotContain(i => i.Rule == "NULL_FORGIVING_OPERATOR");
    }

    #endregion

    #region TS-032: UNUSED_ASSIGNED_VARIABLE — positive

    [Fact]
    public void TS032_UnusedAssignedVariable_DetectedAsWarning()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    public void Process() {
                        var topic = ""orders"";
                        var orderId = ""123"";
                        System.Console.WriteLine(orderId);
                    }
                }
            }");

        var svc = GetComponent(components, "Service");
        svc.CodeIssues.Should().Contain(i => i.Rule == "UNUSED_ASSIGNED_VARIABLE" && i.Message.Contains("topic"));
        svc.CodeIssues.First(i => i.Rule == "UNUSED_ASSIGNED_VARIABLE").Severity.Should().Be("WARNING");
    }

    #endregion

    #region TS-033: UNUSED_ASSIGNED_VARIABLE — negative (variable used)

    [Fact]
    public void TS033_UsedVariable_NotFlagged()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    public void Process() {
                        var x = 42;
                        System.Console.WriteLine(x);
                    }
                }
            }");

        var svc = GetComponent(components, "Service");
        svc.CodeIssues.Should().NotContain(i => i.Rule == "UNUSED_ASSIGNED_VARIABLE");
    }

    #endregion

    #region TS-034: UNUSED_ASSIGNED_VARIABLE — negative (no initializer)

    [Fact]
    public void TS034_VariableWithoutInitializer_NotFlagged()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    public void Process() {
                        int x;
                        x = 42;
                        System.Console.WriteLine(x);
                    }
                }
            }");

        var svc = GetComponent(components, "Service");
        svc.CodeIssues.Should().NotContain(i => i.Rule == "UNUSED_ASSIGNED_VARIABLE");
    }

    #endregion

    #region TS-035: UNUSED_ASSIGNED_VARIABLE — edge (discard _)

    [Fact]
    public void TS035_DiscardVariable_NotFlagged()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    public int GetValue() => 42;
                    public void Process() {
                        var _ = GetValue();
                    }
                }
            }");

        var svc = GetComponent(components, "Service");
        svc.CodeIssues.Should().NotContain(i => i.Rule == "UNUSED_ASSIGNED_VARIABLE");
    }

    #endregion

    #region TS-036: UNUSED_ASSIGNED_VARIABLE — edge (multiple vars, one unused)

    [Fact]
    public void TS036_MultipleVars_OnlyUnusedFlagged()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    public void Process() {
                        var a = 1;
                        var b = 2;
                        System.Console.WriteLine(a);
                    }
                }
            }");

        var svc = GetComponent(components, "Service");
        var unusedIssues = svc.CodeIssues.Where(i => i.Rule == "UNUSED_ASSIGNED_VARIABLE").ToList();
        unusedIssues.Should().ContainSingle();
        unusedIssues[0].Message.Should().Contain("b");
    }

    #endregion

    #region TS-037: ASYNC_VOID_METHOD — positive

    [Fact]
    public void TS037_AsyncVoidMethod_DetectedAsError()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    public async void FireAndForget() {
                        await System.Threading.Tasks.Task.Delay(1);
                    }
                }
            }");

        var svc = GetComponent(components, "Service");
        svc.CodeIssues.Should().Contain(i => i.Rule == "ASYNC_VOID_METHOD");
        svc.CodeIssues.First(i => i.Rule == "ASYNC_VOID_METHOD").Severity.Should().Be("ERROR");
        svc.CodeIssues.First(i => i.Rule == "ASYNC_VOID_METHOD").Message.Should().Contain("async Task");
    }

    #endregion

    #region TS-038: ASYNC_VOID_METHOD — negative (async Task)

    [Fact]
    public void TS038_AsyncTaskMethod_NotFlagged()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    public async System.Threading.Tasks.Task DoWorkAsync() {
                        await System.Threading.Tasks.Task.Delay(1);
                    }
                }
            }");

        var svc = GetComponent(components, "Service");
        svc.CodeIssues.Should().NotContain(i => i.Rule == "ASYNC_VOID_METHOD");
    }

    #endregion

    #region TS-039: ASYNC_VOID_METHOD — negative (regular void)

    [Fact]
    public void TS039_RegularVoidMethod_NotFlagged()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    public void DoWork() { }
                }
            }");

        var svc = GetComponent(components, "Service");
        svc.CodeIssues.Should().NotContain(i => i.Rule == "ASYNC_VOID_METHOD");
    }

    #endregion

    #region TS-040: ASYNC_VOID_METHOD — negative (async Task<T>)

    [Fact]
    public void TS040_AsyncTaskOfTMethod_NotFlagged()
    {
        var (_, components) = AnalyzeSource(@"
            namespace Sample {
                public class Service {
                    public async System.Threading.Tasks.Task<int> GetValueAsync() {
                        await System.Threading.Tasks.Task.Delay(1);
                        return 42;
                    }
                }
            }");

        var svc = GetComponent(components, "Service");
        svc.CodeIssues.Should().NotContain(i => i.Rule == "ASYNC_VOID_METHOD");
    }

    #endregion
}
