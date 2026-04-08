namespace LegacyClinic.Models;

public enum AppointmentStatus
{
    Scheduled,
    Confirmed,
    InProgress,
    Completed,
    Cancelled,
    NoShow
}

public class Appointment
{
    public int Id { get; set; }
    public int PatientId { get; set; }
    public Patient Patient { get; set; } = null!;
    public int DoctorId { get; set; }
    public Doctor Doctor { get; set; } = null!;
    public DateTime ScheduledAt { get; set; }
    public DateTime? CompletedAt { get; set; }
    public AppointmentStatus Status { get; set; }
    public string Notes { get; set; } = null!;
    public string Diagnosis { get; set; } = null!;
    public decimal AmountCharged { get; set; }
    public bool IsPaid { get; set; }
}
