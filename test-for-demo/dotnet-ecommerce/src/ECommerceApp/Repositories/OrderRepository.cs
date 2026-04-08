using ECommerceApp.Data;
using ECommerceApp.Models;
using Microsoft.EntityFrameworkCore;

namespace ECommerceApp.Repositories;

public class OrderRepository : IOrderRepository
{
    private readonly AppDbContext _context;

    public OrderRepository(AppDbContext context)
    {
        _context = context;
    }

    public async Task<IEnumerable<Order>> GetAllAsync()
        => await _context.Orders.Include(o => o.Customer).Include(o => o.Items).ToListAsync();

    public async Task<Order?> GetByIdAsync(int id)
        => await _context.Orders
            .Include(o => o.Customer)
            .Include(o => o.Items).ThenInclude(i => i.Product)
            .FirstOrDefaultAsync(o => o.Id == id);

    public async Task<IEnumerable<Order>> GetByCustomerAsync(int customerId)
        => await _context.Orders.Where(o => o.CustomerId == customerId).Include(o => o.Items).ToListAsync();

    public async Task<Order> CreateAsync(Order order)
    {
        _context.Orders.Add(order);
        await _context.SaveChangesAsync();
        return order;
    }

    public async Task UpdateStatusAsync(int orderId, OrderStatus status)
    {
        var order = await _context.Orders.FindAsync(orderId);
        if (order != null)
        {
            order.Status = status;
            await _context.SaveChangesAsync();
        }
    }
}
