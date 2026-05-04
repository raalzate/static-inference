// ============================================================
// NOMIN430 — Backend ASP.NET Core 8
// Dominio: Entidad Empleado (Maestro)
// Sofka Technologies — Marzo 2026
// ============================================================
namespace NominaDestajo.Domain.Entities;

/// <summary>
/// Entidad principal de empleado (reemplaza tabla MAESTRO del legado).
/// Modo de pago determina la ruta de cálculo en el proceso de liquidación:
/// D = Destajo, T = Turno, A = Automático, G = General con extras.
/// </summary>
public class Empleado
{
    public long Id { get; private set; }
    public string Cia { get; private set; }
    public string Sucursal { get; private set; }
    public string Codigo { get; private set; }
    public string Seccion { get; private set; }
    public string Nombres { get; private set; }
    public string Apellidos { get; private set; }
    public string NombreCompleto => $"{Nombres} {Apellidos}";
    public decimal Sueldo { get; private set; }
    public ModoPago ModoPago { get; private set; }
    public ModoSalario ModoSalario { get; private set; }
    public string? CodHorario { get; private set; }       // FK → Horario.CodHorario
    public string CodTipoNomina { get; private set; }
    public string? CodCargo { get; private set; }
    public EstadoEmpleado Estado { get; private set; }
    public DateOnly FechaIngreso { get; private set; }
    public DateOnly? FechaRetiro { get; private set; }

    // Navigation properties (cargados por EF Core)
    public Horario? Horario { get; private set; }
    public TipoNomina? TipoNomina { get; private set; }

    private Empleado() { } // EF Core

    public static Empleado Create(
        string cia, string sucursal, string codigo, string seccion,
        string nombres, string apellidos, decimal sueldo,
        ModoPago modoPago, string codTipoNomina, DateOnly fechaIngreso,
        string? codHorario = null, ModoSalario modoSalario = ModoSalario.Normal)
    {
        if (string.IsNullOrWhiteSpace(codigo)) throw new ArgumentException("Código empleado es obligatorio.");
        if (string.IsNullOrWhiteSpace(seccion)) throw new ArgumentException("Sección es obligatoria.");
        if (sueldo < 0) throw new ArgumentOutOfRangeException(nameof(sueldo), "El sueldo no puede ser negativo.");

        return new Empleado
        {
            Cia = cia,
            Sucursal = sucursal,
            Codigo = codigo,
            Seccion = seccion,
            Nombres = nombres,
            Apellidos = apellidos,
            Sueldo = sueldo,
            ModoPago = modoPago,
            ModoSalario = modoSalario,
            CodHorario = codHorario,
            CodTipoNomina = codTipoNomina,
            Estado = EstadoEmpleado.Activo,
            FechaIngreso = fechaIngreso
        };
    }

    public bool TieneSalario() => Sueldo > 0;
    public bool TieneHorarioDestajo() => !string.IsNullOrWhiteSpace(CodHorario);
    public bool EsPersonalAdministrativo() => ModoPago == ModoPago.General;
    public bool EsSalarioIntegral() => ModoSalario == ModoSalario.Integral;
}

public enum ModoPago { Destajo = 'D', Turno = 'T', Automatico = 'A', General = 'G' }
public enum ModoSalario { Normal = 'N', Integral = 'I' }
public enum EstadoEmpleado { Activo = 'A', Retirado = 'R' }
