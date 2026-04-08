using FluentAssertions;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using DotNetAnalyzer.Analysis;
using DotNetAnalyzer.Model;
using Xunit;

namespace DotNetAnalyzer.Tests.Analysis;

public class TypeAnalyzerTests
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

        // Add System.Runtime reference for net8.0
        var runtimeDir = Path.GetDirectoryName(typeof(object).Assembly.Location)!;
        var runtimeRef = MetadataReference.CreateFromFile(Path.Combine(runtimeDir, "System.Runtime.dll"));

        return CSharpCompilation.Create(
            "TestAssembly",
            syntaxTrees,
            references.Append(runtimeRef),
            new CSharpCompilationOptions(OutputKind.DynamicallyLinkedLibrary));
    }

    private static readonly string ControllerSource = @"
using System.Collections.Generic;
using System.Threading.Tasks;

namespace SampleProject.Controllers
{
    [ApiController]
    [Route(""api/[controller]"")]
    public class OrdersController
    {
        private readonly IOrderService _orderService;

        public OrdersController(IOrderService orderService)
        {
            _orderService = orderService;
        }

        [HttpGet]
        public Task<IEnumerable<Order>> GetAll()
        {
            return _orderService.GetAllOrdersAsync();
        }

        [HttpPost]
        public Task Create(Order order)
        {
            return _orderService.CreateOrderAsync(order);
        }
    }

    public class ApiControllerAttribute : System.Attribute { }
    public class RouteAttribute : System.Attribute
    {
        public RouteAttribute(string template) { }
    }
    public class HttpGetAttribute : System.Attribute
    {
        public HttpGetAttribute() { }
        public HttpGetAttribute(string template) { }
    }
    public class HttpPostAttribute : System.Attribute
    {
        public HttpPostAttribute() { }
        public HttpPostAttribute(string template) { }
    }
    public interface IOrderService
    {
        Task<IEnumerable<Order>> GetAllOrdersAsync();
        Task CreateOrderAsync(Order order);
    }
    public class Order
    {
        public int Id { get; set; }
    }
}";

    private static readonly string ServiceSource = @"
using System.Collections.Generic;
using System.Threading.Tasks;

namespace SampleProject.Services
{
    public class OrderService : IOrderService
    {
        private readonly IOrderRepository _orderRepository;

        public OrderService(IOrderRepository orderRepository)
        {
            _orderRepository = orderRepository;
        }

        public async Task<IEnumerable<Order>> GetAllOrdersAsync()
        {
            return await _orderRepository.GetAllAsync();
        }

        public async Task CreateOrderAsync(Order order)
        {
            await _orderRepository.AddAsync(order);
        }
    }

    public interface IOrderService
    {
        Task<IEnumerable<Order>> GetAllOrdersAsync();
        Task CreateOrderAsync(Order order);
    }

    public interface IOrderRepository
    {
        Task<IEnumerable<Order>> GetAllAsync();
        Task AddAsync(Order order);
    }

    public class Order
    {
        public int Id { get; set; }
    }
}";

    private static readonly string RepositorySource = @"
using System.Collections.Generic;
using System.Threading.Tasks;

namespace SampleProject.Repositories
{
    public class OrderRepository : IOrderRepository
    {
        public Task<Order?> GetByIdAsync(int id) => Task.FromResult<Order?>(null);
        public Task<IEnumerable<Order>> GetAllAsync() => Task.FromResult<IEnumerable<Order>>(new List<Order>());
        public Task AddAsync(Order order) => Task.CompletedTask;
    }

    public interface IOrderRepository
    {
        Task<Order?> GetByIdAsync(int id);
        Task<IEnumerable<Order>> GetAllAsync();
        Task AddAsync(Order order);
    }

    public class Order
    {
        public int Id { get; set; }
    }
}";

    [Fact]
    public void AnalyzeTypes_WithSampleProject_ExtractsControllerComponent()
    {
        // Arrange
        var compilation = CreateCompilation(ControllerSource);
        var analyzer = new TypeAnalyzer();

        // Act
        var components = analyzer.AnalyzeTypes(compilation);

        // Assert
        components.Should().Contain(c => c.Id.Contains("OrdersController"));
    }

    [Fact]
    public void AnalyzeTypes_WithSampleProject_ExtractsServiceComponent()
    {
        // Arrange
        var compilation = CreateCompilation(ServiceSource);
        var analyzer = new TypeAnalyzer();

        // Act
        var components = analyzer.AnalyzeTypes(compilation);

        // Assert
        components.Should().Contain(c => c.Id.Contains("OrderService"));
    }

    [Fact]
    public void AnalyzeTypes_WithSampleProject_ExtractsRepositoryComponent()
    {
        // Arrange
        var compilation = CreateCompilation(RepositorySource);
        var analyzer = new TypeAnalyzer();

        // Act
        var components = analyzer.AnalyzeTypes(compilation);

        // Assert
        components.Should().Contain(c => c.Id.Contains("OrderRepository"));
    }

    [Fact]
    public void AnalyzeTypes_WithSampleProject_ExtractsInterfaceCorrectly()
    {
        // Arrange
        var compilation = CreateCompilation(RepositorySource);
        var analyzer = new TypeAnalyzer();

        // Act
        var components = analyzer.AnalyzeTypes(compilation);

        // Assert
        var iface = components.FirstOrDefault(c => c.Id.Contains("IOrderRepository"));
        iface.Should().NotBeNull();
        iface!.IsInterface.Should().BeTrue();
    }

    [Fact]
    public void AnalyzeTypes_ComponentHasQualifiedName()
    {
        // Arrange
        var compilation = CreateCompilation(ControllerSource);
        var analyzer = new TypeAnalyzer();

        // Act
        var components = analyzer.AnalyzeTypes(compilation);

        // Assert
        var controller = components.FirstOrDefault(c => c.Id.Contains("OrdersController"));
        controller.Should().NotBeNull();
        controller!.Id.Should().Be("SampleProject.Controllers.OrdersController");
    }
}
