using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace ECommerceApp.Models;

[Table("order_items")]
public class OrderItem
{
    [Key]
    public int Id { get; set; }

    public int OrderId { get; set; }

    [ForeignKey(nameof(OrderId))]
    public Order Order { get; set; } = null!;

    public int ProductId { get; set; }

    [ForeignKey(nameof(ProductId))]
    public Product Product { get; set; } = null!;

    public int Quantity { get; set; }

    [Column(TypeName = "decimal(18,2)")]
    public decimal UnitPrice { get; set; }
}
