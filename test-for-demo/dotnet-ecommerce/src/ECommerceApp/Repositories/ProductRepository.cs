using ECommerceApp.Data;
using ECommerceApp.Models;
using Microsoft.EntityFrameworkCore;

namespace ECommerceApp.Repositories;

public class ProductRepository : IProductRepository
{
    private readonly AppDbContext _context;

    public ProductRepository(AppDbContext context)
    {
        _context = context;
    }

    public async Task<IEnumerable<Product>> GetAllAsync()
        => await _context.Products.Include(p => p.Category).ToListAsync();

    public async Task<Product?> GetByIdAsync(int id)
        => await _context.Products.Include(p => p.Category).FirstOrDefaultAsync(p => p.Id == id);

    public async Task<IEnumerable<Product>> GetByCategoryAsync(int categoryId)
        => await _context.Products.Where(p => p.CategoryId == categoryId).ToListAsync();

    public async Task<Product> CreateAsync(Product product)
    {
        _context.Products.Add(product);
        await _context.SaveChangesAsync();
        return product;
    }

    public async Task UpdateAsync(Product product)
    {
        _context.Products.Update(product);
        await _context.SaveChangesAsync();
    }

    public async Task DeleteAsync(int id)
    {
        var product = await _context.Products.FindAsync(id);
        if (product != null)
        {
            _context.Products.Remove(product);
            await _context.SaveChangesAsync();
        }
    }

    public async Task<bool> ExistsAsync(int id)
        => await _context.Products.AnyAsync(p => p.Id == id);
}
