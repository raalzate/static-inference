
package banco_hexagonal.pruebatecnica.application.Port.In;

import banco_hexagonal.pruebatecnica.domain.Model.Movement;
import java.time.LocalDate;
import java.util.List;

/**
 *
 * @author AustinSalguero
 */
public interface MovementUseCase {
    Movement registerMovement(Long accountNumber, Movement movement);
    List<Movement> getMovementForAccountAndDate(Long accountNumber, LocalDate fromDate, LocalDate toDate);
}
