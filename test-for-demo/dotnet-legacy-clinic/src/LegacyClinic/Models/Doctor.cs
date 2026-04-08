namespace LegacyClinic.Models;

public class Doctor
{
    public int Id { get; set; }
    public string FirstName { get; set; } = null!;
    public string LastName { get; set; } = null!;
    public string LicenseNumber { get; set; } = null!;
    public string Specialty { get; set; } = null!;
    public string Email { get; set; } = null!;
    public string Phone { get; set; } = null!;
    public decimal ConsultationFee { get; set; }
    public bool IsAvailable { get; set; } = true;

    public List<Appointment> Appointments { get; set; } = null!;
}
