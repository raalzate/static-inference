using ECommerceApp.Models;

namespace ECommerceApp.Repositories;

public interface IProductRepository
{
    Task<IEnumerable<Product>> GetAllAsync();
    Task<Product?> GetByIdAsync(int id);
    Task<IEnumerable<Product>> GetByCategoryAsync(int categoryId);
    Task<Product> CreateAsync(Product product);
    Task UpdateAsync(Product product);
    Task DeleteAsync(int id);
    Task<bool> ExistsAsync(int id);
}
