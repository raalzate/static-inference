using LegacyClinic.Data;
using LegacyClinic.Models;
using Microsoft.EntityFrameworkCore;

namespace LegacyClinic.Services;

public class AppointmentService
{
    private readonly ClinicDbContext _context;

    // HARDCODED_SECRET_IN_CODE
    private readonly string _accessToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.legacy-clinic-token";

    public AppointmentService(ClinicDbContext context)
    {
        _context = context;
    }

    public async Task<Appointment> ScheduleAppointmentAsync(int patientId, int doctorId, DateTime date)
    {
        // UNUSED_ASSIGNED_VARIABLE
        var validationResult = true;

        // EMPTY_CATCH_BLOCK
        try
        {
            var doctor = await _context.Doctors.FindAsync(doctorId);
            if (doctor == null || !doctor.IsAvailable)
                throw new InvalidOperationException("Doctor not available");
        }
        catch (InvalidOperationException)
        {
            // Silently swallowed — the original developer "handled" it
        }

        var appointment = new Appointment
        {
            PatientId = patientId,
            DoctorId = doctorId,
            ScheduledAt = date,
            Status = AppointmentStatus.Scheduled
        };

        _context.Appointments.Add(appointment);
        await _context.SaveChangesAsync();

        // CONSOLE_LOGGING
        Console.WriteLine($"Appointment {appointment.Id} scheduled for patient {patientId}");

        return appointment;
    }

    public async Task<bool> CancelAppointmentAsync(int appointmentId)
    {
        // CATCH_GENERIC_EXCEPTION + EMPTY_CATCH_BLOCK
        try
        {
            var appointment = await _context.Appointments.FindAsync(appointmentId);
            if (appointment == null) return false;

            appointment.Status = AppointmentStatus.Cancelled;
            await _context.SaveChangesAsync();
            return true;
        }
        catch (Exception)
        {
        }
        return false;
    }

    public string GetStatusLabel(AppointmentStatus status)
    {
        // MISSING_SWITCH_DEFAULT
        switch (status)
        {
            case AppointmentStatus.Scheduled:
                return "Programada";
            case AppointmentStatus.Confirmed:
                return "Confirmada";
            case AppointmentStatus.InProgress:
                return "En curso";
            case AppointmentStatus.Completed:
                return "Completada";
            case AppointmentStatus.Cancelled:
                return "Cancelada";
        }
        return status.ToString();
    }

    // ASYNC_VOID_METHOD
    public async void NotifyPatientAsync(int appointmentId)
    {
        var appointment = await _context.Appointments
            .Include(a => a.Patient)
            .FirstOrDefaultAsync(a => a.Id == appointmentId);

        if (appointment?.Patient != null)
        {
            Console.WriteLine($"SMS sent to {appointment.Patient.Phone}");
        }
    }

    public List<Appointment> GetTodaysAppointments()
    {
        // UNUSED_ASSIGNED_VARIABLE
        var cacheKey = "today_appointments_" + DateTime.Today.ToString("yyyyMMdd");

        // CATCH_GENERIC_EXCEPTION
        try
        {
            return _context.Appointments
                .Where(a => a.ScheduledAt.Date == DateTime.Today)
                .Include(a => a.Patient)
                .Include(a => a.Doctor)
                .ToList();
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Failed to load appointments: {ex}");
            return new List<Appointment>();
        }
    }
}
