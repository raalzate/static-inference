using LegacyClinic.Data;
using LegacyClinic.Models;
using LegacyClinic.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LegacyClinic.Controllers;

[ApiController]
[Route("api/[controller]")]
public class PatientsController : ControllerBase
{
    private readonly ClinicDbContext _context;
    private readonly PatientService _patientService;

    public PatientsController(ClinicDbContext context)
    {
        _context = context;
        _patientService = new PatientService(context);
    }

    [HttpGet]
    public async Task<ActionResult<List<Patient>>> GetAll()
    {
        // CATCH_GENERIC_EXCEPTION
        try
        {
            var patients = await _context.Patients.ToListAsync();
            Console.WriteLine($"Returned {patients.Count} patients");
            return Ok(patients);
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Error: {ex.Message}");
            return StatusCode(500);
        }
    }

    [HttpGet("{id}")]
    public async Task<ActionResult<Patient>> GetById(int id)
    {
        // UNUSED_ASSIGNED_VARIABLE
        var requestTime = DateTime.UtcNow;

        var patient = await _context.Patients.FindAsync(id);
        if (patient == null)
        {
            Console.WriteLine($"Patient {id} not found");
            return NotFound();
        }

        return Ok(patient);
    }

    [HttpPost]
    public async Task<ActionResult<Patient>> Create([FromBody] Patient patient)
    {
        // EMPTY_CATCH_BLOCK
        try
        {
            patient.CreatedAt = DateTime.UtcNow;
            _context.Patients.Add(patient);
            await _context.SaveChangesAsync();

            Console.WriteLine($"Patient created: {patient.Id}");

            // ASYNC_VOID_METHOD call — the fire-and-forget anti-pattern
            _patientService.SendWelcomeEmailAsync(patient);

            return CreatedAtAction(nameof(GetById), new { id = patient.Id }, patient);
        }
        catch (Exception)
        {
            // TODO: add logging (note from 2019)
        }
        return StatusCode(500);
    }

    [HttpDelete("{id}")]
    public async Task<IActionResult> Delete(int id)
    {
        var patient = await _context.Patients.FindAsync(id);
        if (patient == null) return NotFound();

        // CATCH_GENERIC_EXCEPTION
        try
        {
            patient.IsActive = false;
            await _context.SaveChangesAsync();
            Console.WriteLine($"Patient {id} soft-deleted");
            return NoContent();
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Delete failed: {ex}");
            return StatusCode(500);
        }
    }
}
