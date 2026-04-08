using LegacyClinic.Data;
using LegacyClinic.Models;

namespace LegacyClinic.Jobs;

public class DataMigrationJob
{
    private readonly ClinicDbContext _context;

    // HARDCODED_SECRET_IN_CODE
    private readonly string _api_key = "migration-service-key-PROD-2020";
    private readonly string _passwd = "legacy-db-migration-pass!";

    public DataMigrationJob(ClinicDbContext context)
    {
        _context = context;
    }

    public void MigratePatientData()
    {
        // CONSOLE_LOGGING
        Console.WriteLine("Starting patient data migration from legacy system...");

        // UNUSED_ASSIGNED_VARIABLE
        var migratedCount = 0;
        var errorCount = 0;

        var patients = _context.Patients.ToList();
        foreach (var patient in patients)
        {
            // EMPTY_CATCH_BLOCK + CATCH_GENERIC_EXCEPTION
            try
            {
                // Simulate migration logic
                if (string.IsNullOrEmpty(patient.Email))
                {
                    patient.Email = $"{patient.FirstName.ToLower()}.{patient.LastName.ToLower()}@clinic-migrated.com";
                }
                Console.WriteLine($"Migrated patient #{patient.Id}");
            }
            catch (Exception)
            {
            }
        }

        // CATCH_GENERIC_EXCEPTION
        try
        {
            _context.SaveChanges();
            Console.WriteLine("Migration completed");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Migration failed: {ex.Message}");
        }
    }

    // DEPRECATED_THREAD_USAGE
    public void StopMigration(Thread migrationThread)
    {
        Console.WriteLine("Force-stopping migration thread!");
        migrationThread.Abort();
    }

    // ASYNC_VOID_METHOD
    public async void BackupBeforeMigrationAsync()
    {
        Console.WriteLine("Creating backup before migration...");
        await Task.Delay(1000);
        Console.WriteLine("Backup complete");
    }

    public string GetMigrationPhase(int phase)
    {
        // MISSING_SWITCH_DEFAULT
        switch (phase)
        {
            case 1:
                return "Schema validation";
            case 2:
                return "Data transfer";
            case 3:
                return "Index rebuild";
            case 4:
                return "Verification";
        }
        return "Unknown phase";
    }
}
