using ECommerceApp.DTOs;
using ECommerceApp.Models;
using ECommerceApp.Repositories;
using MassTransit;
using Microsoft.Extensions.Configuration;

namespace ECommerceApp.Services;

public class OrderService : IOrderService
{
    private readonly IOrderRepository _orderRepository;
    private readonly IProductRepository _productRepository;
    private readonly IPublishEndpoint _publishEndpoint;
    private readonly IConfiguration _configuration;

    public OrderService(
        IOrderRepository orderRepository,
        IProductRepository productRepository,
        IPublishEndpoint publishEndpoint,
        IConfiguration configuration)
    {
        _orderRepository = orderRepository;
        _productRepository = productRepository;
        _publishEndpoint = publishEndpoint;
        _configuration = configuration;
    }

    public async Task<IEnumerable<OrderDto>> GetAllOrdersAsync()
    {
        var orders = await _orderRepository.GetAllAsync();
        return orders.Select(MapToDto);
    }

    public async Task<OrderDto?> GetOrderByIdAsync(int id)
    {
        var order = await _orderRepository.GetByIdAsync(id);
        return order == null ? null : MapToDto(order);
    }

    public async Task<OrderDto> PlaceOrderAsync(CreateOrderDto dto)
    {
        var items = new List<OrderItem>();
        decimal total = 0;

        foreach (var item in dto.Items)
        {
            var product = await _productRepository.GetByIdAsync(item.ProductId)
                ?? throw new KeyNotFoundException($"Product {item.ProductId} not found");

            items.Add(new OrderItem
            {
                ProductId = item.ProductId,
                Quantity = item.Quantity,
                UnitPrice = product.Price
            });
            total += product.Price * item.Quantity;
        }

        var order = new Order
        {
            CustomerId = dto.CustomerId,
            Items = items,
            Total = total
        };

        var created = await _orderRepository.CreateAsync(order);

        var ordersTopic = _configuration["Messaging:OrderPlacedTopic"];
        await _publishEndpoint.Publish(new { OrderId = created.Id, created.Total, created.CustomerId });

        return MapToDto(created);
    }

    public async Task CancelOrderAsync(int orderId)
    {
        await _orderRepository.UpdateStatusAsync(orderId, OrderStatus.Cancelled);
        await _publishEndpoint.Publish(new { OrderId = orderId, Status = "Cancelled" });
    }

    public async Task ShipOrderAsync(int orderId)
    {
        await _orderRepository.UpdateStatusAsync(orderId, OrderStatus.Shipped);
        await _publishEndpoint.Publish(new { OrderId = orderId, Status = "Shipped" });
    }

    private static OrderDto MapToDto(Order o) =>
        new(o.Id, o.CustomerId, o.Status.ToString(), o.Total, o.CreatedAt);
}
