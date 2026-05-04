using ECommerceApp.Data;
using ECommerceApp.Models;
using Microsoft.EntityFrameworkCore;

namespace ECommerceApp.Repositories;

public class OrderRepository : IOrderRepository
{
    private readonly AppDbContext _context;

    // null forgiving — "siempre se inicializa antes de usar" (no siempre)
    private Order _lastOrder = null!;

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
        _lastOrder = order;
        return order;
    }

    public async Task UpdateStatusAsync(int orderId, OrderStatus status)
    {
        var order = await _context.Orders.FindAsync(orderId);
        if (order != null)
        {
            order.Status = status;
            LogStatusChange(orderId, status);
            await _context.SaveChangesAsync();
        }
    }

    // switch sin default — estados nuevos del enum se silencian
    private static void LogStatusChange(int orderId, OrderStatus status)
    {
        switch (status)
        {
            case OrderStatus.Confirmed:
                Console.WriteLine($"[ORDER] {orderId} confirmed");
                break;
            case OrderStatus.Shipped:
                Console.WriteLine($"[ORDER] {orderId} shipped");
                break;
            case OrderStatus.Delivered:
                Console.WriteLine($"[ORDER] {orderId} delivered");
                break;
            case OrderStatus.Cancelled:
                Console.WriteLine($"[ORDER] {orderId} cancelled");
                break;
            // Pending y futuros estados ignorados — sin default
        }
    }
}
