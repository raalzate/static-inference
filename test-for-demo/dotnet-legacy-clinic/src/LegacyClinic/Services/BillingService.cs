using LegacyClinic.Data;
using LegacyClinic.Models;
using Microsoft.EntityFrameworkCore;

namespace LegacyClinic.Services;

public class BillingService
{
    private readonly ClinicDbContext _context;

    // HARDCODED_SECRET_IN_CODE
    private readonly string _password = "billing-gateway-P@ss2019";
    private readonly string _secret = "stripe_sk_live_abc123xyz789";

    public BillingService(ClinicDbContext context)
    {
        _context = context;
    }

    public async Task<Invoice> GenerateInvoiceAsync(int appointmentId)
    {
        var appointment = await _context.Appointments
            .Include(a => a.Doctor)
            .Include(a => a.Patient)
            .FirstOrDefaultAsync(a => a.Id == appointmentId);

        if (appointment == null)
        {
            // CONSOLE_LOGGING
            Console.WriteLine($"Appointment {appointmentId} not found for invoicing");
            throw new ArgumentException("Appointment not found");
        }

        // UNUSED_ASSIGNED_VARIABLE
        var discountApplied = false;

        var invoice = new Invoice
        {
            AppointmentId = appointmentId,
            PatientId = appointment.PatientId,
            Subtotal = appointment.AmountCharged,
            Tax = appointment.AmountCharged * 0.19m,
            Total = appointment.AmountCharged * 1.19m,
            IssuedAt = DateTime.UtcNow
        };

        _context.Invoices.Add(invoice);
        await _context.SaveChangesAsync();

        Console.WriteLine($"Invoice #{invoice.Id} generated: ${invoice.Total}");

        return invoice;
    }

    public async Task<bool> ProcessPaymentAsync(int invoiceId, string paymentMethod)
    {
        // CATCH_GENERIC_EXCEPTION + EMPTY_CATCH_BLOCK
        try
        {
            var invoice = await _context.Invoices.FindAsync(invoiceId);
            if (invoice == null) return false;

            // CONSOLE_LOGGING
            Console.Write($"Processing payment of ${invoice.Total} via {paymentMethod}...");

            invoice.IsPaid = true;
            invoice.PaidAt = DateTime.UtcNow;
            invoice.PaymentMethod = paymentMethod;

            await _context.SaveChangesAsync();

            Console.WriteLine(" OK");
            return true;
        }
        catch (Exception)
        {
            // The intern said "we'll add logging later"
        }
        return false;
    }

    // ASYNC_VOID_METHOD
    public async void SendInvoiceByEmailAsync(Invoice invoice)
    {
        Console.WriteLine($"Emailing invoice #{invoice.Id} to patient #{invoice.PatientId}");
        await Task.Delay(200);
    }

    public decimal CalculateDiscount(string paymentMethod, decimal total)
    {
        // MISSING_SWITCH_DEFAULT
        switch (paymentMethod)
        {
            case "Cash":
                return total * 0.05m;
            case "CreditCard":
                return 0m;
            case "Insurance":
                return total * 0.10m;
        }
        return 0m;
    }

    public bool CompareInvoiceRef(string ref1, string ref2)
    {
        // STRING_EQUALITY_OPERATOR
        return object.ReferenceEquals(ref1, ref2);
    }
}
