using ECommerceApp.DTOs;
using ECommerceApp.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace ECommerceApp.Controllers;

[ApiController]
[Route("api/[controller]")]
[Authorize]
public class OrdersController : ControllerBase
{
    private readonly IOrderService _orderService;

    public OrdersController(IOrderService orderService)
    {
        _orderService = orderService;
    }

    [HttpGet]
    public async Task<IActionResult> GetAll()
    {
        var orders = await _orderService.GetAllOrdersAsync();
        return Ok(orders);
    }

    [HttpGet("{id}")]
    public async Task<IActionResult> GetById(int id)
    {
        var order = await _orderService.GetOrderByIdAsync(id);
        return order == null ? NotFound() : Ok(order);
    }

    [HttpPost]
    public async Task<IActionResult> PlaceOrder([FromBody] CreateOrderDto dto)
    {
        var order = await _orderService.PlaceOrderAsync(dto);
        return CreatedAtAction(nameof(GetById), new { id = order.Id }, order);
    }

    [HttpPost("{id}/cancel")]
    public async Task<IActionResult> Cancel(int id)
    {
        await _orderService.CancelOrderAsync(id);
        return NoContent();
    }

    [HttpPost("{id}/ship")]
    [Authorize(Roles = "Admin,Warehouse")]
    public async Task<IActionResult> Ship(int id)
    {
        await _orderService.ShipOrderAsync(id);
        return NoContent();
    }
}
