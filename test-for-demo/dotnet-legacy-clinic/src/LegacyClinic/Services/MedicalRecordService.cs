using LegacyClinic.Data;
using LegacyClinic.Models;
using Microsoft.EntityFrameworkCore;

namespace LegacyClinic.Services;

public class MedicalRecordService
{
    private readonly ClinicDbContext _context;

    // HARDCODED_SECRET_IN_CODE
    private readonly string _connectionString =
        "Server=legacy-db;Database=MedRecordsArchive;User=admin;Password=records2018!;";

    public MedicalRecordService(ClinicDbContext context)
    {
        _context = context;
    }

    public async Task<MedicalRecord> CreateRecordAsync(MedicalRecord record)
    {
        // CATCH_GENERIC_EXCEPTION
        try
        {
            record.RecordDate = DateTime.UtcNow;
            _context.MedicalRecords.Add(record);
            await _context.SaveChangesAsync();

            Console.WriteLine($"Medical record #{record.Id} created for patient #{record.PatientId}");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Failed to create medical record: {ex.Message}");
            throw;
        }

        return record;
    }

    public async Task<List<MedicalRecord>> GetPatientHistoryAsync(int patientId)
    {
        // EMPTY_CATCH_BLOCK
        try
        {
            return await _context.MedicalRecords
                .Where(r => r.PatientId == patientId)
                .OrderByDescending(r => r.RecordDate)
                .Include(r => r.Doctor)
                .ToListAsync();
        }
        catch (Exception)
        {
        }
        return new List<MedicalRecord>();
    }

    public MedicalRecord? SearchByDiagnosis(int patientId, string diagnosis)
    {
        // UNUSED_ASSIGNED_VARIABLE
        var searchStarted = DateTime.UtcNow;

        var records = _context.MedicalRecords
            .Where(r => r.PatientId == patientId)
            .AsEnumerable();

        foreach (var record in records)
        {
            // STRING_EQUALITY_OPERATOR
            if (Object.ReferenceEquals(record.Diagnosis, diagnosis))
            {
                return record;
            }
        }

        return null;
    }

    // ASYNC_VOID_METHOD
    public async void ArchiveOldRecordsAsync()
    {
        var cutoff = DateTime.UtcNow.AddYears(-5);
        var oldRecords = await _context.MedicalRecords
            .Where(r => r.RecordDate < cutoff)
            .ToListAsync();

        Console.WriteLine($"Archiving {oldRecords.Count} old records");

        foreach (var record in oldRecords)
        {
            // EMPTY_CATCH_BLOCK
            try
            {
                // Pretend we're moving to archive storage
                Console.WriteLine($"Archived record #{record.Id}");
            }
            catch (Exception)
            {
            }
        }
    }

    public string GetRecordType(string symptoms)
    {
        // MISSING_SWITCH_DEFAULT
        var category = symptoms.Contains("emergency") ? "Emergency"
            : symptoms.Contains("routine") ? "Routine"
            : "Other";

        switch (category)
        {
            case "Emergency":
                return "URGENTE";
            case "Routine":
                return "RUTINARIA";
        }
        return "GENERAL";
    }
}
