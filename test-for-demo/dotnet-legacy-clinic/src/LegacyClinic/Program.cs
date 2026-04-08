using LegacyClinic.Data;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

// HARDCODED_SECRET_IN_CODE: connection string with password right here
string connectionString = "Server=prod-db;Database=ClinicDB;User=sa;Password=Cl1n1c@2019!;";
builder.Services.AddDbContext<ClinicDbContext>(options =>
    options.UseSqlServer(connectionString));

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseAuthorization();
app.MapControllers();
app.Run();
