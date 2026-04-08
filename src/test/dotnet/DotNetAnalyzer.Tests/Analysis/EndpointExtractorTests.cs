using FluentAssertions;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using DotNetAnalyzer.Analysis;
using DotNetAnalyzer.Model;
using Xunit;

namespace DotNetAnalyzer.Tests.Analysis;

public class EndpointExtractorTests
{
    private static CSharpCompilation CreateCompilation(params string[] sources)
    {
        var syntaxTrees = sources.Select(s => CSharpSyntaxTree.ParseText(s)).ToArray();

        var references = new[]
        {
            MetadataReference.CreateFromFile(typeof(object).Assembly.Location),
            MetadataReference.CreateFromFile(typeof(Enumerable).Assembly.Location),
            MetadataReference.CreateFromFile(typeof(Task).Assembly.Location),
            MetadataReference.CreateFromFile(typeof(Attribute).Assembly.Location),
        };

        var runtimeDir = Path.GetDirectoryName(typeof(object).Assembly.Location)!;
        var runtimeRef = MetadataReference.CreateFromFile(Path.Combine(runtimeDir, "System.Runtime.dll"));

        return CSharpCompilation.Create(
            "TestAssembly",
            syntaxTrees,
            references.Append(runtimeRef),
            new CSharpCompilationOptions(OutputKind.DynamicallyLinkedLibrary));
    }

    private static readonly string ControllerWithEndpointsSource = @"
using System;
using System.Collections.Generic;
using System.Threading.Tasks;

namespace Microsoft.AspNetCore.Mvc
{
    [AttributeUsage(AttributeTargets.Class)]
    public class ApiControllerAttribute : Attribute { }

    [AttributeUsage(AttributeTargets.Class | AttributeTargets.Method)]
    public class RouteAttribute : Attribute
    {
        public string Template { get; }
        public RouteAttribute(string template) { Template = template; }
    }

    [AttributeUsage(AttributeTargets.Method)]
    public class HttpGetAttribute : Attribute
    {
        public string? Template { get; }
        public HttpGetAttribute() { }
        public HttpGetAttribute(string template) { Template = template; }
    }

    [AttributeUsage(AttributeTargets.Method)]
    public class HttpPostAttribute : Attribute
    {
        public string? Template { get; }
        public HttpPostAttribute() { }
        public HttpPostAttribute(string template) { Template = template; }
    }

    public class ControllerBase { }
    public class ActionResult<T> { }
    public class FromBodyAttribute : Attribute { }
}

namespace SampleProject.Models
{
    public class Order
    {
        public int Id { get; set; }
        public string CustomerName { get; set; } = string.Empty;
    }
}

namespace SampleProject.Controllers
{
    using Microsoft.AspNetCore.Mvc;
    using SampleProject.Models;

    [ApiController]
    [Route(""api/[controller]"")]
    public class OrdersController : ControllerBase
    {
        [HttpGet]
        public Task<ActionResult<IEnumerable<Order>>> GetAll()
        {
            throw new NotImplementedException();
        }

        [HttpGet(""{id}"")]
        public Task<ActionResult<Order>> GetById(int id)
        {
            throw new NotImplementedException();
        }

        [HttpPost]
        public Task<ActionResult<Order>> Create([FromBody] Order order)
        {
            throw new NotImplementedException();
        }
    }
}";

    private static List<Component> BuildComponents()
    {
        return new List<Component>
        {
            new Component { Id = "SampleProject.Controllers.OrdersController" },
        };
    }

    [Fact]
    public void ExtractEndpoints_WithApiController_FindsEndpoints()
    {
        // Arrange
        var compilation = CreateCompilation(ControllerWithEndpointsSource);
        var components = BuildComponents();
        var extractor = new EndpointExtractor();

        // Act
        var endpoints = extractor.ExtractEndpoints(compilation, components);

        // Assert
        endpoints.Should().NotBeEmpty();
        endpoints.Should().HaveCountGreaterThanOrEqualTo(3);
        endpoints.Should().OnlyContain(e => e.ComponentId == "SampleProject.Controllers.OrdersController");
    }

    [Fact]
    public void ExtractEndpoints_HttpGetAttribute_ExtractsMethodAndPath()
    {
        // Arrange
        var compilation = CreateCompilation(ControllerWithEndpointsSource);
        var components = BuildComponents();
        var extractor = new EndpointExtractor();

        // Act
        var endpoints = extractor.ExtractEndpoints(compilation, components);

        // Assert
        var getEndpoint = endpoints.FirstOrDefault(e => e.Method == "GET" && !e.Path.Contains("{id}"));
        getEndpoint.Should().NotBeNull();
        getEndpoint!.Path.Should().Contain("api/orders");
    }

    [Fact]
    public void ExtractEndpoints_HttpPostAttribute_ExtractsMethodAndPath()
    {
        // Arrange
        var compilation = CreateCompilation(ControllerWithEndpointsSource);
        var components = BuildComponents();
        var extractor = new EndpointExtractor();

        // Act
        var endpoints = extractor.ExtractEndpoints(compilation, components);

        // Assert
        var postEndpoint = endpoints.FirstOrDefault(e => e.Method == "POST");
        postEndpoint.Should().NotBeNull();
        postEndpoint!.Path.Should().Contain("api/orders");
    }
}
