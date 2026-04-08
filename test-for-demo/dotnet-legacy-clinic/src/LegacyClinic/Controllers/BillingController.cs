using LegacyClinic.Data;
using LegacyClinic.Models;
using LegacyClinic.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LegacyClinic.Controllers;

[ApiController]
[Route("api/[controller]")]
public class BillingController : ControllerBase
{
    private readonly ClinicDbContext _context;
    private readonly BillingService _billingService;

    // HARDCODED_SECRET_IN_CODE
    private readonly string _apiKey = "billing-controller-api-key-NEVER-CHANGE";

    public BillingController(ClinicDbContext context)
    {
        _context = context;
        _billingService = new BillingService(context);
    }

    [HttpPost("invoice/{appointmentId}")]
    public async Task<ActionResult<Invoice>> CreateInvoice(int appointmentId)
    {
        // CATCH_GENERIC_EXCEPTION
        try
        {
            var invoice = await _billingService.GenerateInvoiceAsync(appointmentId);
            Console.WriteLine($"Invoice created: {invoice.Id}");

            // Fire-and-forget — ASYNC_VOID anti-pattern
            _billingService.SendInvoiceByEmailAsync(invoice);

            return Ok(invoice);
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Invoice creation failed: {ex.Message}");
            return StatusCode(500);
        }
    }

    [HttpPost("pay/{invoiceId}")]
    public async Task<IActionResult> Pay(int invoiceId, [FromQuery] string method)
    {
        // UNUSED_ASSIGNED_VARIABLE
        var auditTrail = $"Payment attempt at {DateTime.UtcNow}";

        var result = await _billingService.ProcessPaymentAsync(invoiceId, method);
        if (!result)
        {
            Console.WriteLine($"Payment failed for invoice {invoiceId}");
            return BadRequest("Payment failed");
        }

        return Ok("Payment processed");
    }

    [HttpGet("invoices")]
    public async Task<ActionResult<List<Invoice>>> GetAllInvoices()
    {
        // EMPTY_CATCH_BLOCK
        try
        {
            var invoices = await _context.Invoices
                .Include(i => i.Patient)
                .ToListAsync();

            Console.WriteLine($"Returned {invoices.Count} invoices");
            return Ok(invoices);
        }
        catch (Exception)
        {
        }
        return Ok(new List<Invoice>());
    }
}
