
package banco_hexagonal.pruebatecnica.infraestructure.Adapter.Persistence;

import banco_hexagonal.pruebatecnica.domain.Model.Account;
import banco_hexagonal.pruebatecnica.domain.Model.Movement;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * @author Trabajo
 */
public interface MovementRepositoryJpa extends JpaRepository<Movement, Long>{
    List<Movement> findByAccountAndDateBetween(Account account, LocalDate from, LocalDate to);
}
