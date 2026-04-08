using Microsoft.EntityFrameworkCore;
using SampleProject.Data;
using SampleProject.Repositories;
using SampleProject.Services;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();
builder.Services.AddDbContext<AppDbContext>(options =>
    options.UseInMemoryDatabase("SampleDb"));
builder.Services.AddScoped<IOrderRepository, OrderRepository>();
builder.Services.AddScoped<IOrderService, OrderService>();

var app = builder.Build();

app.MapControllers();
app.Run();
