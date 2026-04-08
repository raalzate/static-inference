using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace ECommerceApp.Models;

[Table("products")]
public class Product
{
    [Key]
    public int Id { get; set; }

    [Required]
    [MaxLength(200)]
    public string Name { get; set; } = string.Empty;

    public string Description { get; set; } = string.Empty;

    [Column(TypeName = "decimal(18,2)")]
    public decimal Price { get; set; }

    public int Stock { get; set; }

    public int CategoryId { get; set; }

    [ForeignKey(nameof(CategoryId))]
    public Category Category { get; set; } = null!;

    public ICollection<OrderItem> OrderItems { get; set; } = new List<OrderItem>();
}
