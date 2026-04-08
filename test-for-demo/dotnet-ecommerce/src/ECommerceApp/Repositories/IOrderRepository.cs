using ECommerceApp.Models;

namespace ECommerceApp.Repositories;

public interface IOrderRepository
{
    Task<IEnumerable<Order>> GetAllAsync();
    Task<Order?> GetByIdAsync(int id);
    Task<IEnumerable<Order>> GetByCustomerAsync(int customerId);
    Task<Order> CreateAsync(Order order);
    Task UpdateStatusAsync(int orderId, OrderStatus status);
}
