using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace ECommerceApp.Models;

[Table("orders")]
public class Order
{
    [Key]
    public int Id { get; set; }

    public int CustomerId { get; set; }

    [ForeignKey(nameof(CustomerId))]
    public Customer Customer { get; set; } = null!;

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public OrderStatus Status { get; set; } = OrderStatus.Pending;

    [Column(TypeName = "decimal(18,2)")]
    public decimal Total { get; set; }

    public ICollection<OrderItem> Items { get; set; } = new List<OrderItem>();
}

public enum OrderStatus
{
    Pending,
    Confirmed,
    Shipped,
    Delivered,
    Cancelled
}
