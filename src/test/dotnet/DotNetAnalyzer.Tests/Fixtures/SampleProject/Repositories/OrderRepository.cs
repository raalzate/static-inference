using Microsoft.EntityFrameworkCore;
using SampleProject.Data;
using SampleProject.Models;

namespace SampleProject.Repositories;

public class OrderRepository : IOrderRepository
{
    private readonly AppDbContext _context;

    public OrderRepository(AppDbContext context)
    {
        _context = context;
    }

    public async Task<Order?> GetByIdAsync(int id)
    {
        return await _context.Orders.FindAsync(id);
    }

    public async Task<IEnumerable<Order>> GetAllAsync()
    {
        return await _context.Orders.ToListAsync();
    }

    public async Task AddAsync(Order order)
    {
        _context.Orders.Add(order);
        await _context.SaveChangesAsync();
    }
}
