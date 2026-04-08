using Microsoft.AspNetCore.Mvc;
using SampleProject.Models;
using SampleProject.Services;

namespace SampleProject.Controllers;

[ApiController]
[Route("api/[controller]")]
public class OrdersController : ControllerBase
{
    private readonly IOrderService _orderService;

    public OrdersController(IOrderService orderService)
    {
        _orderService = orderService;
    }

    [HttpGet]
    public async Task<ActionResult<IEnumerable<Order>>> GetAll()
    {
        var orders = await _orderService.GetAllOrdersAsync();
        return Ok(orders);
    }

    [HttpGet("{id}")]
    public async Task<ActionResult<Order>> GetById(int id)
    {
        var order = await _orderService.GetOrderAsync(id);
        if (order == null) return NotFound();
        return Ok(order);
    }

    [HttpPost]
    public async Task<ActionResult<Order>> Create([FromBody] Order order)
    {
        await _orderService.CreateOrderAsync(order);
        return CreatedAtAction(nameof(GetById), new { id = order.Id }, order);
    }
}
