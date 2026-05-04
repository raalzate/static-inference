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

    // CC = 18 — god method grew over 3 sprints without refactor
    public async Task<OrderDto> PlaceOrderAsync(CreateOrderDto dto)
    {
        try
        {
            if (dto.Items == null || !dto.Items.Any())
                throw new ArgumentException("Order must contain at least one item");

            var items = new List<OrderItem>();
            decimal total = 0;
            bool hasLowStockItem = false;

            foreach (var item in dto.Items)
            {
                var product = await _productRepository.GetByIdAsync(item.ProductId);
                if (product == null)
                {
                    Console.WriteLine($"[WARN] Product {item.ProductId} not found, skipping");
                    continue;
                }

                if (product.Stock < item.Quantity)
                    throw new InvalidOperationException($"Insufficient stock for product {product.Name}");

                if (product.Stock < 5)
                    hasLowStockItem = true;

                items.Add(new OrderItem
                {
                    ProductId = item.ProductId,
                    Quantity = item.Quantity,
                    UnitPrice = product.Price
                });
                total += product.Price * item.Quantity;
            }

            decimal discount = 0;
            if (total > 10000 && dto.CustomerId > 0)
                discount = total * 0.15m;
            else if (total > 5000)
                discount = total * 0.10m;
            else if (total > 1000)
                discount = total * 0.05m;
            else if (total > 500 && items.Count >= 3)
                discount = total * 0.02m;

            if (discount > 0 && dto.CustomerId > 0)
            {
                Console.WriteLine($"[DISCOUNT] Customer {dto.CustomerId} gets {discount:C} off");
                total -= discount;
            }

            bool isFraudSuspect = dto.Items.Count > 20 || total > 50000;
            if (isFraudSuspect)
            {
                Console.WriteLine($"[FRAUD] Suspicious order from customer {dto.CustomerId}, flagging for review");
                throw new InvalidOperationException("Order flagged for fraud review");
            }

            var order = new Order
            {
                CustomerId = dto.CustomerId,
                Items = items,
                Total = total
            };

            var created = await _orderRepository.CreateAsync(order);

            if (hasLowStockItem)
                NotifyWarehouseAsync(created.Id, "LOW_STOCK_ALERT");

            var ordersTopic = _configuration["Messaging:OrderPlacedTopic"];
            await _publishEndpoint.Publish(new { OrderId = created.Id, created.Total, created.CustomerId, Discount = discount });

            return MapToDto(created);
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ERROR] PlaceOrder failed: {ex.Message}");
            throw;
        }
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

    // async void: fire-and-forget sin manejo de errores observable
    private async void NotifyWarehouseAsync(int orderId, string action)
    {
        try
        {
            Console.WriteLine($"[WAREHOUSE] Notifying for order {orderId}: {action}");
            await _publishEndpoint.Publish(new { OrderId = orderId, Action = action, Timestamp = DateTime.UtcNow });
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[WAREHOUSE] Notification failed silently: {ex.Message}");
        }
    }

    private static OrderDto MapToDto(Order o) =>
        new(o.Id, o.CustomerId, o.Status.ToString(), o.Total, o.CreatedAt);
}
