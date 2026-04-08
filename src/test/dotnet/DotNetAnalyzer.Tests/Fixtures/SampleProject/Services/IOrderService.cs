using SampleProject.Models;

namespace SampleProject.Services;

public interface IOrderService
{
    Task<Order?> GetOrderAsync(int id);
    Task<IEnumerable<Order>> GetAllOrdersAsync();
    Task CreateOrderAsync(Order order);
}
