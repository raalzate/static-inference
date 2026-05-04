// ============================================================
// NOMIN430 — Entidad Destajo (registro de pago)
// ============================================================
namespace NominaDestajo.Domain.Entities;

/// <summary>
/// Registro individual de pago a destajo por día y concepto de labor.
/// Una transacción por empleado garantiza consistencia: si falla cualquier
/// registro del empleado, se revierte todo lo del empleado (no el proceso completo).
/// </summary>
public class Destajo
{
    public long Id { get; private set; }
    public string Cia { get; private set; }
    public string Sucursal { get; private set; }
    public string Empleado { get; private set; }
    public string Ano { get; private set; }
    public string Periodo { get; private set; }
    public string Subper { get; private set; }
    public string Semana { get; private set; }
    public string Dia { get; private set; }          // "1"–"7" (7=domingo)
    public string CodLabor { get; private set; }
    public decimal Cantidad { get; private set; }
    public decimal Valor { get; private set; }
    public IndicativoLiquidacion IndLiq { get; private set; }
    public bool Acumulado { get; private set; }
    public string? OrdenP { get; private set; }
    public string? Centro { get; private set; }
    public string? Documento { get; private set; }
    public string? Observacion { get; private set; }
    public DateTime CreadoEn { get; private set; }
    public string CreadoPor { get; private set; }

    private Destajo() { } // EF Core

    public static Destajo CrearAutomatico(
        string cia, string sucursal, string empleado,
        string ano, string periodo, string subper, string semana, string dia,
        string codLabor, decimal cantidad, decimal valor,
        string creadoPor, string? ordenP = null, string? centro = null, string? documento = null)
    {
        ValidarDia(dia);
        return new Destajo
        {
            Cia = cia, Sucursal = sucursal, Empleado = empleado,
            Ano = ano, Periodo = periodo, Subper = subper, Semana = semana, Dia = dia,
            CodLabor = codLabor, Cantidad = cantidad, Valor = valor,
            IndLiq = IndicativoLiquidacion.Automatico,
            Acumulado = false,
            OrdenP = ordenP, Centro = centro, Documento = documento,
            CreadoEn = DateTime.UtcNow, CreadoPor = creadoPor
        };
    }

    public void ActualizarValor(decimal nuevoValor) => Valor = nuevoValor;

    public void MarcarNoLaborado(string codNoHubot)
    {
        CodLabor = codNoHubot;
        IndLiq = IndicativoLiquidacion.Fijo;
        Valor = 0;
    }

    private static void ValidarDia(string dia)
    {
        if (!int.TryParse(dia, out int d) || d < 1 || d > 7)
            throw new ArgumentException($"Día '{dia}' no es válido. Debe ser entre 1 y 7.");
    }
}

public enum IndicativoLiquidacion { Automatico = 'A', Fijo = 'F', Manual = 'M' }
