using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace ECommerceApp.Models;

[Table("customers")]
public class Customer
{
    [Key]
    public int Id { get; set; }

    [Required]
    [MaxLength(100)]
    public string Name { get; set; } = string.Empty;

    [Required]
    [MaxLength(200)]
    public string Email { get; set; } = string.Empty;

    public string Phone { get; set; } = string.Empty;

    public ICollection<Order> Orders { get; set; } = new List<Order>();
}
