using FluentAssertions;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using DotNetAnalyzer.Analysis;
using DotNetAnalyzer.Model;
using Xunit;

namespace DotNetAnalyzer.Tests.Analysis;

public class DependencyInjectionAnalyzerTests
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

    #region Test Source Code

    private static readonly string ConstructorInjectionSource = @"
using System.Collections.Generic;
using System.Threading.Tasks;

namespace SampleProject.Services
{
    public interface IOrderRepository
    {
        Task<IEnumerable<Order>> GetAllAsync();
        Task AddAsync(Order order);
    }

    public interface ILogger
    {
        void Log(string message);
    }

    public class Order
    {
        public int Id { get; set; }
    }

    public class OrderService
    {
        private readonly IOrderRepository _orderRepository;
        private readonly ILogger _logger;

        public OrderService(IOrderRepository orderRepository, ILogger logger)
        {
            _orderRepository = orderRepository;
            _logger = logger;
        }

        public async Task<IEnumerable<Order>> GetAllOrdersAsync()
        {
            _logger.Log(""Getting all orders"");
            return await _orderRepository.GetAllAsync();
        }
    }
}";

    private static readonly string InterfaceImplementationSource = @"
using System.Collections.Generic;
using System.Threading.Tasks;

namespace SampleProject.Repositories
{
    public interface IOrderRepository
    {
        Task<IEnumerable<Order>> GetAllAsync();
        Task AddAsync(Order order);
    }

    public class Order
    {
        public int Id { get; set; }
    }

    public class OrderRepository : IOrderRepository
    {
        public Task<IEnumerable<Order>> GetAllAsync() =>
            Task.FromResult<IEnumerable<Order>>(new List<Order>());

        public Task AddAsync(Order order) => Task.CompletedTask;
    }
}";

    private static readonly string ServiceCollectionRegistrationSource = @"
using System;
using System.Collections.Generic;

namespace SampleProject.Configuration
{
    // Minimal IServiceCollection stub for analysis
    public interface IServiceCollection { }

    public static class ServiceCollectionExtensions
    {
        public static IServiceCollection AddScoped<TService, TImplementation>(this IServiceCollection services)
            where TImplementation : TService
            => services;

        public static IServiceCollection AddTransient<TService, TImplementation>(this IServiceCollection services)
            where TImplementation : TService
            => services;

        public static IServiceCollection AddSingleton<TService, TImplementation>(this IServiceCollection services)
            where TImplementation : TService
            => services;
    }

    public interface IOrderService { }
    public class OrderService : IOrderService { }

    public interface ICacheService { }
    public class RedisCacheService : ICacheService { }

    public interface INotificationService { }
    public class EmailNotificationService : INotificationService { }

    public class Startup
    {
        public void ConfigureServices(IServiceCollection services)
        {
            services.AddScoped<IOrderService, OrderService>();
            services.AddTransient<ICacheService, RedisCacheService>();
            services.AddSingleton<INotificationService, EmailNotificationService>();
        }
    }
}";

    private static readonly string MultiplePatternSource = @"
using System;
using System.Collections.Generic;
using System.Threading.Tasks;

namespace SampleProject
{
    public interface IServiceCollection { }

    public static class ServiceCollectionExtensions
    {
        public static IServiceCollection AddScoped<TService, TImplementation>(this IServiceCollection services)
            where TImplementation : TService
            => services;
    }

    public interface IUserRepository
    {
        Task<User> GetByIdAsync(int id);
    }

    public class User
    {
        public int Id { get; set; }
        public string Name { get; set; } = string.Empty;
    }

    public class UserRepository : IUserRepository
    {
        public Task<User> GetByIdAsync(int id) => Task.FromResult(new User());
    }

    public class UserService
    {
        private readonly IUserRepository _userRepository;

        public UserService(IUserRepository userRepository)
        {
            _userRepository = userRepository;
        }

        public Task<User> GetUserAsync(int id) => _userRepository.GetByIdAsync(id);
    }

    public class Startup
    {
        public void ConfigureServices(IServiceCollection services)
        {
            services.AddScoped<IUserRepository, UserRepository>();
        }
    }
}";

    #endregion

    #region Constructor Injection Tests [TS-021]

    [Fact]
    public void Analyze_ConstructorWithInterfaceParams_CreatesInjectionEdges()
    {
        // Arrange
        var compilation = CreateCompilation(ConstructorInjectionSource);
        var components = new List<Component>
        {
            new Component { Id = "SampleProject.Services.OrderService" },
            new Component { Id = "SampleProject.Services.IOrderRepository", IsInterface = true },
            new Component { Id = "SampleProject.Services.ILogger", IsInterface = true },
            new Component { Id = "SampleProject.Services.Order" },
        };
        var analyzer = new DependencyInjectionAnalyzer();

        // Act
        var edges = analyzer.Analyze(compilation, components);

        // Assert
        edges.Should().Contain(e =>
            e.From == "SampleProject.Services.OrderService" &&
            e.To == "SampleProject.Services.IOrderRepository" &&
            e.Type == "injection");
    }

    [Fact]
    public void Analyze_ConstructorWithMultipleInterfaceParams_CreatesEdgeForEach()
    {
        // Arrange
        var compilation = CreateCompilation(ConstructorInjectionSource);
        var components = new List<Component>
        {
            new Component { Id = "SampleProject.Services.OrderService" },
            new Component { Id = "SampleProject.Services.IOrderRepository", IsInterface = true },
            new Component { Id = "SampleProject.Services.ILogger", IsInterface = true },
            new Component { Id = "SampleProject.Services.Order" },
        };
        var analyzer = new DependencyInjectionAnalyzer();

        // Act
        var edges = analyzer.Analyze(compilation, components);

        // Assert
        var injectionEdges = edges.Where(e =>
            e.From == "SampleProject.Services.OrderService" &&
            e.Type == "injection").ToList();

        injectionEdges.Should().HaveCount(2);
        injectionEdges.Should().Contain(e => e.To == "SampleProject.Services.IOrderRepository");
        injectionEdges.Should().Contain(e => e.To == "SampleProject.Services.ILogger");
    }

    [Fact]
    public void Analyze_ConstructorWithNonInterfaceParam_DoesNotCreateInjectionEdge()
    {
        // Arrange — Order is a concrete class, not an interface
        var source = @"
namespace SampleProject.Services
{
    public class Order { public int Id { get; set; } }

    public class OrderValidator
    {
        private readonly Order _template;

        public OrderValidator(Order template)
        {
            _template = template;
        }
    }
}";
        var compilation = CreateCompilation(source);
        var components = new List<Component>
        {
            new Component { Id = "SampleProject.Services.OrderValidator" },
            new Component { Id = "SampleProject.Services.Order" },
        };
        var analyzer = new DependencyInjectionAnalyzer();

        // Act
        var edges = analyzer.Analyze(compilation, components);

        // Assert — no injection edge for concrete type constructor params
        edges.Should().NotContain(e => e.Type == "injection");
    }

    #endregion

    #region Interface-Implementation Detection [TS-022]

    [Fact]
    public void Analyze_ClassImplementsIPrefixInterface_CreatesImplementsEdge()
    {
        // Arrange
        var compilation = CreateCompilation(InterfaceImplementationSource);
        var components = new List<Component>
        {
            new Component { Id = "SampleProject.Repositories.OrderRepository" },
            new Component { Id = "SampleProject.Repositories.IOrderRepository", IsInterface = true },
            new Component { Id = "SampleProject.Repositories.Order" },
        };
        var analyzer = new DependencyInjectionAnalyzer();

        // Act
        var edges = analyzer.Analyze(compilation, components);

        // Assert
        edges.Should().Contain(e =>
            e.From == "SampleProject.Repositories.OrderRepository" &&
            e.To == "SampleProject.Repositories.IOrderRepository" &&
            e.Type == "implements");
    }

    [Fact]
    public void Analyze_InterfaceImplementation_EdgeWeightIsOne()
    {
        // Arrange
        var compilation = CreateCompilation(InterfaceImplementationSource);
        var components = new List<Component>
        {
            new Component { Id = "SampleProject.Repositories.OrderRepository" },
            new Component { Id = "SampleProject.Repositories.IOrderRepository", IsInterface = true },
            new Component { Id = "SampleProject.Repositories.Order" },
        };
        var analyzer = new DependencyInjectionAnalyzer();

        // Act
        var edges = analyzer.Analyze(compilation, components);

        // Assert
        var implEdge = edges.FirstOrDefault(e =>
            e.From == "SampleProject.Repositories.OrderRepository" &&
            e.To == "SampleProject.Repositories.IOrderRepository" &&
            e.Type == "implements");

        implEdge.Should().NotBeNull();
        implEdge!.Weight.Should().Be(1);
    }

    #endregion

    #region IServiceCollection Registration Detection [TS-023]

    [Fact]
    public void Analyze_AddScopedRegistration_CreatesRegistrationEdge()
    {
        // Arrange
        var compilation = CreateCompilation(ServiceCollectionRegistrationSource);
        var components = new List<Component>
        {
            new Component { Id = "SampleProject.Configuration.Startup" },
            new Component { Id = "SampleProject.Configuration.IOrderService", IsInterface = true },
            new Component { Id = "SampleProject.Configuration.OrderService" },
            new Component { Id = "SampleProject.Configuration.ICacheService", IsInterface = true },
            new Component { Id = "SampleProject.Configuration.RedisCacheService" },
            new Component { Id = "SampleProject.Configuration.INotificationService", IsInterface = true },
            new Component { Id = "SampleProject.Configuration.EmailNotificationService" },
            new Component { Id = "SampleProject.Configuration.IServiceCollection", IsInterface = true },
        };
        var analyzer = new DependencyInjectionAnalyzer();

        // Act
        var edges = analyzer.Analyze(compilation, components);

        // Assert
        edges.Should().Contain(e =>
            e.From == "SampleProject.Configuration.OrderService" &&
            e.To == "SampleProject.Configuration.IOrderService" &&
            e.Type == "registration");
    }

    [Fact]
    public void Analyze_AddTransientRegistration_CreatesRegistrationEdge()
    {
        // Arrange
        var compilation = CreateCompilation(ServiceCollectionRegistrationSource);
        var components = new List<Component>
        {
            new Component { Id = "SampleProject.Configuration.Startup" },
            new Component { Id = "SampleProject.Configuration.IOrderService", IsInterface = true },
            new Component { Id = "SampleProject.Configuration.OrderService" },
            new Component { Id = "SampleProject.Configuration.ICacheService", IsInterface = true },
            new Component { Id = "SampleProject.Configuration.RedisCacheService" },
            new Component { Id = "SampleProject.Configuration.INotificationService", IsInterface = true },
            new Component { Id = "SampleProject.Configuration.EmailNotificationService" },
            new Component { Id = "SampleProject.Configuration.IServiceCollection", IsInterface = true },
        };
        var analyzer = new DependencyInjectionAnalyzer();

        // Act
        var edges = analyzer.Analyze(compilation, components);

        // Assert
        edges.Should().Contain(e =>
            e.From == "SampleProject.Configuration.RedisCacheService" &&
            e.To == "SampleProject.Configuration.ICacheService" &&
            e.Type == "registration");
    }

    [Fact]
    public void Analyze_AddSingletonRegistration_CreatesRegistrationEdge()
    {
        // Arrange
        var compilation = CreateCompilation(ServiceCollectionRegistrationSource);
        var components = new List<Component>
        {
            new Component { Id = "SampleProject.Configuration.Startup" },
            new Component { Id = "SampleProject.Configuration.IOrderService", IsInterface = true },
            new Component { Id = "SampleProject.Configuration.OrderService" },
            new Component { Id = "SampleProject.Configuration.ICacheService", IsInterface = true },
            new Component { Id = "SampleProject.Configuration.RedisCacheService" },
            new Component { Id = "SampleProject.Configuration.INotificationService", IsInterface = true },
            new Component { Id = "SampleProject.Configuration.EmailNotificationService" },
            new Component { Id = "SampleProject.Configuration.IServiceCollection", IsInterface = true },
        };
        var analyzer = new DependencyInjectionAnalyzer();

        // Act
        var edges = analyzer.Analyze(compilation, components);

        // Assert
        edges.Should().Contain(e =>
            e.From == "SampleProject.Configuration.EmailNotificationService" &&
            e.To == "SampleProject.Configuration.INotificationService" &&
            e.Type == "registration");
    }

    [Fact]
    public void Analyze_AllRegistrationLifetimes_CreatesThreeRegistrationEdges()
    {
        // Arrange
        var compilation = CreateCompilation(ServiceCollectionRegistrationSource);
        var components = new List<Component>
        {
            new Component { Id = "SampleProject.Configuration.Startup" },
            new Component { Id = "SampleProject.Configuration.IOrderService", IsInterface = true },
            new Component { Id = "SampleProject.Configuration.OrderService" },
            new Component { Id = "SampleProject.Configuration.ICacheService", IsInterface = true },
            new Component { Id = "SampleProject.Configuration.RedisCacheService" },
            new Component { Id = "SampleProject.Configuration.INotificationService", IsInterface = true },
            new Component { Id = "SampleProject.Configuration.EmailNotificationService" },
            new Component { Id = "SampleProject.Configuration.IServiceCollection", IsInterface = true },
        };
        var analyzer = new DependencyInjectionAnalyzer();

        // Act
        var edges = analyzer.Analyze(compilation, components);

        // Assert
        var registrationEdges = edges.Where(e => e.Type == "registration").ToList();
        registrationEdges.Should().HaveCount(3);
    }

    #endregion

    #region Combined Pattern Tests

    [Fact]
    public void Analyze_CombinedPatterns_DetectsAllEdgeTypes()
    {
        // Arrange
        var compilation = CreateCompilation(MultiplePatternSource);
        var components = new List<Component>
        {
            new Component { Id = "SampleProject.UserService" },
            new Component { Id = "SampleProject.IUserRepository", IsInterface = true },
            new Component { Id = "SampleProject.UserRepository" },
            new Component { Id = "SampleProject.User" },
            new Component { Id = "SampleProject.Startup" },
            new Component { Id = "SampleProject.IServiceCollection", IsInterface = true },
        };
        var analyzer = new DependencyInjectionAnalyzer();

        // Act
        var edges = analyzer.Analyze(compilation, components);

        // Assert — should have injection, implements, and registration edges
        edges.Should().Contain(e => e.Type == "injection");
        edges.Should().Contain(e => e.Type == "implements");
        edges.Should().Contain(e => e.Type == "registration");
    }

    [Fact]
    public void Analyze_EmptyCompilation_ReturnsEmptyList()
    {
        // Arrange
        var source = @"namespace Empty { }";
        var compilation = CreateCompilation(source);
        var components = new List<Component>();
        var analyzer = new DependencyInjectionAnalyzer();

        // Act
        var edges = analyzer.Analyze(compilation, components);

        // Assert
        edges.Should().BeEmpty();
    }

    #endregion
}
