using FluentAssertions;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using DotNetAnalyzer.Analysis;
using DotNetAnalyzer.Model;
using Xunit;

namespace DotNetAnalyzer.Tests.Analysis;

public class InvocationAnalyzerTests
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

    private static readonly string AllTypesSource = @"
using System.Collections.Generic;
using System.Threading.Tasks;

namespace SampleProject.Models
{
    public class Order
    {
        public int Id { get; set; }
        public string CustomerName { get; set; } = string.Empty;
    }
}

namespace SampleProject.Repositories
{
    using SampleProject.Models;

    public interface IOrderRepository
    {
        Task<Order?> GetByIdAsync(int id);
        Task<IEnumerable<Order>> GetAllAsync();
        Task AddAsync(Order order);
    }

    public class OrderRepository : IOrderRepository
    {
        public Task<Order?> GetByIdAsync(int id) => Task.FromResult<Order?>(null);
        public Task<IEnumerable<Order>> GetAllAsync() => Task.FromResult<IEnumerable<Order>>(new List<Order>());
        public Task AddAsync(Order order) => Task.CompletedTask;
    }
}

namespace SampleProject.Services
{
    using SampleProject.Models;
    using SampleProject.Repositories;

    public interface IOrderService
    {
        Task<Order?> GetOrderAsync(int id);
        Task<IEnumerable<Order>> GetAllOrdersAsync();
        Task CreateOrderAsync(Order order);
    }

    public class OrderService : IOrderService
    {
        private readonly IOrderRepository _orderRepository;

        public OrderService(IOrderRepository orderRepository)
        {
            _orderRepository = orderRepository;
        }

        public async Task<Order?> GetOrderAsync(int id)
        {
            return await _orderRepository.GetByIdAsync(id);
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
}

namespace SampleProject.Controllers
{
    using SampleProject.Models;
    using SampleProject.Services;

    public class OrdersController
    {
        private readonly IOrderService _orderService;

        public OrdersController(IOrderService orderService)
        {
            _orderService = orderService;
        }

        public async Task<IEnumerable<Order>> GetAll()
        {
            return await _orderService.GetAllOrdersAsync();
        }

        public async Task Create(Order order)
        {
            await _orderService.CreateOrderAsync(order);
        }
    }
}";

    private static List<Component> BuildComponents()
    {
        return new List<Component>
        {
            new Component { Id = "SampleProject.Controllers.OrdersController" },
            new Component { Id = "SampleProject.Services.OrderService" },
            new Component { Id = "SampleProject.Services.IOrderService", IsInterface = true },
            new Component { Id = "SampleProject.Repositories.OrderRepository" },
            new Component { Id = "SampleProject.Repositories.IOrderRepository", IsInterface = true },
            new Component { Id = "SampleProject.Models.Order" },
        };
    }

    [Fact]
    public void AnalyzeInvocations_ServiceCallsRepository_CreatesEdge()
    {
        // Arrange
        var compilation = CreateCompilation(AllTypesSource);
        var components = BuildComponents();
        var analyzer = new InvocationAnalyzer();

        // Act
        var edges = analyzer.AnalyzeInvocations(compilation, components);

        // Assert
        edges.Should().Contain(e =>
            e.From == "SampleProject.Services.OrderService" &&
            e.To == "SampleProject.Repositories.IOrderRepository");
    }

    [Fact]
    public void AnalyzeInvocations_ControllerCallsService_CreatesEdge()
    {
        // Arrange
        var compilation = CreateCompilation(AllTypesSource);
        var components = BuildComponents();
        var analyzer = new InvocationAnalyzer();

        // Act
        var edges = analyzer.AnalyzeInvocations(compilation, components);

        // Assert
        edges.Should().Contain(e =>
            e.From == "SampleProject.Controllers.OrdersController" &&
            e.To == "SampleProject.Services.IOrderService");
    }
}
