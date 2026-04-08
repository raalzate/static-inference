using SampleProject.Models;
using SampleProject.Repositories;

namespace SampleProject.Services;

public class OrderService : IOrderService
{
    private readonly IOrderRepository _orderRepository;

    public OrderService(IOrderRepository orderRepository)
    {
        _orderRepository = orderRepository;
    }

    public async Task<Order?> GetOrderAsync(int id)
    {
        return await _orderRepository.GetByIdAsync(id);
    }

    public async Task<IEnumerable<Order>> GetAllOrdersAsync()
    {
        return await _orderRepository.GetAllAsync();
    }

    public async Task CreateOrderAsync(Order order)
    {
        order.CreatedAt = DateTime.UtcNow;
        await _orderRepository.AddAsync(order);
    }
}
