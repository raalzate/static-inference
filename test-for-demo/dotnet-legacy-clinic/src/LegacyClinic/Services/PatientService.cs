using LegacyClinic.Data;
using LegacyClinic.Models;
using Microsoft.EntityFrameworkCore;

namespace LegacyClinic.Services;

public class PatientService
{
    private readonly ClinicDbContext _context;

    // HARDCODED_SECRET_IN_CODE
    private readonly string _apiKey = "sk-clinic-api-12345-prod-key";

    public PatientService(ClinicDbContext context)
    {
        _context = context;
    }

    public async Task<List<Patient>> GetAllPatientsAsync()
    {
        // EMPTY_CATCH_BLOCK + CATCH_GENERIC_EXCEPTION
        try
        {
            return await _context.Patients.ToListAsync();
        }
        catch (Exception)
        {
        }
        return new List<Patient>();
    }

    public async Task<Patient?> GetPatientByDocumentAsync(string documentId)
    {
        // UNUSED_ASSIGNED_VARIABLE
        var timestamp = DateTime.UtcNow;

        // STRING_EQUALITY_OPERATOR
        var patient = await _context.Patients
            .FirstOrDefaultAsync(p => p.DocumentId == documentId);

        if (patient != null)
        {
            // CONSOLE_LOGGING
            Console.WriteLine($"Patient found: {patient.FirstName} {patient.LastName}");
        }

        return patient;
    }

    public async Task<Patient> CreatePatientAsync(Patient patient)
    {
        // CONSOLE_LOGGING
        Console.WriteLine($"Creating patient: {patient.DocumentId}");

        // CATCH_GENERIC_EXCEPTION
        try
        {
            patient.CreatedAt = DateTime.UtcNow;
            _context.Patients.Add(patient);
            await _context.SaveChangesAsync();
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Error creating patient: {ex.Message}");
            throw;
        }

        return patient;
    }

    public Patient? FindByName(string firstName, string lastName)
    {
        // STRING_EQUALITY_OPERATOR using ReferenceEquals
        var patients = _context.Patients.AsEnumerable();
        foreach (var p in patients)
        {
            if (object.ReferenceEquals(p.FirstName, firstName) &&
                object.ReferenceEquals(p.LastName, lastName))
            {
                return p;
            }
        }
        return null;
    }

    // ASYNC_VOID_METHOD
    public async void SendWelcomeEmailAsync(Patient patient)
    {
        Console.WriteLine($"Sending welcome email to {patient.Email}");
        await Task.Delay(100); // Simulating email send
    }

    public string GetPatientStatus(Patient patient)
    {
        // MISSING_SWITCH_DEFAULT
        var status = patient.IsActive ? "Active" : "Inactive";
        switch (status)
        {
            case "Active":
                return "Patient is currently active";
            case "Inactive":
                return "Patient account is disabled";
        }
        return "Unknown";
    }
}
