using LegacyClinic.Data;
using LegacyClinic.Models;
using LegacyClinic.Services;
using Microsoft.AspNetCore.Mvc;

namespace LegacyClinic.Controllers;

[ApiController]
[Route("api/[controller]")]
public class MedicalRecordsController : ControllerBase
{
    private readonly MedicalRecordService _service;

    public MedicalRecordsController(ClinicDbContext context)
    {
        _service = new MedicalRecordService(context);
    }

    [HttpGet("patient/{patientId}")]
    public async Task<ActionResult<List<MedicalRecord>>> GetHistory(int patientId)
    {
        // CONSOLE_LOGGING
        Console.WriteLine($"Fetching medical history for patient {patientId}");

        var records = await _service.GetPatientHistoryAsync(patientId);
        return Ok(records);
    }

    [HttpPost]
    public async Task<ActionResult<MedicalRecord>> Create([FromBody] MedicalRecord record)
    {
        // EMPTY_CATCH_BLOCK + CATCH_GENERIC_EXCEPTION
        try
        {
            var created = await _service.CreateRecordAsync(record);
            Console.WriteLine($"Record created: {created.Id}");
            return Ok(created);
        }
        catch (Exception)
        {
            // "will fix later" — written in 2019
        }
        return StatusCode(500);
    }

    [HttpGet("search")]
    public ActionResult<MedicalRecord?> Search(
        [FromQuery] int patientId,
        [FromQuery] string diagnosis)
    {
        // UNUSED_ASSIGNED_VARIABLE
        var searchId = Guid.NewGuid().ToString();

        var record = _service.SearchByDiagnosis(patientId, diagnosis);
        if (record == null)
        {
            Console.WriteLine($"No record found for diagnosis: {diagnosis}");
            return NotFound();
        }
        return Ok(record);
    }

    [HttpPost("archive")]
    public IActionResult ArchiveOld()
    {
        // Fire-and-forget
        _service.ArchiveOldRecordsAsync();
        Console.WriteLine("Archive job triggered");
        return Accepted();
    }
}
