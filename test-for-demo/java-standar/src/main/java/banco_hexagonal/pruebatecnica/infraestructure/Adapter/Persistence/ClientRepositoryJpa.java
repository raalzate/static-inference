
package banco_hexagonal.pruebatecnica.infraestructure.Adapter.Persistence;

import banco_hexagonal.pruebatecnica.domain.Model.Client;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * @author AustinSalguero
 */
public interface ClientRepositoryJpa extends JpaRepository<Client, Long>{
    
    
}
