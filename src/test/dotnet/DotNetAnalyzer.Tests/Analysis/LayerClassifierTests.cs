using FluentAssertions;
using DotNetAnalyzer.Analysis;
using DotNetAnalyzer.Model;
using Xunit;

namespace DotNetAnalyzer.Tests.Analysis;

public class LayerClassifierTests
{
    private readonly LayerClassifier _classifier = new();

    #region Controller Layer Tests

    [Fact]
    public void Classify_ApiControllerAttribute_AssignsControladorLayer()
    {
        // Arrange
        var components = new List<Component>
        {
            new Component
            {
                Id = "SampleProject.Controllers.OrdersController",
                Annotations = new List<string> { "ApiController", "Route" },
            }
        };

        // Act
        _classifier.Classify(components);

        // Assert
        components[0].Layer.Should().Be("Controlador");
    }

    [Fact]
    public void Classify_ControllerSuffix_AssignsControladorLayer()
    {
        // Arrange
        var components = new List<Component>
        {
            new Component
            {
                Id = "SampleProject.Controllers.ProductsController",
                Annotations = new List<string>(),
            }
        };

        // Act
        _classifier.Classify(components);

        // Assert
        components[0].Layer.Should().Be("Controlador");
    }

    #endregion

    #region Business Layer Tests

    [Fact]
    public void Classify_ServiceSuffix_AssignsNegocioLayer()
    {
        // Arrange
        var components = new List<Component>
        {
            new Component
            {
                Id = "SampleProject.Services.OrderService",
                Annotations = new List<string>(),
            }
        };

        // Act
        _classifier.Classify(components);

        // Assert
        components[0].Layer.Should().Be("Negocio");
    }

    [Fact]
    public void Classify_ManagerSuffix_AssignsNegocioLayer()
    {
        // Arrange
        var components = new List<Component>
        {
            new Component
            {
                Id = "SampleProject.Business.OrderManager",
                Annotations = new List<string>(),
            }
        };

        // Act
        _classifier.Classify(components);

        // Assert
        components[0].Layer.Should().Be("Negocio");
    }

    [Fact]
    public void Classify_HandlerSuffix_AssignsNegocioLayer()
    {
        // Arrange
        var components = new List<Component>
        {
            new Component
            {
                Id = "SampleProject.Handlers.CreateOrderHandler",
                Annotations = new List<string>(),
            }
        };

        // Act
        _classifier.Classify(components);

        // Assert
        components[0].Layer.Should().Be("Negocio");
    }

    #endregion

    #region Data/Persistence Layer Tests

    [Fact]
    public void Classify_DbContextSuffix_AssignsDatosLayer()
    {
        // Arrange
        var components = new List<Component>
        {
            new Component
            {
                Id = "SampleProject.Data.ApplicationDbContext",
                Annotations = new List<string>(),
                Extends = "Microsoft.EntityFrameworkCore.DbContext",
            }
        };

        // Act
        _classifier.Classify(components);

        // Assert
        components[0].Layer.Should().BeOneOf("Datos", "Persistencia");
    }

    [Fact]
    public void Classify_RepositorySuffix_AssignsDatosLayer()
    {
        // Arrange
        var components = new List<Component>
        {
            new Component
            {
                Id = "SampleProject.Repositories.OrderRepository",
                Annotations = new List<string>(),
            }
        };

        // Act
        _classifier.Classify(components);

        // Assert
        components[0].Layer.Should().BeOneOf("Datos", "Persistencia");
    }

    [Fact]
    public void Classify_DataSuffix_AssignsDatosLayer()
    {
        // Arrange
        var components = new List<Component>
        {
            new Component
            {
                Id = "SampleProject.Infrastructure.OrderData",
                Annotations = new List<string>(),
            }
        };

        // Act
        _classifier.Classify(components);

        // Assert
        components[0].Layer.Should().BeOneOf("Datos", "Persistencia");
    }

    #endregion

    #region Shared Layer Tests

    [Fact]
    public void Classify_HelperSuffix_AssignsCompartidaLayer()
    {
        // Arrange
        var components = new List<Component>
        {
            new Component
            {
                Id = "SampleProject.Utilities.StringHelper",
                Annotations = new List<string>(),
            }
        };

        // Act
        _classifier.Classify(components);

        // Assert
        components[0].Layer.Should().Be("Compartida");
    }

    [Fact]
    public void Classify_ExtensionsSuffix_AssignsCompartidaLayer()
    {
        // Arrange
        var components = new List<Component>
        {
            new Component
            {
                Id = "SampleProject.Extensions.ServiceCollectionExtensions",
                Annotations = new List<string>(),
            }
        };

        // Act
        _classifier.Classify(components);

        // Assert
        components[0].Layer.Should().Be("Compartida");
    }

    [Fact]
    public void Classify_MiddlewareSuffix_AssignsCompartidaLayer()
    {
        // Arrange
        var components = new List<Component>
        {
            new Component
            {
                Id = "SampleProject.Middleware.ExceptionHandlingMiddleware",
                Annotations = new List<string>(),
            }
        };

        // Act
        _classifier.Classify(components);

        // Assert
        components[0].Layer.Should().Be("Compartida");
    }

    #endregion

    #region Default / Unknown Tests

    [Fact]
    public void Classify_NoMatchingPatterns_AssignsCompartidaLayer()
    {
        // Arrange
        var components = new List<Component>
        {
            new Component
            {
                Id = "SampleProject.SomeRandomClass",
                Annotations = new List<string>(),
            }
        };

        // Act
        _classifier.Classify(components);

        // Assert
        components[0].Layer.Should().Be("Compartida");
    }

    [Fact]
    public void Classify_MultipleComponents_AssignsLayersToAll()
    {
        // Arrange
        var components = new List<Component>
        {
            new Component { Id = "App.OrdersController", Annotations = new List<string> { "ApiController" } },
            new Component { Id = "App.OrderService", Annotations = new List<string>() },
            new Component { Id = "App.OrderRepository", Annotations = new List<string>() },
            new Component { Id = "App.StringHelper", Annotations = new List<string>() },
        };

        // Act
        _classifier.Classify(components);

        // Assert
        components.Should().OnlyContain(c => c.Layer != null, "all components should have a layer assigned");
        components[0].Layer.Should().Be("Controlador");
        components[1].Layer.Should().Be("Negocio");
        components[2].Layer.Should().BeOneOf("Datos", "Persistencia");
        components[3].Layer.Should().Be("Compartida");
    }

    #endregion
}
