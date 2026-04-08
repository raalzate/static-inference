using ECommerceApp.DTOs;
using ECommerceApp.Models;
using ECommerceApp.Repositories;
using MassTransit;
using Microsoft.Extensions.Configuration;

namespace ECommerceApp.Services;

public class ProductService : IProductService
{
    private readonly IProductRepository _productRepository;
    private readonly IPublishEndpoint _publishEndpoint;
    private readonly IConfiguration _configuration;

    public ProductService(
        IProductRepository productRepository,
        IPublishEndpoint publishEndpoint,
        IConfiguration configuration)
    {
        _productRepository = productRepository;
        _publishEndpoint = publishEndpoint;
        _configuration = configuration;
    }

    public async Task<IEnumerable<ProductDto>> GetAllProductsAsync()
    {
        var products = await _productRepository.GetAllAsync();
        return products.Select(MapToDto);
    }

    public async Task<ProductDto?> GetProductByIdAsync(int id)
    {
        var product = await _productRepository.GetByIdAsync(id);
        return product == null ? null : MapToDto(product);
    }

    public async Task<ProductDto> CreateProductAsync(CreateProductDto dto)
    {
        var product = new Product
        {
            Name = dto.Name,
            Description = dto.Description,
            Price = dto.Price,
            Stock = dto.Stock,
            CategoryId = dto.CategoryId
        };

        var created = await _productRepository.CreateAsync(product);

        var catalogTopic = _configuration["Messaging:ProductCreatedTopic"];
        await _publishEndpoint.Publish(new { ProductId = created.Id, created.Name, created.Price });

        return MapToDto(created);
    }

    public async Task UpdateProductAsync(int id, CreateProductDto dto)
    {
        var product = await _productRepository.GetByIdAsync(id)
            ?? throw new KeyNotFoundException($"Product {id} not found");

        product.Name = dto.Name;
        product.Description = dto.Description;
        product.Price = dto.Price;
        product.Stock = dto.Stock;

        await _productRepository.UpdateAsync(product);
    }

    public async Task DeleteProductAsync(int id)
    {
        await _productRepository.DeleteAsync(id);
    }

    private static ProductDto MapToDto(Product p) => new(p.Id, p.Name, p.Description, p.Price, p.Stock, p.CategoryId);
}
