// ============================================================
// NOMIN430 — Application Layer: CQRS Commands + Handlers
// Patrón: MediatR + CQRS
// Sofka Technologies — Marzo 2026
// ============================================================
using MediatR;
using NominaDestajo.Domain.Entities;
using NominaDestajo.Domain.Services;

namespace NominaDestajo.Application.Commands;

// ============================================================
// COMMAND: EjecutarLiquidacion
// Disparado por el endpoint POST /api/nomina/destajo/liquidar
// ============================================================

/// <summary>
/// Command para ejecutar el proceso de liquidación destajo semanal.
/// Equivale al botón "Procesar" / pfProceso() del sistema legado.
/// </summary>
public record EjecutarLiquidacionCommand : IRequest<LiquidacionResponse>
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

    /// <summary>Días festivos de la semana (posiciones 1-6, "X"=festivo, ""=normal)</summary>
    public required string[] DiasFestivos { get; init; }  // fdia(1..6) del legado

    /// <summary>Si true, no muestra diálogos interactivos (LiqMasiva del legado)</summary>
    public bool ModoMasivo { get; init; } = false;

    /// <summary>Usuario que ejecuta el proceso (para auditoría)</summary>
    public required string UsuarioSistema { get; init; }
}

public record LiquidacionResponse(
    bool Exitoso,
    int EmpleadosProcesados,
    int EmpleadosConError,
    string? MensajeError,
    IReadOnlyList<ObservacionEmpleado> Observaciones);

public record ObservacionEmpleado(
    string CodEmpleado,
    string Seccion,
    string Observacion,
    decimal AjusteDomingo,
    decimal AjusteMinimo);

// ============================================================
// COMMAND HANDLER
// ============================================================

public class EjecutarLiquidacionCommandHandler : IRequestHandler<EjecutarLiquidacionCommand, LiquidacionResponse>
{
    private readonly IEmpleadoRepository _empleadoRepo;
    private readonly IDestajoRepository _destajoRepo;
    private readonly INominaConfigRepository _configRepo;
    private readonly IProcesoLogRepository _procesoLogRepo;
    private readonly PayrollCalculationService _calculationService;
    private readonly IUnitOfWork _unitOfWork;

    public EjecutarLiquidacionCommandHandler(
        IEmpleadoRepository empleadoRepo,
        IDestajoRepository destajoRepo,
        INominaConfigRepository configRepo,
        IProcesoLogRepository procesoLogRepo,
        PayrollCalculationService calculationService,
        IUnitOfWork unitOfWork)
    {
        _empleadoRepo = empleadoRepo;
        _destajoRepo = destajoRepo;
        _configRepo = configRepo;
        _procesoLogRepo = procesoLogRepo;
        _calculationService = calculationService;
        _unitOfWork = unitOfWork;
    }

    public async Task<LiquidacionResponse> Handle(
        EjecutarLiquidacionCommand request,
        CancellationToken cancellationToken)
    {
        // Registrar inicio del proceso en audit log
        var procesoLog = await _procesoLogRepo.CrearAsync(new ProcesoLog
        {
            Cia = request.Cia,
            Sucursal = request.Sucursal,
            Ano = request.Ano,
            Periodo = request.Periodo,
            Subper = request.Subper,
            Semana = request.Semana,
            RangoIni = request.SeccionInicio + request.EmpleadoInicio,
            RangoFin = request.SeccionFin + request.EmpleadoFin,
            ModoMasivo = request.ModoMasivo,
            Estado = "EN_PROCESO",
            IniciadoPor = request.UsuarioSistema
        }, cancellationToken);

        try
        {
            // 1. Cargar configuración del sistema
            var parametros = await _configRepo.CargarParametrosAsync(
                request.Cia, request.Sucursal, cancellationToken);

            // 2. Verificar que el subperíodo no sea especial (BR-01)
            var periodo = await _configRepo.ObtenerPeriodoAsync(
                request.Cia, request.Sucursal, request.Ano, request.Periodo, request.Subper, cancellationToken);

            if (periodo.DiasSubperiodo == 0)
                return new LiquidacionResponse(false, 0, 0,
                    "El presente subperíodo es especial, no se puede liquidar destajo", []);

            // 3. Verificar acumulados previos (BR-04)
            var cantAcumulados = await _destajoRepo.ContarAcumuladosAsync(
                request.Cia, request.Sucursal, request.Ano, request.Periodo, request.Subper,
                request.SeccionInicio, request.EmpleadoInicio,
                request.SeccionFin, request.EmpleadoFin, cancellationToken);

            if (cantAcumulados > 0)
                return new LiquidacionResponse(false, 0, 0,
                    $"Ya hay {cantAcumulados} registros acumulados. Revierta el acumulado antes de reliquidar.", []);

            // 4. Limpiar registros automáticos previos
            await _destajoRepo.EliminarAutomaticosAsync(
                request.Cia, request.Sucursal, request.Ano, request.Periodo, request.Subper,
                request.Semana, request.SeccionInicio, request.EmpleadoInicio,
                request.SeccionFin, request.EmpleadoFin, cancellationToken);

            // 5. Cargar empleados del rango (Maestro UNION CondMaestro1 + CondMaestro2)
            var empleados = await _empleadoRepo.ObtenerParaLiquidacionAsync(
                request.Cia, request.Sucursal,
                request.SeccionInicio, request.EmpleadoInicio,
                request.SeccionFin, request.EmpleadoFin,
                periodo.FechaFin, request.Ano, request.Periodo, request.Subper, request.Semana,
                cancellationToken);

            var observaciones = new List<ObservacionEmpleado>();
            var procesados = 0;
            var conError = 0;

            // 6. Ciclo principal por empleado
            foreach (var empleado in empleados)
            {
                cancellationToken.ThrowIfCancellationRequested();

                // Construir contexto de liquidación por empleado
                var ctx = await ConstruirContextoAsync(empleado, request, parametros, cancellationToken);

                // Iniciar transacción por empleado (BR-06: rollback individual)
                await using var tx = await _unitOfWork.BeginTransactionAsync(cancellationToken);
                try
                {
                    var result = _calculationService.CalcularEmpleado(ctx);

                    if (result.TieneErrores)
                    {
                        await tx.RollbackAsync(cancellationToken);
                        conError++;
                        observaciones.Add(new ObservacionEmpleado(
                            empleado.Codigo, empleado.Seccion,
                            result.MensajeError ?? "Error desconocido", 0, 0));
                        continue;
                    }

                    // Persistir registros del empleado
                    if (result.Registros.Any())
                        await _destajoRepo.InsertarLoteAsync(result.Registros, cancellationToken);

                    await tx.CommitAsync(cancellationToken);
                    procesados++;

                    // Recolectar observaciones
                    foreach (var obs in result.Observaciones)
                        observaciones.Add(new ObservacionEmpleado(
                            empleado.Codigo, empleado.Seccion, obs,
                            result.AjusteDomingo, result.AjusteMinimo));
                }
                catch (Exception ex)
                {
                    await tx.RollbackAsync(cancellationToken);
                    conError++;
                    observaciones.Add(new ObservacionEmpleado(
                        empleado.Codigo, empleado.Seccion,
                        $"Error: {ex.Message}", 0, 0));
                }
            }

            // 7. Actualizar indicativo prenómina
            if (procesados > 0)
                await _configRepo.ActualizarIndicativoPrenominaAsync(
                    request.Cia, request.Sucursal, request.Ano, request.Periodo, request.Subper,
                    false, cancellationToken);  // false = prenómina pendiente

            // 8. Actualizar log del proceso
            await _procesoLogRepo.FinalizarAsync(procesoLog.Id, procesados, conError, cancellationToken);

            return new LiquidacionResponse(true, procesados, conError, null, observaciones);
        }
        catch (Exception ex)
        {
            await _procesoLogRepo.RegistrarErrorAsync(procesoLog.Id, ex.Message, cancellationToken);
            throw;
        }
    }

    private async Task<LiquidacionContext> ConstruirContextoAsync(
        Empleado empleado, EjecutarLiquidacionCommand request,
        ParametrosSistema parametros, CancellationToken ct)
    {
        // Cargar datos adicionales del empleado para el contexto
        var ausencias = await _empleadoRepo.ObtenerAusenciasAsync(
            request.Cia, request.Sucursal, empleado.Codigo,
            request.Ano, request.Periodo, request.Subper, ct);

        return new LiquidacionContext
        {
            Cia = request.Cia,
            Sucursal = request.Sucursal,
            Empleado = empleado,
            Horario = empleado.Horario!,
            TipoNomina = empleado.TipoNomina!,
            Ano = request.Ano,
            Periodo = request.Periodo,
            Subper = request.Subper,
            Semana = request.Semana,
            DiasFestivos = request.DiasFestivos,
            Parametros = parametros,
            Ausencias = ausencias,
            UsuarioSistema = request.UsuarioSistema,
            ModoMasivo = request.ModoMasivo
        };
    }
}

// ============================================================
// QUERY: ConsultarDestajoQuery
// ============================================================

public record ConsultarDestajoQuery : IRequest<IReadOnlyList<DestajoDto>>
{
    public required string Cia { get; init; }
    public required string Sucursal { get; init; }
    public required string Ano { get; init; }
    public required string Periodo { get; init; }
    public string? Subper { get; init; }
    public string? CodEmpleado { get; init; }
}

public record DestajoDto(
    long Id, string Empleado, string NombreEmpleado, string Seccion,
    string Semana, string Dia, string CodLabor, string NomLabor,
    decimal Cantidad, decimal Valor, string IndLiq, bool Acumulado,
    string? OrdenP, DateTime CreadoEn);

public class ConsultarDestajoQueryHandler : IRequestHandler<ConsultarDestajoQuery, IReadOnlyList<DestajoDto>>
{
    private readonly IDestajoRepository _repo;

    public ConsultarDestajoQueryHandler(IDestajoRepository repo) => _repo = repo;

    public async Task<IReadOnlyList<DestajoDto>> Handle(
        ConsultarDestajoQuery request, CancellationToken cancellationToken)
        => await _repo.ConsultarAsync(request, cancellationToken);
}
