using System.ComponentModel.DataAnnotations.Schema;

namespace SampleProject.Models;

[Table("orders")]
public class Order
{
    public int Id { get; set; }
    public string CustomerName { get; set; } = string.Empty;
    public decimal TotalAmount { get; set; }
    public DateTime CreatedAt { get; set; }
}
