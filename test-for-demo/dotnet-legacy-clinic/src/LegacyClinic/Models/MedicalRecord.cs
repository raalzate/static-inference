namespace LegacyClinic.Models;

public class MedicalRecord
{
    public int Id { get; set; }
    public int PatientId { get; set; }
    public Patient Patient { get; set; } = null!;
    public int DoctorId { get; set; }
    public Doctor Doctor { get; set; } = null!;
    public DateTime RecordDate { get; set; }
    public string Symptoms { get; set; } = null!;
    public string Diagnosis { get; set; } = null!;
    public string Treatment { get; set; } = null!;
    public string Prescriptions { get; set; } = null!;
    public string LabResults { get; set; } = null!;
    public string InternalNotes { get; set; } = null!;
}
