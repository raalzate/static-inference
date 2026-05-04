// ============================================================
// NOMIN430 — Servicio de Dominio: Cálculo de Nómina Destajo
// Reemplaza la lógica de negocio de Nomin430_n.pfProceso()
// Patrón: Domain Service (sin estado, sin dependencias de infraestructura)
// ============================================================
using NominaDestajo.Domain.Entities;
using NominaDestajo.Domain.ValueObjects;

namespace NominaDestajo.Domain.Services;

/// <summary>
/// Servicio de dominio para el cálculo de liquidación de nómina destajo semanal.
/// Descompone el God Class Nomin430_n en responsabilidades claras.
/// Sin efectos secundarios directos de BD — retorna resultados para persistencia.
/// </summary>
public class PayrollCalculationService
{
    private readonly ShiftService _shiftService;
    private readonly AbsenceService _absenceService;
    private readonly TransportSubsidyService _transportService;

    public PayrollCalculationService(
        ShiftService shiftService,
        AbsenceService absenceService,
        TransportSubsidyService transportService)
    {
        _shiftService = shiftService;
        _absenceService = absenceService;
        _transportService = transportService;
    }

    /// <summary>
    /// Calcula la nómina destajo para un empleado en la semana indicada.
    /// Retorna la lista de registros Destajo a persistir.
    /// Equivale a la cadena: Liqesta → Cambiolab → Calprom → LiqAjusteBasico → Liqtran
    /// </summary>
    public LiquidacionResult CalcularEmpleado(LiquidacionContext ctx)
    {
        var result = new LiquidacionResult(ctx.Empleado.Codigo);

        // BR-03: Empleado sin salario no se liquida
        if (!ctx.Empleado.TieneSalario())
        {
            result.AgregarObservacion("Empleado sin salario — no liquidado");
            return result;
        }

        // BR-05: Sin horario destajo no se liquida
        if (!ctx.Empleado.TieneHorarioDestajo())
        {
            result.AgregarObservacion("Empleado sin horario destajo — no liquidado");
            return result;
        }

        if (ctx.Empleado.EsPersonalAdministrativo())
        {
            // Modo G: solo horas extras para nómina general
            var turnosResult = _shiftService.LiquidarTurnosAdministrativos(ctx);
            result.AgregarRegistros(turnosResult.Registros);
            return result;
        }

        // Calcular horas por tipo de horario (XZ, Y, Z)
        var horasPorDia = CalcularHorasPorDia(ctx);

        // Liquidar labores por día (Liqesta + Cambiolab)
        var laboresDia = LiquidarLaboresPorDia(ctx, horasPorDia);
        result.AgregarRegistros(laboresDia);

        // Modo T: agregar liquidación de turnos
        if (ctx.Empleado.ModoPago == ModoPago.Turno)
        {
            var turnosResult = _shiftService.LiquidarTurnosPersonalCampo(ctx);
            result.AgregarRegistros(turnosResult.Registros);
        }

        // Modo A: liquidación automática
        if (ctx.Empleado.ModoPago == ModoPago.Automatico)
        {
            var autoResult = LiquidarAutomatico(ctx);
            result.AgregarRegistros(autoResult);
        }

        // Verificar si hay pagos destajo para continuar con promedios
        if (result.Registros.Any())
        {
            // Calcular promedios y ajuste básico (Calprom + LiqAjusteBasico)
            var ajustes = CalcularPromediosYAjuste(ctx, result.Registros);
            result.AgregarRegistros(ajustes.NuevosRegistros);
            if (ajustes.AjusteDomingo != 0) result.AgregarAjusteDomingo(ajustes.AjusteDomingo);
            if (ajustes.AjusteMinimo != 0) result.AgregarAjusteMinimo(ajustes.AjusteMinimo);

            // Calcular auxilio de transporte (Liqtran)
            var transporte = _transportService.Calcular(ctx);
            if (transporte.Valor > 0)
                result.AgregarRegistros([transporte.ToDestajo(ctx)]);
        }
        else if (ctx.DiasHabiles.Length == 1 && ctx.DiasHabiles[0].EsFestivo)
        {
            // Fracción de semana con un solo día festivo — solo Calprom
            var ajustes = CalcularPromediosYAjuste(ctx, []);
            result.AgregarRegistros(ajustes.NuevosRegistros);
        }

        return result;
    }

    /// <summary>
    /// Calcula las horas por día según tipo de horario.
    /// XZ: usa horas fijas del horario por día.
    /// Y: usa 12 horas por día.
    /// Z: redistribuye horas excluyendo días festivos tipo Retiro.
    /// </summary>
    private HorasDia[] CalcularHorasPorDia(LiquidacionContext ctx)
    {
        var horario = ctx.Horario;
        var horasDia = new decimal[]
        {
            horario.HorasD1, horario.HorasD2, horario.HorasD3,
            horario.HorasD4, horario.HorasD5, horario.HorasD6,
            horario.HorasDia  // día 7 = promedio
        };

        if (ctx.EsHorarioXZ)
        {
            // XZ: horas fijas del horario, sin redistribución
            return horasDia.Select((h, i) => new HorasDia(i + 1, h)).ToArray();
        }

        if (ctx.EsHorarioY)
        {
            // Y: 12 horas uniformes por día
            return Enumerable.Range(1, 7).Select(i => new HorasDia(i, 12m)).ToArray();
        }

        if (ctx.EsHorarioZ)
        {
            // Z: redistribuir horas entre días hábiles excluyendo festivos tipo R
            var cantFest = ctx.DiasHabiles.Count(d => d.EsFestivo && ctx.TipoNomina.TipoFestivo == TipoFestivo.Retiro);
            var diasHabiles = horario.DiasSem - cantFest;
            var horasRestantes = horario.HorasSem - (horario.HorasDia * cantFest);
            var horasRedistribuidas = diasHabiles > 0
                ? Math.Round(horasRestantes / diasHabiles, 4)
                : 0;

            return Enumerable.Range(1, 7).Select(i =>
            {
                var dia = ctx.DiasHabiles.FirstOrDefault(d => d.NroDia == i);
                if (dia != null && dia.EsFestivo && ctx.TipoNomina.TipoFestivo == TipoFestivo.Retiro)
                    return new HorasDia(i, horario.HorasDia);  // horas normales en festivo tipo R
                return new HorasDia(i, horasRedistribuidas);
            }).ToArray();
        }

        // Horario estándar
        return horasDia.Select((h, i) => new HorasDia(i + 1, h)).ToArray();
    }

    private IReadOnlyList<Destajo> LiquidarLaboresPorDia(LiquidacionContext ctx, HorasDia[] horasPorDia)
    {
        var registros = new List<Destajo>();

        for (int dia = 1; dia <= 6; dia++)
        {
            var diaDato = ctx.DiasHabiles.FirstOrDefault(d => d.NroDia == dia);
            var horas = horasPorDia.FirstOrDefault(h => h.Dia == dia)?.Horas ?? 0;

            if (horas == 0) continue;

            var codLabor = diaDato?.EsFestivo == true
                ? ctx.Parametros.CodLaborFestivo
                : ctx.LaborNormal(ctx.Empleado.Seccion, dia);

            if (string.IsNullOrEmpty(codLabor)) continue;

            var valor = CalcularValorDia(ctx.Empleado.Sueldo, horas, ctx.Horario.HorasSem);

            registros.Add(Destajo.CrearAutomatico(
                ctx.Cia, ctx.Sucursal, ctx.Empleado.Codigo,
                ctx.Ano, ctx.Periodo, ctx.Subper, ctx.Semana, dia.ToString(),
                codLabor, horas, valor, ctx.UsuarioSistema,
                ctx.OrdenTrabajo(codLabor), ctx.CentrosCosto(codLabor)));
        }

        return registros;
    }

    private decimal CalcularValorDia(decimal sueldo, decimal horas, decimal horasSem)
    {
        if (horasSem == 0) return 0;
        // Valor proporcional: sueldo_semana × (horas_dia / horas_semana)
        var sueldoSemanal = sueldo * 7 / 30m;  // Aproximación mensual → semanal Colombia
        return Math.Round(sueldoSemanal * horas / horasSem, 2);
    }

    private IReadOnlyList<Destajo> LiquidarAutomatico(LiquidacionContext ctx)
    {
        // Modo A: generación automática sin turnos ni destajo por pieza
        // La lógica específica depende de la configuración de la empresa
        return [];
    }

    private PromedioAjusteResult CalcularPromediosYAjuste(LiquidacionContext ctx, IReadOnlyList<Destajo> registrosExistentes)
    {
        // Calprom: promedio semanal para ajuste de domingo/festivo
        // LiqAjusteBasico: ajuste al básico si el trabajador gana menos del mínimo
        var totalSemana = registrosExistentes.Sum(r => r.Valor);
        var diasLaborados = registrosExistentes.Select(r => r.Dia).Distinct().Count();

        var ajusteDomingo = 0m;
        var ajusteMinimo = 0m;
        var nuevosRegistros = new List<Destajo>();

        // Ajuste domingo: si laboró todos los días, agrega el proporcional del domingo
        if (diasLaborados >= 6 && totalSemana > 0)
        {
            var promedioDiario = totalSemana / diasLaborados;
            ajusteDomingo = Math.Round(promedioDiario, 2);

            nuevosRegistros.Add(Destajo.CrearAutomatico(
                ctx.Cia, ctx.Sucursal, ctx.Empleado.Codigo,
                ctx.Ano, ctx.Periodo, ctx.Subper, ctx.Semana, "7",
                ctx.Parametros.CodLaborDomingo, 1m, ajusteDomingo,
                ctx.UsuarioSistema, ctx.Parametros.OrdenDomingo, ctx.Parametros.CentroDomingo));
        }

        // Ajuste al básico (LiqAjusteBasico): si gana menos del mínimo según grupo de labor
        // Esta lógica es compleja y depende de la tabla Seriegruaju — simplificada aquí
        // TODO: implementar lookup por grupo de labor y comparación con salario mínimo vigente

        return new PromedioAjusteResult(nuevosRegistros, ajusteDomingo, ajusteMinimo);
    }
}

// ============================================================
// VALUE OBJECTS Y DTOs DEL DOMINIO
// ============================================================

public record HorasDia(int Dia, decimal Horas);

public record PromedioAjusteResult(
    IReadOnlyList<Destajo> NuevosRegistros,
    decimal AjusteDomingo,
    decimal AjusteMinimo);

public class LiquidacionResult
{
    private readonly List<Destajo> _registros = [];
    private readonly List<string> _observaciones = [];
    private decimal _ajusteDomingo;
    private decimal _ajusteMinimo;

    public string CodEmpleado { get; }
    public IReadOnlyList<Destajo> Registros => _registros.AsReadOnly();
    public IReadOnlyList<string> Observaciones => _observaciones.AsReadOnly();
    public decimal AjusteDomingo => _ajusteDomingo;
    public decimal AjusteMinimo => _ajusteMinimo;
    public bool TieneErrores { get; private set; }
    public string? MensajeError { get; private set; }

    public LiquidacionResult(string codEmpleado) => CodEmpleado = codEmpleado;

    public void AgregarRegistros(IEnumerable<Destajo> registros) => _registros.AddRange(registros);
    public void AgregarObservacion(string obs) => _observaciones.Add(obs);
    public void AgregarAjusteDomingo(decimal v) => _ajusteDomingo = v;
    public void AgregarAjusteMinimo(decimal v) => _ajusteMinimo = v;
    public void MarcarError(string mensaje) { TieneErrores = true; MensajeError = mensaje; }
}

public enum TipoFestivo { Retiro, Fijo }
