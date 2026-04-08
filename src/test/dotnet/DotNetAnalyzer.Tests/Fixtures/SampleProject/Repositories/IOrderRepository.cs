using SampleProject.Models;

namespace SampleProject.Repositories;

public interface IOrderRepository
{
    Task<Order?> GetByIdAsync(int id);
    Task<IEnumerable<Order>> GetAllAsync();
    Task AddAsync(Order order);
}
