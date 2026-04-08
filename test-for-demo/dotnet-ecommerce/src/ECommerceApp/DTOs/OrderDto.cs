using System.Text.Json.Serialization;

namespace ECommerceApp.DTOs;

public record OrderDto(
    [property: JsonPropertyName("id")] int Id,
    [property: JsonPropertyName("customer_id")] int CustomerId,
    [property: JsonPropertyName("status")] string Status,
    [property: JsonPropertyName("total")] decimal Total,
    [property: JsonPropertyName("created_at")] DateTime CreatedAt
);

public record CreateOrderDto(
    int CustomerId,
    List<OrderItemDto> Items
);

public record OrderItemDto(
    int ProductId,
    int Quantity
);
