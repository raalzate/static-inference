// ============================================================
// NOMIN430 — API Layer: REST Controller
// ASP.NET Core 8 — Minimal API style + Controller
// Sofka Technologies — Marzo 2026
// ============================================================
using MediatR;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using NominaDestajo.Application.Commands;

namespace NominaDestajo.Api.Controllers;

/// <summary>
/// API REST para liquidación de nómina a destajo semanal.
/// Reemplaza el WinForms Nomin430 del sistema legado con una API RESTful.
/// </summary>
[ApiController]
[Route("api/nomina/destajo")]
[Authorize]
[Produces("application/json")]
public class NominaDestajoController : ControllerBase
{
    private readonly IMediator _mediator;
    private readonly ILogger<NominaDestajoController> _logger;

    public NominaDestajoController(IMediator mediator, ILogger<NominaDestajoController> logger)
    {
        _mediator = mediator;
        _logger = logger;
    }

    /// <summary>
    /// Ejecuta el proceso de liquidación destajo para un rango de empleados.
    /// Equivale al botón "Procesar" de Nomin430 en el sistema legado.
    /// </summary>
    /// <remarks>
    /// El proceso es asíncrono y transaccional por empleado.
    /// Si falla un empleado, los demás continúan procesándose.
    ///
    /// **Modos de pago procesados:** D (destajo), T (turno), A (automático), G (general con extras)
    ///
    /// **Reglas críticas:** BR-01 (subperíodo especial), BR-04 (acumulados), BR-06 (impacto en nómina general)
    /// </remarks>
    [HttpPost("liquidar")]
    [Authorize(Roles = "Liquidador,Administrador")]
    [ProducesResponseType(typeof(LiquidacionResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status409Conflict)]
    public async Task<IActionResult> EjecutarLiquidacion(
        [FromBody] EjecutarLiquidacionRequest request,
        CancellationToken cancellationToken)
    {
        _logger.LogInformation(
            "Iniciando liquidación destajo Cia={Cia} Suc={Suc} {Ano}/{Per}/{Sub} Sem={Sem}",
            request.Cia, request.Sucursal, request.Ano, request.Periodo, request.Subper, request.Semana);

        var command = new EjecutarLiquidacionCommand
        {
            Cia = request.Cia,
            Sucursal = request.Sucursal,
            Ano = request.Ano,
            Periodo = request.Periodo,
            Subper = request.Subper,
            Semana = request.Semana,
            SeccionInicio = request.SeccionInicio,
            EmpleadoInicio = request.EmpleadoInicio,
            SeccionFin = request.SeccionFin,
            EmpleadoFin = request.EmpleadoFin,
            DiasFestivos = request.DiasFestivos ?? [],
            ModoMasivo = request.ModoMasivo,
            UsuarioSistema = User.Identity?.Name ?? "SISTEMA"
        };

        var result = await _mediator.Send(command, cancellationToken);

        if (!result.Exitoso && result.EmpleadosProcesados == 0)
            return Conflict(new ProblemDetails
            {
                Title = "Liquidación bloqueada",
                Detail = result.MensajeError,
                Status = StatusCodes.Status409Conflict
            });

        return Ok(result);
    }

    /// <summary>
    /// Consulta los registros de destajo para un período.
    /// Permite filtrar por subperíodo y empleado.
    /// </summary>
    [HttpGet("{cia}/{sucursal}/{ano}/{periodo}")]
    [Authorize(Roles = "Liquidador,Consultor,Administrador")]
    [ProducesResponseType(typeof(IReadOnlyList<DestajoDto>), StatusCodes.Status200OK)]
    public async Task<IActionResult> ConsultarDestajo(
        string cia, string sucursal, string ano, string periodo,
        [FromQuery] string? subper = null,
        [FromQuery] string? empleado = null,
        CancellationToken cancellationToken = default)
    {
        var query = new ConsultarDestajoQuery
        {
            Cia = cia, Sucursal = sucursal, Ano = ano, Periodo = periodo,
            Subper = subper, CodEmpleado = empleado
        };

        var result = await _mediator.Send(query, cancellationToken);
        return Ok(result);
    }

    /// <summary>
    /// Exporta las observaciones de liquidación como archivo Excel.
    /// Equivale a msgProceso.CopyToXLS() del sistema legado.
    /// </summary>
    [HttpGet("{cia}/{sucursal}/{ano}/{periodo}/{subper}/observaciones/export")]
    [Authorize(Roles = "Liquidador,Administrador")]
    [ProducesResponseType(typeof(FileResult), StatusCodes.Status200OK)]
    public async Task<IActionResult> ExportarObservaciones(
        string cia, string sucursal, string ano, string periodo, string subper,
        [FromQuery] string semana = "00",
        CancellationToken cancellationToken = default)
    {
        var query = new ExportarObservacionesQuery
        {
            Cia = cia, Sucursal = sucursal, Ano = ano, Periodo = periodo, Subper = subper, Semana = semana
        };

        var fileBytes = await _mediator.Send(query, cancellationToken);
        var fileName = $"Obs_Destajo_{cia}-{sucursal}_{ano}{periodo}{subper}_Sem{semana}.xlsx";
        return File(fileBytes, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", fileName);
    }

    /// <summary>
    /// Verifica si hay registros acumulados en el rango indicado (BR-04).
    /// Útil para validar antes de ejecutar la liquidación.
    /// </summary>
    [HttpGet("verificar-acumulados")]
    [Authorize(Roles = "Liquidador,Administrador")]
    [ProducesResponseType(typeof(VerificacionAcumuladosResponse), StatusCodes.Status200OK)]
    public async Task<IActionResult> VerificarAcumulados(
        [FromQuery] string cia, [FromQuery] string sucursal,
        [FromQuery] string ano, [FromQuery] string periodo, [FromQuery] string subper,
        [FromQuery] string secIni, [FromQuery] string empIni,
        [FromQuery] string secFin, [FromQuery] string empFin,
        CancellationToken cancellationToken = default)
    {
        var query = new VerificarAcumuladosQuery
        {
            Cia = cia, Sucursal = sucursal, Ano = ano, Periodo = periodo, Subper = subper,
            SeccionInicio = secIni, EmpleadoInicio = empIni, SeccionFin = secFin, EmpleadoFin = empFin
        };

        var result = await _mediator.Send(query, cancellationToken);
        return Ok(result);
    }
}

// ============================================================
// REQUEST / RESPONSE DTOs
// ============================================================

public record EjecutarLiquidacionRequest
{
    public required string Cia { get; init; }
    public required string Sucursal { get; init; }
    public required string Ano { get; init; }
    public required string Periodo { get; init; }
    public required string Subper { get; init; }
    public required string Semana { get; init; }
    public required string SeccionInicio { get; init; }
    public required string EmpleadoInicio { get; init; }
    public required string SeccionFin { get; init; }
    public required string EmpleadoFin { get; init; }

    /// <summary>
    /// Días festivos por posición (1-6). "X" = festivo, "" = normal.
    /// Ejemplo: ["", "X", "", "", "", ""] → martes es festivo.
    /// </summary>
    public string[]? DiasFestivos { get; init; }
    public bool ModoMasivo { get; init; } = false;
}

public record VerificacionAcumuladosResponse(
    int CantidadAcumulados,
    bool PuedeLiquidar,
    string Mensaje);

public record ExportarObservacionesQuery : MediatR.IRequest<byte[]>
{
    public required string Cia { get; init; }
    public required string Sucursal { get; init; }
    public required string Ano { get; init; }
    public required string Periodo { get; init; }
    public required string Subper { get; init; }
    public required string Semana { get; init; }
}

public record VerificarAcumuladosQuery : MediatR.IRequest<VerificacionAcumuladosResponse>
{
    public required string Cia { get; init; }
    public required string Sucursal { get; init; }
    public required string Ano { get; init; }
    public required string Periodo { get; init; }
    public required string Subper { get; init; }
    public required string SeccionInicio { get; init; }
    public required string EmpleadoInicio { get; init; }
    public required string SeccionFin { get; init; }
    public required string EmpleadoFin { get; init; }
}
