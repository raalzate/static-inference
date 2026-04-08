using FluentAssertions;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using DotNetAnalyzer.Analysis;
using DotNetAnalyzer.Model;
using Xunit;

namespace DotNetAnalyzer.Tests.Analysis;

public class SecretsDetectorTests
{
    private static CSharpCompilation CreateCompilation(params string[] sources)
    {
        var syntaxTrees = sources.Select(s => CSharpSyntaxTree.ParseText(s)).ToArray();

        var references = new[]
        {
            MetadataReference.CreateFromFile(typeof(object).Assembly.Location),
            MetadataReference.CreateFromFile(typeof(Enumerable).Assembly.Location),
            MetadataReference.CreateFromFile(typeof(Task).Assembly.Location),
        };

        var runtimeDir = Path.GetDirectoryName(typeof(object).Assembly.Location)!;
        var runtimeRef = MetadataReference.CreateFromFile(Path.Combine(runtimeDir, "System.Runtime.dll"));

        return CSharpCompilation.Create(
            "TestAssembly",
            syntaxTrees,
            references.Append(runtimeRef),
            new CSharpCompilationOptions(OutputKind.DynamicallyLinkedLibrary));
    }

    #region IConfiguration Injection Detection

    private static readonly string ConfigurationInjectionSource = @"
namespace Microsoft.Extensions.Configuration
{
    public interface IConfiguration
    {
        string this[string key] { get; set; }
        IConfigurationSection GetSection(string key);
    }
    public interface IConfigurationSection : IConfiguration
    {
        string Value { get; }
    }
}

namespace SampleProject.Services
{
    public class EmailService
    {
        private readonly Microsoft.Extensions.Configuration.IConfiguration _configuration;

        public EmailService(Microsoft.Extensions.Configuration.IConfiguration configuration)
        {
            _configuration = configuration;
        }

        public string GetSmtpHost()
        {
            return _configuration[""Smtp:Host""];
        }
    }
}";

    [Fact]
    public void Detect_IConfigurationInjection_AddsToSecretsReferences()
    {
        // Arrange
        var compilation = CreateCompilation(ConfigurationInjectionSource);
        var detector = new SecretsDetector();
        var components = new TypeAnalyzer().AnalyzeTypes(compilation);

        // Act
        detector.Detect(compilation, components);

        // Assert
        var service = components.FirstOrDefault(c => c.Id.Contains("EmailService"));
        service.Should().NotBeNull();
        service!.SecretsReferences.Should().Contain("IConfiguration");
    }

    #endregion

    #region Configuration Indexer Key Detection

    [Fact]
    public void Detect_ConfigurationIndexerCall_CapturesKeyName()
    {
        // Arrange
        var compilation = CreateCompilation(ConfigurationInjectionSource);
        var detector = new SecretsDetector();
        var components = new TypeAnalyzer().AnalyzeTypes(compilation);

        // Act
        detector.Detect(compilation, components);

        // Assert
        var service = components.FirstOrDefault(c => c.Id.Contains("EmailService"));
        service.Should().NotBeNull();
        service!.SecretsReferences.Should().Contain("config:Smtp:Host");
    }

    private static readonly string MultipleKeysSource = @"
namespace Microsoft.Extensions.Configuration
{
    public interface IConfiguration
    {
        string this[string key] { get; set; }
    }
}

namespace SampleProject.Services
{
    public class DatabaseService
    {
        private readonly Microsoft.Extensions.Configuration.IConfiguration _configuration;

        public DatabaseService(Microsoft.Extensions.Configuration.IConfiguration configuration)
        {
            _configuration = configuration;
        }

        public string GetConnectionString()
        {
            var host = _configuration[""Database:Host""];
            var port = _configuration[""Database:Port""];
            return $""{host}:{port}"";
        }
    }
}";

    [Fact]
    public void Detect_MultipleConfigurationKeys_CapturesAllKeys()
    {
        // Arrange
        var compilation = CreateCompilation(MultipleKeysSource);
        var detector = new SecretsDetector();
        var components = new TypeAnalyzer().AnalyzeTypes(compilation);

        // Act
        detector.Detect(compilation, components);

        // Assert
        var service = components.FirstOrDefault(c => c.Id.Contains("DatabaseService"));
        service.Should().NotBeNull();
        service!.SecretsReferences.Should().Contain("config:Database:Host");
        service.SecretsReferences.Should().Contain("config:Database:Port");
    }

    #endregion

    #region Appsettings Reference Detection

    private static readonly string AppsettingsReferenceSource = @"
namespace SampleProject.Startup
{
    public class ConfigLoader
    {
        public void LoadConfig()
        {
            var path = ""appsettings.json"";
            var envPath = ""appsettings.Development.json"";
        }
    }
}";

    [Fact]
    public void Detect_AppsettingsJsonReference_AddsToSecretsReferences()
    {
        // Arrange
        var compilation = CreateCompilation(AppsettingsReferenceSource);
        var detector = new SecretsDetector();
        var components = new TypeAnalyzer().AnalyzeTypes(compilation);

        // Act
        detector.Detect(compilation, components);

        // Assert
        var loader = components.FirstOrDefault(c => c.Id.Contains("ConfigLoader"));
        loader.Should().NotBeNull();
        loader!.SecretsReferences.Should().Contain("appsettings.json");
    }

    [Fact]
    public void Detect_AppsettingsDevelopmentJsonReference_AddsToSecretsReferences()
    {
        // Arrange
        var compilation = CreateCompilation(AppsettingsReferenceSource);
        var detector = new SecretsDetector();
        var components = new TypeAnalyzer().AnalyzeTypes(compilation);

        // Act
        detector.Detect(compilation, components);

        // Assert
        var loader = components.FirstOrDefault(c => c.Id.Contains("ConfigLoader"));
        loader.Should().NotBeNull();
        loader!.SecretsReferences.Should().Contain("appsettings.Development.json");
    }

    #endregion

    #region No Configuration Pattern

    private static readonly string NoConfigSource = @"
namespace SampleProject.Services
{
    public class PlainService
    {
        public string DoWork() => ""done"";
    }
}";

    [Fact]
    public void Detect_NoConfigPattern_LeavesSecretsReferencesEmpty()
    {
        // Arrange
        var compilation = CreateCompilation(NoConfigSource);
        var detector = new SecretsDetector();
        var components = new TypeAnalyzer().AnalyzeTypes(compilation);

        // Act
        detector.Detect(compilation, components);

        // Assert
        var service = components.FirstOrDefault(c => c.Id.Contains("PlainService"));
        service.Should().NotBeNull();
        service!.SecretsReferences.Should().BeEmpty();
    }

    #endregion
}
