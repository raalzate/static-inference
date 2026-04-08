using LegacyClinic.Data;
using LegacyClinic.Models;
using LegacyClinic.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LegacyClinic.Controllers;

[ApiController]
[Route("api/[controller]")]
public class AppointmentsController : ControllerBase
{
    private readonly ClinicDbContext _context;
    private readonly AppointmentService _appointmentService;

    public AppointmentsController(ClinicDbContext context)
    {
        _context = context;
        _appointmentService = new AppointmentService(context);
    }

    [HttpGet]
    public async Task<ActionResult<List<Appointment>>> GetAll()
    {
        // CATCH_GENERIC_EXCEPTION + CONSOLE_LOGGING
        try
        {
            var appointments = await _context.Appointments
                .Include(a => a.Patient)
                .Include(a => a.Doctor)
                .ToListAsync();

            Console.WriteLine($"Returned {appointments.Count} appointments");
            return Ok(appointments);
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Error loading appointments: {ex.Message}");
            return StatusCode(500);
        }
    }

    [HttpPost]
    public async Task<ActionResult<Appointment>> Schedule(
        [FromQuery] int patientId,
        [FromQuery] int doctorId,
        [FromQuery] DateTime date)
    {
        // UNUSED_ASSIGNED_VARIABLE
        var correlationId = Guid.NewGuid();

        // EMPTY_CATCH_BLOCK
        try
        {
            var appointment = await _appointmentService
                .ScheduleAppointmentAsync(patientId, doctorId, date);

            Console.WriteLine($"Appointment scheduled: {appointment.Id}");
            return Ok(appointment);
        }
        catch (Exception)
        {
        }
        return StatusCode(500);
    }

    [HttpPut("{id}/cancel")]
    public async Task<IActionResult> Cancel(int id)
    {
        var result = await _appointmentService.CancelAppointmentAsync(id);
        if (!result)
        {
            Console.WriteLine($"Failed to cancel appointment {id}");
            return NotFound();
        }

        // Fire-and-forget notification
        _appointmentService.NotifyPatientAsync(id);

        return NoContent();
    }

    [HttpGet("today")]
    public ActionResult<List<Appointment>> GetToday()
    {
        var appointments = _appointmentService.GetTodaysAppointments();
        Console.WriteLine($"Today's appointments: {appointments.Count}");
        return Ok(appointments);
    }
}
