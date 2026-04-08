using ECommerceApp.DTOs;

namespace ECommerceApp.Services;

public interface IOrderService
{
    Task<IEnumerable<OrderDto>> GetAllOrdersAsync();
    Task<OrderDto?> GetOrderByIdAsync(int id);
    Task<OrderDto> PlaceOrderAsync(CreateOrderDto dto);
    Task CancelOrderAsync(int orderId);
    Task ShipOrderAsync(int orderId);
}
