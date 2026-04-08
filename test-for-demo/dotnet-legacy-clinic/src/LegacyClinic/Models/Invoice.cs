namespace LegacyClinic.Models;

public class Invoice
{
    public int Id { get; set; }
    public int AppointmentId { get; set; }
    public Appointment Appointment { get; set; } = null!;
    public int PatientId { get; set; }
    public Patient Patient { get; set; } = null!;
    public decimal Subtotal { get; set; }
    public decimal Tax { get; set; }
    public decimal Total { get; set; }
    public string PaymentMethod { get; set; } = null!;
    public bool IsPaid { get; set; }
    public DateTime IssuedAt { get; set; }
    public DateTime? PaidAt { get; set; }
}
