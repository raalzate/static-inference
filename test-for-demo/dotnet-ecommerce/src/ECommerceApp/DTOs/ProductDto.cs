using System.Text.Json.Serialization;

namespace ECommerceApp.DTOs;

public record ProductDto(
    [property: JsonPropertyName("id")] int Id,
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("description")] string Description,
    [property: JsonPropertyName("price")] decimal Price,
    [property: JsonPropertyName("stock")] int Stock,
    [property: JsonPropertyName("category_id")] int CategoryId
);

public record CreateProductDto(
    string Name,
    string Description,
    decimal Price,
    int Stock,
    int CategoryId
);
