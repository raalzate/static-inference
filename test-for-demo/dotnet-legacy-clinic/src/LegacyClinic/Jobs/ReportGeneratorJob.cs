using LegacyClinic.Data;
using LegacyClinic.Models;
using Microsoft.EntityFrameworkCore;

namespace LegacyClinic.Jobs;

public class ReportGeneratorJob
{
    private readonly ClinicDbContext _context;

    // HARDCODED_SECRET_IN_CODE
    private readonly string _token = "report-service-bearer-token-production-2023";

    public ReportGeneratorJob(ClinicDbContext context)
    {
        _context = context;
    }

    public void GenerateDailyReport()
    {
        // CONSOLE_LOGGING everywhere
        Console.WriteLine("=== DAILY CLINIC REPORT ===");
        Console.WriteLine($"Date: {DateTime.Now}");

        // CATCH_GENERIC_EXCEPTION
        try
        {
            var todayAppointments = _context.Appointments
                .Where(a => a.ScheduledAt.Date == DateTime.Today)
                .ToList();

            Console.WriteLine($"Total appointments: {todayAppointments.Count}");

            // MISSING_SWITCH_DEFAULT
            foreach (var apt in todayAppointments)
            {
                switch (apt.Status)
                {
                    case AppointmentStatus.Completed:
                        Console.WriteLine($"  [DONE] Appointment #{apt.Id}");
                        break;
                    case AppointmentStatus.Cancelled:
                        Console.WriteLine($"  [CANCELLED] Appointment #{apt.Id}");
                        break;
                    case AppointmentStatus.NoShow:
                        Console.WriteLine($"  [NO-SHOW] Appointment #{apt.Id}");
                        break;
                }
            }

            // UNUSED_ASSIGNED_VARIABLE
            var reportId = Guid.NewGuid().ToString();

            Console.WriteLine("=== END REPORT ===");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Report generation failed: {ex}");
        }
    }

    // DEPRECATED_THREAD_USAGE
    public void CancelRunningReport(Thread reportThread)
    {
        Console.WriteLine("Aborting report generation thread...");
        reportThread.Abort();
    }

    // ASYNC_VOID_METHOD
    public async void SendReportByEmailAsync(string recipientEmail)
    {
        Console.WriteLine($"Sending daily report to {recipientEmail}");
        await Task.Delay(500);
        Console.WriteLine("Report sent!");
    }
}
