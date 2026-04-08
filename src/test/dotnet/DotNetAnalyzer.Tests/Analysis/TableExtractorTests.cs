using FluentAssertions;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using DotNetAnalyzer.Analysis;
using DotNetAnalyzer.Model;
using Xunit;

namespace DotNetAnalyzer.Tests.Analysis;

public class TableExtractorTests
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

    private static readonly string DbContextWithDbSetSource = @"
using System;

namespace Microsoft.EntityFrameworkCore
{
    public class DbContext
    {
        public DbContext() { }
    }

    public class DbSet<TEntity> where TEntity : class { }

    public class DbContextOptions<TContext> { }
}

namespace System.ComponentModel.DataAnnotations.Schema
{
    [AttributeUsage(AttributeTargets.Class)]
    public class TableAttribute : Attribute
    {
        public string Name { get; }
        public TableAttribute(string name) { Name = name; }
    }
}

namespace SampleProject.Models
{
    using System.ComponentModel.DataAnnotations.Schema;

    [Table(""orders"")]
    public class Order
    {
        public int Id { get; set; }
        public string CustomerName { get; set; } = string.Empty;
        public decimal TotalAmount { get; set; }
    }
}

namespace SampleProject.Data
{
    using Microsoft.EntityFrameworkCore;
    using SampleProject.Models;

    public class AppDbContext : DbContext
    {
        public DbSet<Order> Orders { get; set; } = null!;
    }
}

namespace SampleProject.Repositories
{
    using SampleProject.Data;
    using SampleProject.Models;

    public class OrderRepository
    {
        private readonly AppDbContext _context;

        public OrderRepository(AppDbContext context)
        {
            _context = context;
        }

        public void Add(Order order)
        {
            _context.Orders.Add(order);
        }
    }
}";

    [Fact]
    public void ExtractTables_WithDbSet_FindsTableName()
    {
        // Arrange
        var compilation = CreateCompilation(DbContextWithDbSetSource);
        var components = new List<Component>
        {
            new Component { Id = "SampleProject.Repositories.OrderRepository" },
            new Component { Id = "SampleProject.Data.AppDbContext" },
        };
        var extractor = new TableExtractor();

        // Act
        extractor.ExtractTables(compilation, components);

        // Assert — the repository or context component should have the table name populated
        var repoOrContext = components.FirstOrDefault(c =>
            c.TablesUsed.Any(t => t.Equals("orders", StringComparison.OrdinalIgnoreCase) ||
                                  t.Equals("Orders", StringComparison.OrdinalIgnoreCase)));
        repoOrContext.Should().NotBeNull("a component that uses DbSet<Order> should have a table entry");
        repoOrContext!.TablesUsed.Should().Contain(t =>
            t.Equals("orders", StringComparison.OrdinalIgnoreCase));
    }

    [Fact]
    public void ExtractTables_WithTableAttribute_FindsTableName()
    {
        // Arrange
        var compilation = CreateCompilation(DbContextWithDbSetSource);
        var components = new List<Component>
        {
            new Component { Id = "SampleProject.Repositories.OrderRepository" },
            new Component { Id = "SampleProject.Data.AppDbContext" },
        };
        var extractor = new TableExtractor();

        // Act
        extractor.ExtractTables(compilation, components);

        // Assert — the [Table("orders")] attribute should be detected and used as the table name
        var allTables = components.SelectMany(c => c.TablesUsed).ToList();
        allTables.Should().Contain(t =>
            t.Equals("orders", StringComparison.OrdinalIgnoreCase),
            "the [Table(\"orders\")] attribute on Order class should be detected");
    }
}
