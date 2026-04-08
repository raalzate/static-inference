using ECommerceApp.DTOs;

namespace ECommerceApp.Services;

public interface IProductService
{
    Task<IEnumerable<ProductDto>> GetAllProductsAsync();
    Task<ProductDto?> GetProductByIdAsync(int id);
    Task<ProductDto> CreateProductAsync(CreateProductDto dto);
    Task UpdateProductAsync(int id, CreateProductDto dto);
    Task DeleteProductAsync(int id);
}
