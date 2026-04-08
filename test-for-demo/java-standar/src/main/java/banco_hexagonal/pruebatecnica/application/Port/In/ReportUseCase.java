
package banco_hexagonal.pruebatecnica.application.Port.In;

import banco_hexagonal.pruebatecnica.domain.Model.Account;
import banco_hexagonal.pruebatecnica.domain.Model.Movement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 *
 * @author AustinSalguero
 */
public interface ReportUseCase {
    Map<Account, List<Movement>> generateReport(Long clientId, LocalDate fromDate, LocalDate toDate);
}
