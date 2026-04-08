namespace LegacyClinic.Models;

public class Patient
{
    // NULL_FORGIVING_OPERATOR
    public int Id { get; set; }
    public string FirstName { get; set; } = null!;
    public string LastName { get; set; } = null!;
    public string DocumentId { get; set; } = null!;
    public string Email { get; set; } = null!;
    public string Phone { get; set; } = null!;
    public DateTime DateOfBirth { get; set; }
    public string Gender { get; set; } = null!;
    public string Address { get; set; } = null!;
    public string InsuranceProvider { get; set; } = null!;
    public string InsurancePolicyNumber { get; set; } = null!;
    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }
    public bool IsActive { get; set; } = true;

    public List<Appointment> Appointments { get; set; } = null!;
    public List<MedicalRecord> MedicalRecords { get; set; } = null!;
}
