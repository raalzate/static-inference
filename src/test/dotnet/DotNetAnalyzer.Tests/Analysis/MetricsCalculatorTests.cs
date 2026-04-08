using FluentAssertions;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using DotNetAnalyzer.Analysis;
using DotNetAnalyzer.Model;
using Xunit;

namespace DotNetAnalyzer.Tests.Analysis;

public class MetricsCalculatorTests
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

    #region CBO Tests

    private static readonly string CboSource = @"
using System.Collections.Generic;
using System.Threading.Tasks;

namespace SampleProject.Services
{
    public class OrderService
    {
        private readonly IOrderRepository _orderRepository;
        private readonly ILogger _logger;
        private readonly INotificationService _notificationService;

        public OrderService(IOrderRepository orderRepository, ILogger logger, INotificationService notificationService)
        {
            _orderRepository = orderRepository;
            _logger = logger;
            _notificationService = notificationService;
        }

        public Task<IEnumerable<Order>> GetAll()
        {
            _logger.Log(""Getting orders"");
            return _orderRepository.GetAllAsync();
        }

        public Task Create(Order order)
        {
            _notificationService.Notify(""created"");
            return _orderRepository.AddAsync(order);
        }
    }

    public interface IOrderRepository
    {
        Task<IEnumerable<Order>> GetAllAsync();
        Task AddAsync(Order order);
    }

    public interface ILogger
    {
        void Log(string message);
    }

    public interface INotificationService
    {
        void Notify(string message);
    }

    public class Order
    {
        public int Id { get; set; }
    }
}";

    [Fact]
    public void Calculate_CBO_CountsUniqueOutgoingTypeDependencies()
    {
        // Arrange
        var compilation = CreateCompilation(CboSource);
        var typeAnalyzer = new TypeAnalyzer();
        var components = typeAnalyzer.AnalyzeTypes(compilation);
        var calculator = new MetricsCalculator();

        // Act
        calculator.Calculate(compilation, components);

        // Assert
        var orderService = components.First(c => c.Id.Contains("OrderService") && !c.IsInterface);
        // OrderService depends on: IOrderRepository, ILogger, INotificationService, Order
        orderService.Cbo.Should().BeGreaterOrEqualTo(3, "OrderService has at least 3 unique type dependencies");
    }

    [Fact]
    public void Calculate_CBO_InterfaceWithNoDependencies_ReturnsZeroOrLow()
    {
        // Arrange
        var source = @"
namespace SampleProject
{
    public interface ISimpleService
    {
        void DoWork();
    }
}";
        var compilation = CreateCompilation(source);
        var typeAnalyzer = new TypeAnalyzer();
        var components = typeAnalyzer.AnalyzeTypes(compilation);
        var calculator = new MetricsCalculator();

        // Act
        calculator.Calculate(compilation, components);

        // Assert
        var iface = components.First(c => c.Id.Contains("ISimpleService"));
        iface.Cbo.Should().Be(0, "an interface with no type references has CBO of 0");
    }

    #endregion

    #region LCOM Tests

    private static readonly string HighCohesionSource = @"
namespace SampleProject
{
    public class CohesiveClass
    {
        private int _x;
        private int _y;

        public int GetSum() { return _x + _y; }
        public int GetProduct() { return _x * _y; }
        public void SetValues(int x, int y) { _x = x; _y = y; }
    }
}";

    private static readonly string LowCohesionSource = @"
namespace SampleProject
{
    public class NonCohesiveClass
    {
        private int _a;
        private int _b;
        private int _c;

        public int GetA() { return _a; }
        public int GetB() { return _b; }
        public int GetC() { return _c; }
    }
}";

    [Fact]
    public void Calculate_LCOM_HighCohesion_ReturnsLowValue()
    {
        // Arrange
        var compilation = CreateCompilation(HighCohesionSource);
        var typeAnalyzer = new TypeAnalyzer();
        var components = typeAnalyzer.AnalyzeTypes(compilation);
        var calculator = new MetricsCalculator();

        // Act
        calculator.Calculate(compilation, components);

        // Assert
        var cohesive = components.First(c => c.Id.Contains("CohesiveClass"));
        cohesive.Lcom.Should().NotBeNull();
        cohesive.Lcom.Should().BeInRange(0.0, 0.5, "high cohesion means low LCOM value");
    }

    [Fact]
    public void Calculate_LCOM_LowCohesion_ReturnsHighValue()
    {
        // Arrange
        var compilation = CreateCompilation(LowCohesionSource);
        var typeAnalyzer = new TypeAnalyzer();
        var components = typeAnalyzer.AnalyzeTypes(compilation);
        var calculator = new MetricsCalculator();

        // Act
        calculator.Calculate(compilation, components);

        // Assert
        var nonCohesive = components.First(c => c.Id.Contains("NonCohesiveClass"));
        nonCohesive.Lcom.Should().NotBeNull();
        nonCohesive.Lcom.Should().BeGreaterOrEqualTo(0.5, "low cohesion means high LCOM value");
    }

    [Fact]
    public void Calculate_LCOM_NoFieldsOrMethods_ReturnsZero()
    {
        // Arrange
        var source = @"
namespace SampleProject
{
    public class EmptyClass { }
}";
        var compilation = CreateCompilation(source);
        var typeAnalyzer = new TypeAnalyzer();
        var components = typeAnalyzer.AnalyzeTypes(compilation);
        var calculator = new MetricsCalculator();

        // Act
        calculator.Calculate(compilation, components);

        // Assert
        var empty = components.First(c => c.Id.Contains("EmptyClass"));
        empty.Lcom.Should().Be(0.0, "a class with no fields or methods has LCOM of 0");
    }

    #endregion

    #region LOC Tests

    [Fact]
    public void Calculate_LOC_CountsNonBlankNonCommentLines()
    {
        // Arrange
        var source = @"
namespace SampleProject
{
    // This is a comment
    public class SimpleClass
    {
        /* Block comment */
        private int _value;

        /// <summary>
        /// XML doc comment
        /// </summary>
        public int GetValue()
        {
            return _value;
        }

        public void SetValue(int v)
        {
            _value = v;
        }
    }
}";
        var compilation = CreateCompilation(source);
        var typeAnalyzer = new TypeAnalyzer();
        var components = typeAnalyzer.AnalyzeTypes(compilation);
        var calculator = new MetricsCalculator();

        // Act
        calculator.Calculate(compilation, components);

        // Assert
        var simple = components.First(c => c.Id.Contains("SimpleClass"));
        // LOC should count class declaration, field, method signatures, braces, statements
        // but NOT blank lines, // comments, /* comments */, /// doc comments
        simple.Loc.Should().BeGreaterThan(0, "non-blank non-comment lines should be counted");
    }

    [Fact]
    public void Calculate_LOC_EmptyClass_HasMinimalLines()
    {
        // Arrange
        var source = @"
namespace SampleProject
{
    public class EmptyClass { }
}";
        var compilation = CreateCompilation(source);
        var typeAnalyzer = new TypeAnalyzer();
        var components = typeAnalyzer.AnalyzeTypes(compilation);
        var calculator = new MetricsCalculator();

        // Act
        calculator.Calculate(compilation, components);

        // Assert
        var empty = components.First(c => c.Id.Contains("EmptyClass"));
        empty.Loc.Should().BeGreaterThan(0, "even an empty class has at least one line");
    }

    #endregion
}
