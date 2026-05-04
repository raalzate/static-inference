// ============================================================
// NOMIN430 — ASP.NET Core 8 Program.cs
// Configuración completa del servidor
// Sofka Technologies — Marzo 2026
// ============================================================
using Microsoft.EntityFrameworkCore;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.OpenApi.Models;
using MediatR;
using NominaDestajo.Domain.Services;
using NominaDestajo.Infrastructure.Data;
using NominaDestajo.Infrastructure.Repositories;

var builder = WebApplication.CreateBuilder(args);

// ============================================================
// SERVICES
// ============================================================

// 1. Base de datos — EF Core 8 + SQL Server
builder.Services.AddDbContext<NominaDbContext>(options =>
    options.UseSqlServer(
        builder.Configuration.GetConnectionString("NominaIntegral"),
        sql => {
            sql.CommandTimeout(120);
            sql.EnableRetryOnFailure(3, TimeSpan.FromSeconds(10), null);
        })
    .EnableSensitiveDataLogging(builder.Environment.IsDevelopment())
);

// 2. MediatR + CQRS (registra automáticamente handlers)
builder.Services.AddMediatR(cfg =>
    cfg.RegisterServicesFromAssembly(typeof(Program).Assembly));

// 3. Repositorios (Infrastructure)
builder.Services.AddScoped<IEmpleadoRepository, EmpleadoRepository>();
builder.Services.AddScoped<IDestajoRepository, DestajoRepository>();
builder.Services.AddScoped<INominaConfigRepository, NominaConfigRepository>();
builder.Services.AddScoped<IProcesoLogRepository, ProcesoLogRepository>();
builder.Services.AddScoped<IUnitOfWork, UnitOfWork>();

// 4. Domain Services
builder.Services.AddScoped<PayrollCalculationService>();
builder.Services.AddScoped<ShiftService>();
builder.Services.AddScoped<AbsenceService>();
builder.Services.AddScoped<TransportSubsidyService>();

// 5. Autenticación JWT
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.Authority = builder.Configuration["Auth:Authority"];
        options.Audience = builder.Configuration["Auth:Audience"];
        options.RequireHttpsMetadata = !builder.Environment.IsDevelopment();
    });

builder.Services.AddAuthorization(options =>
{
    options.AddPolicy("Liquidador", policy =>
        policy.RequireRole("Liquidador", "Administrador"));
    options.AddPolicy("Consultor", policy =>
        policy.RequireRole("Liquidador", "Consultor", "Administrador"));
});

// 6. Controllers
builder.Services.AddControllers();

// 7. Swagger / OpenAPI
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(c =>
{
    c.SwaggerDoc("v1", new OpenApiInfo
    {
        Title = "Nómina Integral — Destajo API",
        Version = "v1",
        Description = "API REST para liquidación de nómina destajo semanal. Reemplaza el módulo Nomin430 del sistema VB.Net legado.",
        Contact = new OpenApiContact { Name = "Sofka Technologies", Email = "nomina@sofka.com.co" }
    });

    c.AddSecurityDefinition("Bearer", new OpenApiSecurityScheme
    {
        In = ParameterLocation.Header,
        Description = "JWT Bearer token. Formato: Bearer {token}",
        Name = "Authorization",
        Type = SecuritySchemeType.Http,
        Scheme = "bearer"
    });

    c.AddSecurityRequirement(new OpenApiSecurityRequirement
    {{
        new OpenApiSecurityScheme { Reference = new OpenApiReference { Type = ReferenceType.SecurityScheme, Id = "Bearer" }},
        Array.Empty<string>()
    }});
});

// 8. Logging estructurado (Serilog recomendado en producción)
builder.Services.AddLogging(logging =>
{
    logging.AddConsole();
    if (!builder.Environment.IsDevelopment())
        logging.AddApplicationInsights(); // Azure Monitor
});

// 9. Health checks
builder.Services.AddHealthChecks()
    .AddSqlServer(builder.Configuration.GetConnectionString("NominaIntegral")!,
        name: "sqlserver", tags: ["database"]);

// 10. CORS (ajustar origenes según ambiente)
builder.Services.AddCors(options =>
    options.AddDefaultPolicy(policy =>
        policy.WithOrigins(builder.Configuration.GetSection("Cors:Origins").Get<string[]>() ?? ["http://localhost:3000"])
              .AllowAnyMethod()
              .AllowAnyHeader()
              .AllowCredentials()));

// ============================================================
// MIDDLEWARE PIPELINE
// ============================================================

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI(c =>
    {
        c.SwaggerEndpoint("/swagger/v1/swagger.json", "Nómina Destajo API v1");
        c.RoutePrefix = string.Empty;
    });
    app.UseDeveloperExceptionPage();
}
else
{
    // En producción: error handler global
    app.UseExceptionHandler("/error");
    app.UseHsts();
}

app.UseHttpsRedirection();
app.UseCors();
app.UseAuthentication();
app.UseAuthorization();

// Health check endpoint
app.MapHealthChecks("/health", new Microsoft.AspNetCore.Diagnostics.HealthChecks.HealthCheckOptions
{
    ResponseWriter = async (context, report) =>
    {
        context.Response.ContentType = "application/json";
        var result = System.Text.Json.JsonSerializer.Serialize(new
        {
            status = report.Status.ToString(),
            checks = report.Entries.Select(e => new { name = e.Key, status = e.Value.Status.ToString() })
        });
        await context.Response.WriteAsync(result);
    }
});

app.MapControllers();

// Ejecutar migraciones automáticas en desarrollo
if (app.Environment.IsDevelopment())
{
    using var scope = app.Services.CreateScope();
    var db = scope.ServiceProvider.GetRequiredService<NominaDbContext>();
    await db.Database.MigrateAsync();
}

app.Run();
